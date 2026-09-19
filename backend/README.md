# Chitti backend

A small Node 22 / Express 5 service for the Chitti Android app. It does two things:

1. **Accounts.** Sign-in happens on the phone with Firebase Authentication. The app sends the Firebase ID token
   with every request; this server verifies it with `firebase-admin` (including the revocation check) and keeps a
   small user record in MongoDB.
2. **End-to-end encrypted backups**, like WhatsApp's. The phone encrypts the backup with a key derived from a
   backup password that only the user knows (PBKDF2-HMAC-SHA256 -> AES-256). The server stores **only the
   ciphertext** (in MongoDB GridFS) plus the public KDF parameters (salt, iterations) the phone needs to derive
   the key again. Each account keeps only its latest backup.

## API

All `/v1/*` routes need `Authorization: Bearer <Firebase ID token>`. A missing, invalid, expired or revoked token
gets `401 {"error":"unauthorized"}`. Every route acts only on the token's `uid`, so one user can never reach
another user's data. Errors are JSON: `{"error":"<code>","message":"..."}`.

| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `GET /health` | no | - | `200 {"ok":true}` | - |
| `POST /v1/me` | yes | optional JSON `{"displayName": string<=100}` | `200` user | `400 bad_request`, `413 too_large` (JSON over 10 kB) |
| `GET /v1/me` | yes | - | `200 {...user, "backup": meta \| null}` | `404 not_found` (call `POST /v1/me` first) |
| `DELETE /v1/me` | yes | - | `204`; deletes the user record **and** the backup | - |
| `PUT /v1/backup` | yes | raw `application/octet-stream` ciphertext + `X-Backup-*` headers (below) | `201` meta | `400 bad_request`, `400 checksum_mismatch`, `413 too_large`, `429 rate_limited` |
| `GET /v1/backup/meta` | yes | - | `200` meta | `404 no_backup` |
| `GET /v1/backup` | yes | - | `200` ciphertext stream with `Content-Length` and the same `X-Backup-*` headers | `404 no_backup` |
| `DELETE /v1/backup` | yes | - | `204` | - |

**User** (`POST /v1/me`): `{"uid","email","emailVerified","displayName","providers":[...],"createdAt","lastLoginAt"}`.
Email, verification status and sign-in provider come from the verified token. `displayName` comes from the token,
or from the request body when the token has none. Calling it again is safe: `createdAt` stays the same and
`lastLoginAt` is updated.

**Upload headers** (all required):

| Header | Rule |
|---|---|
| `X-Backup-Format-Version` | integer (currently `1`) |
| `X-Backup-Kdf` | exactly `PBKDF2-HMAC-SHA256` |
| `X-Backup-Kdf-Iterations` | integer, 100000..5000000 |
| `X-Backup-Kdf-Salt` | standard padded base64 (Android `Base64.NO_WRAP`) of 16..64 bytes |
| `X-Backup-Sha256` | hex SHA-256 of the body (ciphertext) |
| `X-Backup-Device` | device name, at most 100 characters (keep it ASCII) |

**Meta**: `{"size":int,"sha256":hex,"createdAt":ISO-8601,"formatVersion":int,"kdf":"PBKDF2-HMAC-SHA256","kdfIterations":int,"kdfSalt":base64,"device":string}`.

The body is streamed into GridFS while the server hashes it, so a 50 MB upload never sits in memory. If the body
goes over `MAX_BACKUP_BYTES`, the SHA-256 does not match, or the client disconnects, the partial file is deleted.
Only after a successful upload is the user's backup pointer swapped (one atomic MongoDB operation), and then the
previous file is deleted.

**Limits**: 100 requests per 15 minutes per user on `/v1` (plus a looser per-IP limit before auth), and
10 uploads per hour per user. JSON bodies are limited to 10 kB.

## Security model

- **The server stores ciphertext only.** The backup password and the key derived from it never leave the phone.
  The KDF salt and iteration count are not secret; they are stored so a new phone can derive the same key from
  the password. Without the password, neither the server, its operator nor a database thief can decrypt a backup.
- **If the user forgets the backup password, the backup cannot be recovered.** That is the cost of end-to-end
  encryption.
- **What the server can see:** the Firebase `uid`, email address and whether it is verified, the display name,
  the sign-in provider, account creation and last login times, and for the backup: its size, SHA-256 of the
  ciphertext, upload time, KDF parameters and the device name.
- **Integrity:** the SHA-256 header protects against corruption in transit. The app's authenticated encryption
  (AES-GCM) is what protects against tampering; the server cannot forge a valid backup without the key.
- **Authentication:** `verifyIdToken(token, true)` checks the signature, expiry, audience (`FIREBASE_PROJECT_ID`)
  and whether the token has been revoked.
- **Logging:** request logs contain only a request id, method, URL, status and timing. Headers (including the
  token), bodies and backup data are never logged. `X-Request-Id` is returned on every response.
- `helmet()`, no `X-Powered-By`, `Cache-Control: no-store` on `/v1`, and `trust proxy` set to one hop for
  Render's load balancer, so rate limits see the real client IP.

## Run locally

