import { createHash, randomBytes } from 'node:crypto';
import http from 'node:http';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { MongoClient } from 'mongodb';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { pino } from 'pino';
import { createApp } from '../src/app.js';

// Fake Firebase: token string -> decoded token. Anything else (e.g. "revoked") is rejected.
const TOKENS = {
  'token-alice': { uid: 'alice', email: 'alice@example.com', email_verified: true, name: 'Alice', firebase: { sign_in_provider: 'google.com' } },
  'token-bob': { uid: 'bob', email: 'bob@example.com', email_verified: false, firebase: { sign_in_provider: 'password' } },
};
const verifyToken = async (token) => {
  if (!TOKENS[token]) throw new Error('auth/id-token-revoked');
  return TOKENS[token];
};
const ALICE = 'Bearer token-alice';
const BOB = 'Bearer token-bob';
const MAX = 1024 * 1024; // 1 MiB backup limit in tests
const silent = pino({ level: 'silent' });

let mongod, client, db, app;

beforeAll(async () => {
  mongod = await MongoMemoryServer.create();
  client = await MongoClient.connect(mongod.getUri());
  db = client.db('chitti-test');
  app = createApp({ db, verifyToken, logger: silent, maxBackupBytes: MAX, limits: { uploadsPerHour: 1000, requestsPer15Min: 1000 } });
});

afterAll(async () => {
  await client?.close();
  await mongod?.stop();
});

beforeEach(async () => {
  await Promise.all(['users', 'backupMeta', 'backups.files', 'backups.chunks'].map((c) => db.collection(c).deleteMany({})));
});

function backupHeaders(body, overrides = {}) {
  return {
    'Content-Type': 'application/octet-stream',
    'X-Backup-Format-Version': '1',
    'X-Backup-Kdf': 'PBKDF2-HMAC-SHA256',
    'X-Backup-Kdf-Iterations': '600000',
    'X-Backup-Kdf-Salt': Buffer.alloc(16, 7).toString('base64'),
    'X-Backup-Sha256': createHash('sha256').update(body).digest('hex'),
    'X-Backup-Device': 'iQOO Neo 9 Pro',
    ...overrides,
  };
}

const upload = (auth, body, overrides, target = app) =>
  request(target).put('/v1/backup').set('Authorization', auth).set(backupHeaders(body, overrides)).send(body);

const download = (auth) =>
  request(app)
    .get('/v1/backup')
    .set('Authorization', auth)
    .buffer(true)
    .parse((res, cb) => {
      const parts = [];
      res.on('data', (c) => parts.push(c));
      res.on('end', () => cb(null, Buffer.concat(parts)));
    });

const gridfsCounts = async () => ({
  files: await db.collection('backups.files').countDocuments(),
  chunks: await db.collection('backups.chunks').countDocuments(),
});

describe('health', () => {
  it('GET /health is public', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ ok: true });
    expect(res.headers['x-powered-by']).toBeUndefined();
    expect(res.headers['x-request-id']).toBeTruthy();
  });

  it('GET and HEAD /ping are public and never cached', async () => {
    const res = await request(app).get('/ping');
    expect(res.status).toBe(200);
    expect(res.text).toBe('pong');
    expect(res.headers['cache-control']).toBe('no-store');
    expect((await request(app).head('/ping')).status).toBe(200);
  });
});

describe('auth', () => {
  const routes = [
    ['post', '/v1/me'],
    ['get', '/v1/me'],
    ['delete', '/v1/me'],
    ['put', '/v1/backup'],
    ['get', '/v1/backup'],
    ['get', '/v1/backup/meta'],
    ['delete', '/v1/backup'],
  ];
  it.each(routes)('%s %s returns 401 without, with malformed, and with a rejected token', async (method, path) => {
    for (const header of [undefined, 'token-alice', 'Basic abc', 'Bearer revoked']) {
      const req = request(app)[method](path);
      if (header) req.set('Authorization', header);
      const res = await req;
      expect(res.status).toBe(401);
      expect(res.body.error).toBe('unauthorized');
    }
  });
});

