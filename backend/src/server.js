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

const production = process.env.NODE_ENV === 'production';
const mongoUri = required('MONGODB_URI');
const projectId = required('FIREBASE_PROJECT_ID');
const serviceAccountB64 = production ? required('FIREBASE_SERVICE_ACCOUNT_BASE64') : process.env.FIREBASE_SERVICE_ACCOUNT_BASE64;
const maxBackupBytes = Number(process.env.MAX_BACKUP_BYTES) || 52428800;
const port = Number(process.env.PORT) || 8080;

// Signature checks only need the project id (Google's public keys); the revocation check calls the
// Firebase Auth API and therefore needs the service account.
let checkRevoked = true;
if (serviceAccountB64) {
  const serviceAccount = JSON.parse(Buffer.from(serviceAccountB64, 'base64').toString('utf8'));
  initializeApp({ credential: cert(serviceAccount), projectId });
} else {
  initializeApp({ projectId });
  checkRevoked = false;
  logger.warn('FIREBASE_SERVICE_ACCOUNT_BASE64 not set: ID tokens are verified but revocation is NOT checked (dev only)');
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