```bash
cd backend
npm install
cp .env.example .env     # then fill in MONGODB_URI (local mongod or Atlas)
npm run dev              # node --env-file=.env --watch src/server.js
curl localhost:8080/health
```

Without `FIREBASE_SERVICE_ACCOUNT_BASE64` (allowed only when `NODE_ENV` is not `production`), ID tokens are still
verified against Google's public keys, but revocation is not checked.

### Tests

```bash
cd backend
npm test
```

The tests use an in-memory MongoDB (`mongodb-memory-server`; the first run downloads a MongoDB binary, about
100 MB, and later runs work offline) and a fake token verifier instead of Firebase. They cover: 401s on every
route, idempotent upserts, user isolation, byte-for-byte upload/download round trips, checksum mismatch, invalid
headers, oversized uploads with and without `Content-Length` (no GridFS files or chunks left behind), client
disconnects mid-upload, replacing a backup deleting the old file, `DELETE /v1/me` removing the backup, and
upload rate limiting.

### Environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `MONGODB_URI` | yes | - | Atlas `mongodb+srv://...` connection string |
| `MONGODB_DB` | no | `chitti` | |
| `FIREBASE_PROJECT_ID` | yes | - | `chitti-bd8ce` |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | in production | - | base64 of the service-account JSON |
| `PORT` | no | `8080` | Render sets this |
| `MAX_BACKUP_BYTES` | no | `52428800` (50 MB) | |
| `LOG_LEVEL` | no | `info` | pino level |

## Deploy: MongoDB Atlas + Firebase + Render (all free tiers)

### 1. MongoDB Atlas

1. Create an account at <https://cloud.mongodb.com> and a project, then **Create** a cluster: choose **M0 (Free)**
   and a region close to your Render region (for example AWS Singapore or Mumbai for users in India).
2. **Database Access** -> **Add New Database User**: password authentication, generate a strong password, and
   give it the built-in role **Read and write to any database** (or, stricter, `readWrite` on the `chitti` database only).
3. **Network Access** -> **Add IP Address** -> `0.0.0.0/0` (allow from anywhere).
   *Trade-off:* Render's free tier has no static outbound IPs, so you cannot allowlist it. The database is then
   protected only by the user's credentials and TLS, so use a long random password and keep the URI secret.
   Paid Render plans have static outbound IPs if you later want to lock this down.
4. **Connect** -> **Drivers** -> copy the `mongodb+srv://<user>:<password>@.../?retryWrites=true&w=majority`
   string and put the password in it. This is `MONGODB_URI`. The collections (`users`, `backupMeta`,
   `backups.files`, `backups.chunks`) are created automatically. Note that M0 has 512 MB of storage, so about ten
   users with 50 MB backups will fill it.

### 2. Firebase service account

1. Firebase console -> project **chitti-bd8ce** -> gear icon -> **Project settings** -> **Service accounts**.
2. **Generate new private key** -> confirm. A JSON file downloads. **Never commit it** (`.gitignore` blocks
   `service-account*.json` and `backend/*.json.key`, but keep it outside the repo anyway).
3. Base64-encode it on one line:
   - macOS/Linux/Git Bash: `base64 -w0 service-account.json` (macOS: `base64 -i service-account.json`)
   - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("service-account.json"))`
4. The output is `FIREBASE_SERVICE_ACCOUNT_BASE64`. After pasting it into Render, delete the local JSON file or
   store it in a password manager.

### 3. Render

1. Push the repo to GitHub (the `backend/` folder with `package.json` and `package-lock.json`).
2. At <https://dashboard.render.com>: **New** -> **Blueprint** -> connect the repository -> set **Blueprint Path**
   to `backend/render.yaml`. Render reads the file and proposes one free web service, `chitti-backend`
   (`rootDir: backend`, `npm ci`, `npm start`, health check `/health`).
   *(Or create it by hand: **New** -> **Web Service**, root directory `backend`, build `npm ci`, start `npm start`,
   instance type Free, health check path `/health`, and the env vars below.)*
3. Render asks for the `sync: false` secrets: paste `MONGODB_URI` and `FIREBASE_SERVICE_ACCOUNT_BASE64`.
   `NODE_ENV=production`, `FIREBASE_PROJECT_ID=chitti-bd8ce`, `MONGODB_DB` and `MAX_BACKUP_BYTES` are already set
   by the Blueprint.
4. Deploy, then check `https://<service>.onrender.com/health` returns `{"ok":true}`. Put that base URL into the
   Android app's backend setting.

Free-tier notes: the instance sleeps after about 15 minutes idle and takes 30 to 60 seconds to wake. The app calls
`GET /health` first to wake it. RAM is 512 MB, which is why uploads are streamed. Render sends `SIGTERM` on
redeploy; the server stops accepting connections, lets in-flight requests finish (up to 25 s) and closes MongoDB.

## Layout

```
src/server.js   env config, firebase-admin, MongoDB connection, listen, graceful shutdown
src/app.js      Express app: middleware, auth, rate limits, /v1/me, error handling
src/backup.js   /v1/backup: header validation, streaming GridFS upload/download, atomic replace
src/errors.js   HttpError
test/           vitest + supertest + mongodb-memory-server
render.yaml     Render Blueprint
```
