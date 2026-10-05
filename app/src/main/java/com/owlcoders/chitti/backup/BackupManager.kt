package com.owlcoders.chitti.backup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.owlcoders.chitti.account.Auth
import com.owlcoders.chitti.account.Backend
import com.owlcoders.chitti.account.BackupMeta
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.documents.IdentityStore
import com.owlcoders.chitti.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encrypted backup and restore, modelled on WhatsApp:
 *  - the user sets a backup password once; the key derived from it is kept on this phone sealed in
 *    the [Vault], so later backups (including automatic ones) don't ask again;
 *  - a backup is the database plus every vault file (documents, ID details), zipped, encrypted
 *    with [BackupCrypto], and only then uploaded. Plain copies exist only in the app's private
 *    cache and are deleted as soon as the encrypted file is written;
 *  - restoring downloads the ciphertext, and nothing on the phone is replaced until the password
 *    has decrypted the whole backup successfully.
 * The server keeps only the latest backup.
 */
object BackupManager {

    enum class Stage { Preparing, Encrypting, Uploading, Downloading, Decrypting, Restoring }

    private const val KEY_FILE = "backup-key.bin"
    private const val PREFS = "chitti_backup"
    private const val DB_ENTRY = "database/${AppDatabase.NAME}"
    private const val WORK = "chitti-auto-backup"

    enum class Frequency { Off, Daily, Weekly }

    data class Status(val lastBackupAt: Long, val lastBackupSize: Long, val frequency: Frequency, val wifiOnly: Boolean)

    fun status(context: Context): Status {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Status(
            lastBackupAt = p.getLong("last_at", 0L),
            lastBackupSize = p.getLong("last_size", 0L),
            frequency = runCatching { Frequency.valueOf(p.getString("frequency", "Off")!!) }.getOrDefault(Frequency.Off),
            wifiOnly = p.getBoolean("wifi_only", true)
        )
    }

    fun hasPassword(context: Context): Boolean = runCatching { Vault.read(context, KEY_FILE) != null }.getOrDefault(false)

    /** Derives and keeps the backup key. Takes a second or two by design (600,000 PBKDF2 rounds). */
    suspend fun setPassword(context: Context, password: CharArray) = withContext(Dispatchers.Default) {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        storeKey(context, key.encoded, salt, BackupCrypto.DEFAULT_ITERATIONS)
    }

    private fun storeKey(context: Context, key: ByteArray, salt: ByteArray, iterations: Int) {
        val json = JSONObject()
            .put("key", Base64.encodeToString(key, Base64.NO_WRAP))
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iterations", iterations)
        Vault.write(context, KEY_FILE, json.toString().toByteArray())
    }

    private data class StoredKey(val key: ByteArray, val salt: ByteArray, val iterations: Int)

    private fun loadKey(context: Context): StoredKey? {
        val o = Vault.read(context, KEY_FILE)?.let { JSONObject(String(it)) } ?: return null
        return StoredKey(
            Base64.decode(o.getString("key"), Base64.NO_WRAP),
            Base64.decode(o.getString("salt"), Base64.NO_WRAP),
            o.getInt("iterations")
        )
    }

    // ---------------------------------------------------------------- backup

    suspend fun backUpNow(context: Context, onProgress: (Stage, Float) -> Unit = { _, _ -> }): BackupMeta = withContext(Dispatchers.IO) {
        val stored = loadKey(context) ?: throw IllegalStateException("Set a backup password first.")
        val cache = File(context.cacheDir, "backup").apply { mkdirs() }
        val plain = File(cache, "plain.zip")
        val sealed = File(cache, "backup.chtb")
        try {
            onProgress(Stage.Preparing, 0f)
            Backend.wake()
            writeZip(context, plain)

            onProgress(Stage.Encrypting, 0f)
            plain.inputStream().use { input ->
                sealed.outputStream().use { out ->
                    BackupCrypto.encrypt(input, out, SecretKeySpec(stored.key, "AES"), stored.salt, stored.iterations)
                }
            }
            plain.delete()

            onProgress(Stage.Uploading, 0f)
            val meta = Backend.uploadBackup(
                file = sealed,
                sha256 = sha256Hex(sealed),
                iterations = stored.iterations,
                saltBase64 = Base64.encodeToString(stored.salt, Base64.NO_WRAP),
                // HTTP headers must be ASCII; some device names are not.
                device = "${Build.MANUFACTURER} ${Build.MODEL}".filter { it.code in 32..126 }.ifBlank { "Android" },
                onProgress = { onProgress(Stage.Uploading, it) }
            )
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("last_at", System.currentTimeMillis())
                .putLong("last_size", meta.size)
                .apply()
            meta
        } finally {
            plain.delete()
            sealed.delete()
        }
    }