describe('/v1/me', () => {
  it('upserts idempotently from the verified token', async () => {
    const first = await request(app).post('/v1/me').set('Authorization', ALICE).send({ displayName: 'ignored' });
    expect(first.status).toBe(200);
    expect(first.body).toMatchObject({ uid: 'alice', email: 'alice@example.com', emailVerified: true, displayName: 'Alice', providers: ['google.com'] });
    expect(first.body._id).toBeUndefined();

    const second = await request(app).post('/v1/me').set('Authorization', ALICE);
    expect(second.status).toBe(200);
    expect(second.body.createdAt).toBe(first.body.createdAt);
    expect(new Date(second.body.lastLoginAt) >= new Date(first.body.lastLoginAt)).toBe(true);
    expect(second.body.providers).toEqual(['google.com']);
    expect(second.body.displayName).toBe('Alice');
    expect(await db.collection('users').countDocuments()).toBe(1);
  });

  it('uses body displayName only when the token has none, and validates the body', async () => {
    const res = await request(app).post('/v1/me').set('Authorization', BOB).send({ displayName: 'Bobby' });
    expect(res.body).toMatchObject({ uid: 'bob', displayName: 'Bobby', emailVerified: false, providers: ['password'] });
    // A later login without a body keeps the stored name.
    expect((await request(app).post('/v1/me').set('Authorization', BOB)).body.displayName).toBe('Bobby');

    const tooLong = await request(app).post('/v1/me').set('Authorization', BOB).send({ displayName: 'x'.repeat(101) });
    expect(tooLong.status).toBe(400);
    expect(tooLong.body.error).toBe('bad_request');
    const bigBody = await request(app).post('/v1/me').set('Authorization', BOB).send({ pad: 'x'.repeat(20_000) });
    expect(bigBody.status).toBe(413);
    const badJson = await request(app).post('/v1/me').set('Authorization', BOB).set('Content-Type', 'application/json').send('{nope');
    expect(badJson.status).toBe(400);
    expect(badJson.body.error).toBe('bad_request');
  });

  it('GET is 404 before registration, then returns the user with backup meta', async () => {
    expect((await request(app).get('/v1/me').set('Authorization', ALICE)).status).toBe(404);
    await request(app).post('/v1/me').set('Authorization', ALICE);
    const noBackup = await request(app).get('/v1/me').set('Authorization', ALICE);
    expect(noBackup.status).toBe(200);
    expect(noBackup.body).toMatchObject({ uid: 'alice', backup: null });

    await upload(ALICE, randomBytes(100)).expect(201);
    const withBackup = await request(app).get('/v1/me').set('Authorization', ALICE);
    expect(withBackup.body.backup).toMatchObject({ size: 100, device: 'iQOO Neo 9 Pro' });
  });

  it('DELETE removes the user and their backup, and nobody else\'s', async () => {
    await request(app).post('/v1/me').set('Authorization', ALICE);
    await upload(ALICE, randomBytes(5000)).expect(201);
    await request(app).post('/v1/me').set('Authorization', BOB);
    await upload(BOB, randomBytes(10)).expect(201);

    const res = await request(app).delete('/v1/me').set('Authorization', ALICE);
    expect(res.status).toBe(204);
    expect((await request(app).get('/v1/me').set('Authorization', ALICE)).status).toBe(404);
    expect((await request(app).get('/v1/backup/meta').set('Authorization', ALICE)).status).toBe(404);
    expect(await db.collection('backups.files').countDocuments({ 'metadata.uid': 'alice' })).toBe(0);
    expect(await gridfsCounts()).toEqual({ files: 1, chunks: 1 }); // only Bob's remains
    expect((await request(app).get('/v1/me').set('Authorization', BOB)).status).toBe(200);
  });
});

