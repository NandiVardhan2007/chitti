import { randomUUID } from 'node:crypto';
import express from 'express';
import helmet from 'helmet';
import { rateLimit, ipKeyGenerator } from 'express-rate-limit';
import { pinoHttp } from 'pino-http';
import { backupRouter, deleteBackup, getBackupMeta } from './backup.js';
import { HttpError } from './errors.js';

/**
 * Builds the Express app. Everything external is injected so tests can run without Firebase:
 *  - db: a connected mongodb `Db`
 *  - verifyToken(idToken) -> decoded Firebase token ({ uid, email, ... }); throws if invalid/revoked
 */
export function createApp({ db, verifyToken, logger, maxBackupBytes = 52428800, limits = {} }) {
  const { requestsPer15Min = 100, uploadsPerHour = 10 } = limits;
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1); // Render terminates TLS in one proxy hop in front of the service.
  app.use(helmet());
  app.use(
    pinoHttp({
      logger,
      genReqId: (req, res) => {
        const incoming = req.get('x-request-id');
        const id = incoming && /^[\w-]{1,64}$/.test(incoming) ? incoming : randomUUID();
        res.setHeader('X-Request-Id', id);
        return id;
      },
      autoLogging: { ignore: (req) => req.url === '/health' || req.url === '/ping' },
      // Log only what we need: never headers (Authorization), bodies or backup bytes.
      serializers: {
        req: (req) => ({ id: req.id, method: req.method, url: req.url }),
        res: (res) => ({ statusCode: res.statusCode }),
      },
    }),
  );

  app.get('/health', (req, res) => res.json({ ok: true }));
  // For an uptime monitor that keeps the free instance awake: no database work, never cached, not logged.
  app.get('/ping', (req, res) => res.set('Cache-Control', 'no-store').type('text/plain').send('pong'));

  const limiter = (windowMs, limit, key) =>
    rateLimit({
      windowMs,
      limit,
      standardHeaders: 'draft-7',
      legacyHeaders: false,
      keyGenerator: key,
      message: { error: 'rate_limited', message: 'Too many requests, try again later' },
    });
  const byIp = (req) => ipKeyGenerator(req.ip);
  const byUid = (req) => (req.auth?.uid ? `uid:${req.auth.uid}` : byIp(req));

  const v1 = express.Router();
  // Coarse per-IP limit before auth so bad tokens cannot hammer the verifier, then the per-user limit.
  v1.use(limiter(15 * 60_000, requestsPer15Min * 3, byIp));
  v1.use(requireAuth(verifyToken));
  v1.use((req, res, next) => {
    res.set('Cache-Control', 'no-store');
    next();
  });
  v1.use(limiter(15 * 60_000, requestsPer15Min, byUid));
  v1.put('/backup', limiter(60 * 60_000, uploadsPerHour, byUid));

  const json = express.json({ limit: '10kb' });
  const users = db.collection('users');

  v1.post('/me', json, async (req, res) => {
    const body = req.body ?? {};
    if (typeof body !== 'object' || Array.isArray(body)) throw new HttpError(400, 'bad_request', 'Body must be a JSON object');
    if (body.displayName !== undefined && (typeof body.displayName !== 'string' || body.displayName.length > 100)) {
      throw new HttpError(400, 'bad_request', 'displayName must be a string of at most 100 characters');
    }
    const t = req.auth;
    const now = new Date();
    const displayName = t.name || body.displayName?.trim() || null;
    const provider = t.firebase?.sign_in_provider;
    const update = {
      $set: { email: t.email ?? null, emailVerified: t.email_verified === true, lastLoginAt: now },
      $setOnInsert: { createdAt: now },
    };
    if (displayName) update.$set.displayName = displayName;
    else update.$setOnInsert.displayName = null;
    if (provider) update.$addToSet = { providers: provider };
    else update.$setOnInsert.providers = [];
    const doc = await users.findOneAndUpdate({ _id: t.uid }, update, { upsert: true, returnDocument: 'after' });
    res.json(toUser(doc));
  });

  v1.get('/me', async (req, res) => {
    const doc = await users.findOne({ _id: req.auth.uid });
    if (!doc) throw new HttpError(404, 'not_found', 'User not registered; call POST /v1/me first');
    res.json({ ...toUser(doc), backup: await getBackupMeta(db, req.auth.uid) });
  });

  v1.delete('/me', async (req, res) => {
    await deleteBackup(db, req.auth.uid);
    await users.deleteOne({ _id: req.auth.uid });
    res.status(204).end();
  });

  v1.use('/backup', backupRouter({ db, maxBackupBytes }));
  app.use('/v1', v1);

  app.use((req, res) => res.status(404).json({ error: 'not_found', message: 'No such route' }));

  // Express 5 forwards rejected promises from async handlers here.
  app.use((err, req, res, _next) => {
    let status = err.status ?? err.statusCode ?? 500;
    let code = err.code;
    if (err instanceof HttpError) {
      // already mapped
    } else if (err.type === 'entity.too.large') {
      code = 'too_large';
    } else if (err.type === 'entity.parse.failed') {
      code = 'bad_request';
    } else {
      status = 500;
      code = 'internal';
    }
    if (status >= 500) req.log.error({ err }, 'request failed');
    if (res.headersSent || res.destroyed) return res.destroy();
    res.status(status).json({ error: code, message: status >= 500 ? 'Internal server error' : err.message });
  });

  return app;
}

function requireAuth(verifyToken) {
  return async (req, res, next) => {
    const match = /^Bearer\s+(\S+)\s*$/i.exec(req.get('authorization') ?? '');
    let decoded = null;
    if (match) {
      try {
        decoded = await verifyToken(match[1]);
      } catch {
        decoded = null; // invalid, expired or revoked: never log the token or the reason detail
      }
    }
    if (!decoded?.uid) return res.status(401).json({ error: 'unauthorized', message: 'Missing or invalid ID token' });
    req.auth = decoded;
    next();
  };
}

function toUser(doc) {
  const { _id, ...rest } = doc;
  return { uid: _id, ...rest };
}
