package com.owlcoders.chitti.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File

/**
 * The vault: encrypted storage for everything sensitive (scanned ID documents, Aadhaar / PAN /
 * ration card numbers, the backup key).
 *
 * Data is sealed with AES-256-GCM through Tink. The AES key itself is wrapped by a master key
 * that lives in the Android Keystore (hardware-backed on devices with a TEE / StrongBox), so it
 * can never be read out of the phone, not even by Chitti. Copying the app's files to another
 * device yields ciphertext nobody can open. Android auto-backup is disabled for the same reason;
 * Chitti's own backup re-encrypts with the user's backup password instead (see BackupCrypto).
 *
 * Every blob is bound to its purpose with associated data (e.g. the file name), so a ciphertext
 * cannot be swapped into another slot.
 */
object Vault {

    private const val KEYSET_NAME = "chitti_vault_keyset"
    private const val PREFS = "chitti_vault_prefs"
    private const val MASTER_KEY_URI = "android-keystore://chitti_vault_master_key"
    private const val DIR = "vault"

    @Volatile
    private var aead: Aead? = null

    private fun aead(context: Context): Aead {
        aead?.let { return it }
        return synchronized(this) {
            aead ?: run {
                AeadConfig.register()
                val handle = AndroidKeysetManager.Builder()
                    .withSharedPref(context.applicationContext, KEYSET_NAME, PREFS)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java).also { aead = it }
            }
        }
    }

    fun seal(context: Context, plain: ByteArray, purpose: String): ByteArray =
        aead(context).encrypt(plain, purpose.toByteArray())

    fun open(context: Context, sealed: ByteArray, purpose: String): ByteArray =
        aead(context).decrypt(sealed, purpose.toByteArray())

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun file(context: Context, name: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Bad vault file name" }
        return File(dir(context), name)
    }

    /** Encrypts [plain] into the vault under [name], replacing any previous content atomically. */
    fun write(context: Context, name: String, plain: ByteArray) {
        val target = file(context, name)
        val tmp = File(target.parentFile, "$name.tmp")
        tmp.writeBytes(seal(context, plain, "file:$name"))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun read(context: Context, name: String): ByteArray? {
        val f = file(context, name)
        if (!f.exists()) return null
        return open(context, f.readBytes(), "file:$name")
    }

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }

    fun list(context: Context): List<String> =
        dir(context).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { it.name }.orEmpty()
}