describe('/v1/backup', () => {
  it('round-trips ciphertext byte-for-byte across several GridFS chunks', async () => {
    const body = randomBytes(700 * 1024 + 13); // spans three 255 KiB GridFS chunks
    const put = await upload(ALICE, body);
    expect(put.status).toBe(201);
    const sha = createHash('sha256').update(body).digest('hex');
    expect(put.body).toEqual({
      size: body.length,
      sha256: sha,
      createdAt: expect.any(String),
      formatVersion: 1,
      kdf: 'PBKDF2-HMAC-SHA256',
      kdfIterations: 600000,
      kdfSalt: Buffer.alloc(16, 7).toString('base64'),
      device: 'iQOO Neo 9 Pro',
    });
    expect(new Date(put.body.createdAt).toISOString()).toBe(put.body.createdAt);

    const meta = await request(app).get('/v1/backup/meta').set('Authorization', ALICE);
    expect(meta.status).toBe(200);
    expect(meta.body).toEqual(put.body);

    const res = await download(ALICE);
    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toBe('application/octet-stream');
    expect(res.headers['content-length']).toBe(String(body.length));
    expect(res.headers['cache-control']).toBe('no-store');
    expect(res.headers['x-backup-format-version']).toBe('1');
    expect(res.headers['x-backup-kdf']).toBe('PBKDF2-HMAC-SHA256');
    expect(res.headers['x-backup-kdf-iterations']).toBe('600000');
    expect(res.headers['x-backup-kdf-salt']).toBe(put.body.kdfSalt);
    expect(res.headers['x-backup-sha256']).toBe(sha);
    expect(res.headers['x-backup-device']).toBe('iQOO Neo 9 Pro');
    expect(Buffer.compare(res.body, body)).toBe(0);
  });

  it('404s when there is no backup, and DELETE is 204', async () => {
    expect((await request(app).get('/v1/backup/meta').set('Authorization', ALICE)).body).toMatchObject({ error: 'no_backup' });
    const none = await request(app).get('/v1/backup').set('Authorization', ALICE);
    expect(none.status).toBe(404);
    expect(none.body.error).toBe('no_backup');
    await upload(ALICE, randomBytes(10)).expect(201);
    expect((await request(app).delete('/v1/backup').set('Authorization', ALICE)).status).toBe(204);
    expect((await request(app).get('/v1/backup/meta').set('Authorization', ALICE)).status).toBe(404);
    expect(await gridfsCounts()).toEqual({ files: 0, chunks: 0 });
  });

  it('rejects a checksum mismatch, deletes the upload and keeps the previous backup', async () => {
    const good = randomBytes(300);
    await upload(ALICE, good).expect(201);
    const res = await upload(ALICE, randomBytes(400), { 'X-Backup-Sha256': 'a'.repeat(64) });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('checksum_mismatch');
    expect(await gridfsCounts()).toEqual({ files: 1, chunks: 1 });
    expect(Buffer.compare((await download(ALICE)).body, good)).toBe(0);
  });

  it.each([
    ['missing format version', { 'X-Backup-Format-Version': '' }],
    ['non-integer format version', { 'X-Backup-Format-Version': '1.5' }],
    ['wrong kdf', { 'X-Backup-Kdf': 'scrypt' }],
    ['too few iterations', { 'X-Backup-Kdf-Iterations': '99999' }],
    ['too many iterations', { 'X-Backup-Kdf-Iterations': '5000001' }],
    ['salt too short', { 'X-Backup-Kdf-Salt': Buffer.alloc(15).toString('base64') }],
    ['salt too long', { 'X-Backup-Kdf-Salt': Buffer.alloc(65).toString('base64') }],
    ['salt not base64', { 'X-Backup-Kdf-Salt': '!!!!notbase64!!!!!!!!!!!' }],
    ['bad sha256', { 'X-Backup-Sha256': 'xyz' }],
    ['device too long', { 'X-Backup-Device': 'd'.repeat(101) }],
  ])('rejects invalid headers: %s', async (_name, overrides) => {
    const res = await upload(ALICE, randomBytes(32), overrides);
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('bad_request');
    expect(await gridfsCounts()).toEqual({ files: 0, chunks: 0 });
  });

  // Raw node:http client (like OkHttp) that keeps writing while the server answers early.
  async function rawPut(body, extraHeaders) {
    const server = app.listen(0);
    try {
      return await new Promise((resolve, reject) => {
        const headers = { Authorization: ALICE, ...backupHeaders(body), ...extraHeaders };
        const req = http.request({ port: server.address().port, method: 'PUT', path: '/v1/backup', headers }, (res) => {
          let text = '';
          res.on('data', (c) => (text += c));
          res.on('end', () => resolve({ status: res.statusCode, json: JSON.parse(text) }));
        });
        req.on('error', reject);
        for (let i = 0; i < body.length; i += 64 * 1024) req.write(body.subarray(i, i + 64 * 1024));
        req.end();
      });
    } finally {
      server.close();
    }
  }

  it.each([
    ['declared Content-Length', (body) => ({ 'Content-Length': String(body.length) })],
    ['chunked, no Content-Length', () => ({ 'Transfer-Encoding': 'chunked' })],
  ])('rejects an oversized upload (%s) with 413 and leaves no GridFS orphan', async (_name, framing) => {
    await upload(ALICE, randomBytes(10)).expect(201); // the existing backup must survive
    const body = randomBytes(MAX + 300 * 1024);
    const { status, json } = await rawPut(body, framing(body));
    expect(status).toBe(413);
    expect(json.error).toBe('too_large');
    expect(await gridfsCounts()).toEqual({ files: 1, chunks: 1 });
    expect((await request(app).get('/v1/backup/meta').set('Authorization', ALICE)).body.size).toBe(10);
  });

  it('cleans up the partial GridFS file when the client disconnects mid-upload', async () => {
    const server = app.listen(0);
    try {
      const body = randomBytes(900 * 1024);
      const req = http.request({
        port: server.address().port,
        method: 'PUT',
        path: '/v1/backup',
        headers: { Authorization: ALICE, 'Content-Length': String(body.length), ...backupHeaders(body) },
      });
      req.on('error', () => {});
      req.write(body.subarray(0, 600 * 1024));
      // Wait until the server has stored at least one chunk, then drop the connection.
      for (let i = 0; i < 100 && (await gridfsCounts()).chunks === 0; i++) await new Promise((r) => setTimeout(r, 20));
      expect((await gridfsCounts()).chunks).toBeGreaterThan(0);
      req.destroy();
      let counts;
      for (let i = 0; i < 100; i++) {
        counts = await gridfsCounts();
        if (counts.files === 0 && counts.chunks === 0) break;
        await new Promise((r) => setTimeout(r, 20));
      }
      expect(counts).toEqual({ files: 0, chunks: 0 });
      expect(await db.collection('backupMeta').countDocuments()).toBe(0);
    } finally {
      server.close();
    }
  });

  it('replacing a backup deletes the old GridFS file', async () => {
    const first = randomBytes(600 * 1024);
    const second = randomBytes(2000);
    await upload(ALICE, first).expect(201);
    const oldId = (await db.collection('backupMeta').findOne({ _id: 'alice' })).fileId;
    await upload(ALICE, second, { 'X-Backup-Device': 'Pixel' }).expect(201);

    expect(await db.collection('backups.files').countDocuments({ _id: oldId })).toBe(0);
    expect(await db.collection('backups.chunks').countDocuments({ files_id: oldId })).toBe(0);
    expect(await gridfsCounts()).toEqual({ files: 1, chunks: 1 });
    const res = await download(ALICE);
    expect(Buffer.compare(res.body, second)).toBe(0);
    expect(res.headers['x-backup-device']).toBe('Pixel');
  });

  it('isolates users: Bob cannot read or delete Alice\'s backup', async () => {
    const alice = randomBytes(1000);
    await upload(ALICE, alice).expect(201);
    expect((await request(app).get('/v1/backup').set('Authorization', BOB)).status).toBe(404);
    expect((await request(app).get('/v1/backup/meta').set('Authorization', BOB)).status).toBe(404);
    expect((await request(app).delete('/v1/backup').set('Authorization', BOB)).status).toBe(204);
    expect((await request(app).delete('/v1/me').set('Authorization', BOB)).status).toBe(204);

    const bob = randomBytes(50);
    await upload(BOB, bob).expect(201);
    expect(Buffer.compare((await download(ALICE)).body, alice)).toBe(0);
    expect(Buffer.compare((await download(BOB)).body, bob)).toBe(0);
  });

  it('rate-limits uploads per user', async () => {
    const limited = createApp({ db, verifyToken, logger: silent, maxBackupBytes: MAX, limits: { uploadsPerHour: 2 } });
    const body = randomBytes(10);
    await upload(ALICE, body, {}, limited).expect(201);
    await upload(ALICE, body, {}, limited).expect(201);
    const res = await upload(ALICE, body, {}, limited);
    expect(res.status).toBe(429);
    expect(res.body.error).toBe('rate_limited');
    await upload(BOB, body, {}, limited).expect(201); // other users are unaffected
  });
});
