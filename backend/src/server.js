import { MongoClient } from 'mongodb';
import { initializeApp, cert } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { pino } from 'pino';
import { createApp } from './app.js';

const logger = pino({ level: process.env.LOG_LEVEL ?? 'info' });

function required(name) {
  const value = process.env[name];
  if (!value) {
    logger.fatal(`Missing required environment variable ${name}`);
    process.exit(1);
  }
  return value;
}

const mongoUri = required('MONGODB_URI');
const projectId = required('FIREBASE_PROJECT_ID');
const serviceAccountB64 = process.env.FIREBASE_SERVICE_ACCOUNT_BASE64;
const maxBackupBytes = Number(process.env.MAX_BACKUP_BYTES) || 52428800;
const port = Number(process.env.PORT) || 8080;

// Signature, expiry, audience and issuer checks only need the project id (Google's public keys).
// The service account adds one thing: the revocation check, which calls the Firebase Auth API.
// It is optional because some projects forbid service-account keys; ID tokens expire within an
// hour, so without it a revoked session can last at most that long.
let checkRevoked = false;
if (serviceAccountB64) {
  const serviceAccount = JSON.parse(Buffer.from(serviceAccountB64, 'base64').toString('utf8'));
  if (serviceAccount.project_id !== projectId) {
    logger.fatal(`FIREBASE_SERVICE_ACCOUNT_BASE64 is for project ${serviceAccount.project_id}, not ${projectId}`);
    process.exit(1);
  }
  initializeApp({ credential: cert(serviceAccount), projectId });
  checkRevoked = true;
} else {
  initializeApp({ projectId });
  logger.info('No service account: ID tokens are verified, revocation is not checked');
}
const firebaseAuth = getAuth();
const verifyToken = (idToken) => firebaseAuth.verifyIdToken(idToken, checkRevoked);

const client = new MongoClient(mongoUri, { maxPoolSize: 10 });
await client.connect();
const db = client.db(process.env.MONGODB_DB || 'chitti');

const app = createApp({ db, verifyToken, logger, maxBackupBytes });
const server = app.listen(port, () => logger.info({ port }, 'chitti-backend listening'));
server.requestTimeout = 15 * 60_000; // allow a 50 MB upload over a slow mobile link

let shuttingDown = false;
function shutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  logger.info({ signal }, 'shutting down');
  setTimeout(() => process.exit(1), 25_000).unref(); // Render sends SIGKILL ~30 s after SIGTERM
  server.close(async () => {
    await client.close().catch(() => {});
    process.exit(0);
  });
  server.closeIdleConnections();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
