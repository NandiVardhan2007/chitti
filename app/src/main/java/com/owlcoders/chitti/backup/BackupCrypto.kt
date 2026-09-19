package com.owlcoders.chitti.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for backups, in the spirit of WhatsApp's encrypted backups: the key is
 * derived on the phone from a password only the user knows, and the server only ever receives
 * ciphertext. Without the password nobody can read a backup: not Chitti, not the server, not
 * Google or MongoDB.
 *
 * Key: PBKDF2-HMAC-SHA256, 600,000 iterations (OWASP 2023), 16-byte random salt -> AES-256.
 *
 * Format (streaming, so a large backup never has to sit in memory):
 *   header = "CHTB" | version:1 | iterations:u32 | saltLen:1 | salt | noncePrefix:7
 *   then chunks of up to 1 MiB plaintext, each: ciphertextLen:u32 | AES-GCM(ciphertext + tag)
 *   nonce(12) = noncePrefix(7) | chunkIndex:u32 | isLast:1, AAD = header.
 * Binding the index and a last-chunk flag into each nonce means chunks cannot be reordered,
 * dropped or cut off without decryption failing (the "STREAM" construction).
 */
object BackupCrypto {

    const val KDF_NAME = "PBKDF2-HMAC-SHA256"
    const val DEFAULT_ITERATIONS = 600_000
    const val FORMAT_VERSION = 1

    private val MAGIC = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte())
    private const val CHUNK = 1 shl 20
    private const val TAG_BITS = 128
    private const val PREFIX_LEN = 7
    private val random = SecureRandom()

    class WrongPasswordException : Exception("Wrong backup password")
    class CorruptBackupException(message: String) : Exception(message)

    data class Header(val iterations: Int, val salt: ByteArray, val noncePrefix: ByteArray, val bytes: ByteArray)

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKey, salt: ByteArray, iterations: Int) {
        val prefix = ByteArray(PREFIX_LEN).also { random.nextBytes(it) }
        val header = headerBytes(iterations, salt, prefix)
        val out = DataOutputStream(output)
        out.write(header)

        var current = readChunk(input)
        var index = 0
        while (true) {
            val next = if (current.size == CHUNK) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index, last)))
            cipher.updateAAD(header)
            val sealed = cipher.doFinal(current)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            current = next
            index++
        }
        out.flush()
    }

    /** Reads just the header, so the caller can derive the key from the salt it carries. */
    fun readHeader(input: InputStream): Header {
        val din = DataInputStream(input)
        val magic = ByteArray(4).also { din.readFullyOr(it) }
        if (!magic.contentEquals(MAGIC)) throw CorruptBackupException("Not a Chitti backup")
        val version = din.readUnsignedByte()
        if (version != FORMAT_VERSION) throw CorruptBackupException("Unsupported backup version $version")
        val iterations = din.readInt()
        val saltLen = din.readUnsignedByte()
        if (saltLen !in 8..64 || iterations !in 10_000..10_000_000) throw CorruptBackupException("Bad backup header")
        val salt = ByteArray(saltLen).also { din.readFullyOr(it) }
        val prefix = ByteArray(PREFIX_LEN).also { din.readFullyOr(it) }
        return Header(iterations, salt, prefix, headerBytes(iterations, salt, prefix))
    }

    /** Decrypts everything after [header] (already read from [input]) into [output]. */
    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey, header: Header) {
        val din = DataInputStream(input)
        var pending = readSealedChunk(din) ?: throw CorruptBackupException("Backup is empty")
        var index = 0
        while (true) {
            val following = readSealedChunk(din)
            val last = following == null
            val plain = openChunk(pending, key, header, index, last) ?: run {
                // A first chunk that opens with the other last-flag was cut short, not mis-keyed.
                if (index == 0 && openChunk(pending, key, header, 0, !last) == null) throw WrongPasswordException()
                throw CorruptBackupException("Backup is damaged or incomplete")
            }
            output.write(plain)
            if (last) break
            pending = following
            index++
        }
        output.flush()
    }

    private fun openChunk(sealed: ByteArray, key: SecretKey, header: Header, index: Int, last: Boolean): ByteArray? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(header.noncePrefix, index, last)))
        cipher.updateAAD(header.bytes)
        return try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            null
        }
    }

    private fun headerBytes(iterations: Int, salt: ByteArray, prefix: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + 1 + 4 + 1 + salt.size + PREFIX_LEN)
            .put(MAGIC).put(FORMAT_VERSION.toByte()).putInt(iterations).put(salt.size.toByte()).put(salt).put(prefix)
            .array()

    private fun nonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(index).put(if (last) 1 else 0).array()

    private fun readChunk(input: InputStream): ByteArray {
        val buf = ByteArray(CHUNK)
        var filled = 0
        while (filled < CHUNK) {
            val n = input.read(buf, filled, CHUNK - filled)
            if (n < 0) break
            filled += n
        }
        return if (filled == CHUNK) buf else buf.copyOf(filled)
    }

    private fun readSealedChunk(din: DataInputStream): ByteArray? {
        val len = try {
            din.readInt()
        } catch (e: EOFException) {
            return null
        }
        if (len !in 16..(CHUNK + 16)) throw CorruptBackupException("Backup is damaged")
        return ByteArray(len).also { din.readFullyOr(it) }
    }

    private fun DataInputStream.readFullyOr(buf: ByteArray) {
        try {
            readFully(buf)
        } catch (e: EOFException) {
            throw CorruptBackupException("Backup is incomplete")
        }
    }
}