    private fun writeZip(context: Context, target: File) {
        val db = AppDatabase.getDatabase(context)
        // Fold the write-ahead log into the main file so one file is the whole database.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val manifest = JSONObject()
                .put("app", "chitti")
                .put("formatVersion", BackupCrypto.FORMAT_VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put("databaseVersion", 6)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            // Vault files go in decrypted: the device key cannot leave this phone, so the backup
            // carries the plaintext, protected by the backup password instead. The backup key
            // itself is never included.
            for (name in Vault.list(context)) {
                if (name == KEY_FILE) continue
                val bytes = Vault.read(context, name) ?: continue
                zip.putNextEntry(ZipEntry("vault/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * Downloads and decrypts the backup with [password], then replaces this phone's data with it.
     * Throws [BackupCrypto.WrongPasswordException] before anything is replaced if the password is
     * wrong. After success the caller should call [restartApp].
     */
    suspend fun restore(context: Context, password: CharArray, onProgress: (Stage, Float) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "restore").apply { mkdirs() }
        val sealed = File(cache, "backup.chtb")
        val plain = File(cache, "plain.zip")
        try {
            onProgress(Stage.Downloading, 0f)
            Backend.downloadBackup(sealed) { onProgress(Stage.Downloading, it) }

            onProgress(Stage.Decrypting, 0f)
            val key = sealed.inputStream().buffered().use { input ->
                val header = BackupCrypto.readHeader(input)
                val key = BackupCrypto.deriveKey(password, header.salt, header.iterations)
                plain.outputStream().use { out -> BackupCrypto.decrypt(input, out, key, header) }
                Triple(key.encoded, header.salt, header.iterations)
            }

            onProgress(Stage.Restoring, 0f)
            val entries = readZip(plain)
            val dbBytes = entries[DB_ENTRY] ?: throw BackupCrypto.CorruptBackupException("Backup has no database")

            AppDatabase.closeForRestore()
            val dbFile = context.getDatabasePath(AppDatabase.NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbFile.writeBytes(dbBytes)

            for (name in Vault.list(context)) if (name != KEY_FILE) Vault.delete(context, name)
            entries.filterKeys { it.startsWith("vault/") }.forEach { (path, bytes) ->
                Vault.write(context, path.removePrefix("vault/"), bytes)
            }
            // Keep the key so this phone can keep backing up without asking for the password.
            storeKey(context, key.first, key.second, key.third)
            IdentityStore.invalidate()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("restored", true).apply()
        } finally {
            sealed.delete()
            plain.delete()
        }
    }

    private fun readZip(file: File): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                // Only the paths a Chitti backup can contain; anything else (or "..") is ignored.
                val allowed = name == "manifest.json" || name == DB_ENTRY ||
                    (name.startsWith("vault/") && name.removePrefix("vault/").matches(Regex("[A-Za-z0-9._-]+")))
                if (allowed && !entry.isDirectory) out[name] = zip.readBytes()
            }
        }
        return out
    }

    /** Relaunches Chitti so every screen reopens on the restored database. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    // ---------------------------------------------------------------- automatic backups

    fun setSchedule(context: Context, frequency: Frequency, wifiOnly: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("frequency", frequency.name).putBoolean("wifi_only", wifiOnly).apply()
        val wm = WorkManager.getInstance(context)
        if (frequency == Frequency.Off) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(if (frequency == Frequency.Daily) 1L else 7L, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Runs a scheduled backup when signed in and a backup password exists; otherwise does nothing. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Auth.init(applicationContext)
        if (Auth.currentUser.value == null || !BackupManager.hasPassword(applicationContext)) return Result.success()
        return try {
            BackupManager.backUpNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
