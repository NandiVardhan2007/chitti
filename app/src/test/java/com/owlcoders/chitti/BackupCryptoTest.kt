package com.owlcoders.chitti

import com.owlcoders.chitti.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {

    // Real backups use 600,000 iterations; the format is identical, tests just run faster.
    private val iterations = 10_000

    private fun seal(plain: ByteArray, password: String): ByteArray {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password.toCharArray(), salt, iterations)
        return ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(plain), it, key, salt, iterations) }.toByteArray()
    }

    private fun open(sealed: ByteArray, password: String): ByteArray {
        val input = ByteArrayInputStream(sealed)
        val header = BackupCrypto.readHeader(input)
        val key = BackupCrypto.deriveKey(password.toCharArray(), header.salt, header.iterations)
        return ByteArrayOutputStream().also { BackupCrypto.decrypt(input, it, key, header) }.toByteArray()
    }

    @Test fun roundTripSmallEmptyAndMultiChunk() {
        for (size in listOf(0, 17, (1 shl 20), (1 shl 20) + 1, 3 * (1 shl 20) + 12345)) {
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("size $size", plain, open(seal(plain, "correct horse"), "correct horse"))
        }
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val plain = "Aadhaar 2345 6789 0123 PAN ABCPR1234K".repeat(50).toByteArray()
        val sealed = String(seal(plain, "pw12345678"), Charsets.ISO_8859_1)
        assertFalse(sealed.contains("ABCPR1234K"))
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordIsDetected() {
        open(seal(Random(1).nextBytes(5000), "right password"), "wrong password")
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordOnMultiChunkIsDetected() {
        open(seal(Random(2).nextBytes(3 * (1 shl 20)), "right password"), "wrong password")
    }

    @Test fun truncationIsDetected() {
        val sealed = seal(Random(3).nextBytes(3 * (1 shl 20)), "pw")
        // Cut off the final chunk entirely: every remaining chunk is intact, but the stream ends early.
        val lastChunkLen = (1 shl 20) + 16 + 4
        val cut = sealed.copyOf(sealed.size - lastChunkLen)
        assertTrue(runCatching { open(cut, "pw") }.exceptionOrNull() is BackupCrypto.CorruptBackupException)
    }

    @Test fun tamperingIsDetected() {
        val sealed = seal(Random(4).nextBytes(200_000), "pw")
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()
        val error = runCatching { open(sealed, "pw") }.exceptionOrNull()
        assertTrue(error is BackupCrypto.WrongPasswordException || error is BackupCrypto.CorruptBackupException)
    }

    @Test fun notABackupIsRejected() {
        val error = runCatching { open("hello world, not a backup".toByteArray(), "pw") }.exceptionOrNull()
        assertTrue(error is BackupCrypto.CorruptBackupException)
    }
}
