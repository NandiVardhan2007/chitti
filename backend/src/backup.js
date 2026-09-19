import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { pipeline } from 'node:stream/promises';
import express from 'express';
import { GridFSBucket } from 'mongodb';
import { HttpError } from './errors.js';

// The server never sees the key or the plaintext: it stores the phone's ciphertext in GridFS plus the
// public KDF parameters the phone needs to re-derive the key from the user's backup password.
const KDF = 'PBKDF2-HMAC-SHA256';
const BUCKET = 'backups'; // GridFS collections: backups.files + backups.chunks
const META = 'backupMeta'; // one document per user: { _id: uid, fileId, ...meta } -> the latest backup

const bucket = (db) => new GridFSBucket(db, { bucketName: BUCKET });
const noBackup = () => new HttpError(404, 'no_backup', 'No backup stored for this account');
const badRequest = (message) => new HttpError(400, 'bad_request', message);

export async function getBackupMeta(db, uid) {
  const doc = await db.collection(META).findOne({ _id: uid });
  return doc ? toMeta(doc) : null;
}

export async function deleteBackup(db, uid) {
  const doc = await db.collection(META).findOneAndDelete({ _id: uid });
  if (doc) await deleteFile(db, doc.fileId);
}

async function deleteFile(db, fileId) {
  await db.collection(`${BUCKET}.files`).deleteOne({ _id: fileId });
  await db.collection(`${BUCKET}.chunks`).deleteMany({ files_id: fileId });
}

export function backupRouter({ db, maxBackupBytes }) {
  const router = express.Router();

  router.put('/', async (req, res) => {
    const uid = req.auth.uid;
    const meta = parseBackupHeaders(req);
    const tooLarge = () => {
      discardBody(req, 2 * maxBackupBytes);
      return new HttpError(413, 'too_large', `Backup exceeds ${maxBackupBytes} bytes`);
    };
    if (Number(req.get('content-length')) > maxBackupBytes) throw tooLarge();

    // Stream request -> GridFS while hashing; memory use stays at roughly one GridFS chunk.
    const upload = bucket(db).openUploadStream(uid, { metadata: { uid } });
    const hash = createHash('sha256');
    let size = 0;
    let failure = null;
    try {
      // destroyOnReturn:false keeps the socket open when we stop early, so the 413 can still be sent.
      for await (const chunk of req.iterator({ destroyOnReturn: false })) {
        size += chunk.length;
        if (size > maxBackupBytes) throw tooLarge();
        hash.update(chunk);
        if (!upload.write(chunk)) {
          if (upload.errored) throw upload.errored;
          await once(upload, 'drain');
        }
      }
    } catch (err) {
      failure = req.readableAborted ? badRequest('Upload was interrupted by the client') : err;
    }
    // Always end the upload so no chunk write is still in flight when we clean up.
    try {
      await new Promise((resolve, reject) => upload.end((err) => (err ? reject(err) : resolve())));
    } catch (err) {
      failure ??= err;
    }
    if (!failure && size === 0) failure = badRequest('Backup body is empty');
    if (!failure && hash.digest('hex') !== meta.sha256) {
      failure = new HttpError(400, 'checksum_mismatch', 'SHA-256 of the body does not match X-Backup-Sha256');
    }
    if (failure) {
      await deleteFile(db, upload.id);
      throw failure;
    }

    // Swap the user's pointer to the new file in one atomic operation, then drop the old file.
    const doc = { _id: uid, fileId: upload.id, size, createdAt: new Date(), ...meta };
    let previous;
    try {
      previous = await db.collection(META).findOneAndReplace({ _id: uid }, doc, { upsert: true, returnDocument: 'before' });
    } catch (err) {
      await deleteFile(db, upload.id);
      throw err;
    }
    if (previous) await deleteFile(db, previous.fileId);
    res.status(201).json(toMeta(doc));
  });

  router.get('/meta', async (req, res) => {
    const meta = await getBackupMeta(db, req.auth.uid);
    if (!meta) throw noBackup();
    res.json(meta);
  });

  router.get('/', async (req, res) => {
    const doc = await db.collection(META).findOne({ _id: req.auth.uid });
    if (!doc) throw noBackup();
    res.set({
      'Content-Type': 'application/octet-stream',
      'Content-Length': String(doc.size),
      'X-Backup-Format-Version': String(doc.formatVersion),
      'X-Backup-Kdf': doc.kdf,
      'X-Backup-Kdf-Iterations': String(doc.kdfIterations),
      'X-Backup-Kdf-Salt': doc.kdfSalt,
      'X-Backup-Sha256': doc.sha256,
      'X-Backup-Device': doc.device,
    });
    await pipeline(bucket(db).openDownloadStream(doc.fileId), res);
  });

  router.delete('/', async (req, res) => {
    await deleteBackup(db, req.auth.uid);
    res.status(204).end();
  });

  return router;
}

// Reads and throws away the rest of an oversized body so the client can read our 413 instead of
// seeing a connection reset. Past the cap we stop wasting bandwidth and drop the connection.
function discardBody(req, cap) {
  let discarded = 0;
  req.on('data', (chunk) => {
    discarded += chunk.length;
    if (discarded > cap) req.destroy();
  });
  req.resume();
}

function parseBackupHeaders(req) {
  const int = (name, min, max) => {
    const value = req.get(name) ?? '';
    const n = /^\d{1,10}$/.test(value) ? Number(value) : NaN;
    if (!(n >= min && n <= max)) throw badRequest(`${name} must be an integer in ${min}..${max}`);
    return n;
  };
  const formatVersion = int('X-Backup-Format-Version', 1, 1_000_000);
  if (req.get('X-Backup-Kdf') !== KDF) throw badRequest(`X-Backup-Kdf must be ${KDF}`);
  const kdfIterations = int('X-Backup-Kdf-Iterations', 100_000, 5_000_000);

  const kdfSalt = req.get('X-Backup-Kdf-Salt') ?? '';
  const isBase64 = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(kdfSalt);
  const saltBytes = isBase64 ? Buffer.from(kdfSalt, 'base64').length : 0;
  if (saltBytes < 16 || saltBytes > 64) throw badRequest('X-Backup-Kdf-Salt must be standard base64 of 16..64 bytes');

  const sha256 = (req.get('X-Backup-Sha256') ?? '').toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(sha256)) throw badRequest('X-Backup-Sha256 must be a hex SHA-256');

  const device = req.get('X-Backup-Device');
  if (device === undefined || device.length > 100 || !/^[\t\x20-\x7e\x80-\xff]*$/.test(device)) {
    throw badRequest('X-Backup-Device is required, at most 100 characters');
  }
  return { sha256, formatVersion, kdf: KDF, kdfIterations, kdfSalt, device };
}

function toMeta(doc) {
  const { size, sha256, createdAt, formatVersion, kdf, kdfIterations, kdfSalt, device } = doc;
  return { size, sha256, createdAt, formatVersion, kdf, kdfIterations, kdfSalt, device };
}
