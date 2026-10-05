package com.owlcoders.chitti.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.owlcoders.chitti.db.entities.PersonalDocument
import com.owlcoders.chitti.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** A document that has been read but not saved yet: the user reviews [fields] first. */
class DocumentDraft(
    val bytes: ByteArray,
    val mimeType: String,
    val pageCount: Int,
    val fields: ExtractedFields
)

sealed interface IntakeResult {
    data class Ready(val draft: DocumentDraft) : IntakeResult
    /** The PDF is password protected (e-Aadhaar is). Ask for the password and try again. */
    data object NeedsPassword : IntakeResult
    data class Failed(val message: String) : IntakeResult
}

/**
 * Turns a scan, a photo or a PDF into a [DocumentDraft], all on the phone:
 *  - text is read with ML Kit's on-device recogniser (nothing is uploaded);
 *  - photos are re-encoded, which strips EXIF metadata such as GPS location, and resized so a
 *    card photo does not take 10 MB of vault space;
 *  - PDFs are rendered page by page for recognition and kept as the original file.
 */
object DocumentIntake {

    private const val TAG = "DocumentIntake"
    private const val MAX_EDGE = 2200
    private const val MAX_PDF_PAGES = 4

    suspend fun fromImages(context: Context, uris: List<Uri>): IntakeResult = withContext(Dispatchers.IO) {
        try {
            val bitmaps = uris.mapNotNull { decodeScaled(context, it) }
            if (bitmaps.isEmpty()) return@withContext IntakeResult.Failed("Couldn't open that image.")
            val text = StringBuilder()
            for (b in bitmaps) text.append(recognise(b)).append('\n')
            // Several pages (a scan of both sides) are kept as one stacked JPEG.
            val stored = if (bitmaps.size == 1) bitmaps[0] else stack(bitmaps)
            val bytes = ByteArrayOutputStream().use { out ->
                stored.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            IntakeResult.Ready(DocumentDraft(bytes, "image/jpeg", bitmaps.size, IdParser.parse(text.toString())))
        } catch (e: Exception) {
            Log.w(TAG, "Image intake failed: ${e.message}")
            IntakeResult.Failed("Couldn't read that image.")
        }
    }

    suspend fun fromPdf(context: Context, uri: Uri, password: String? = null): IntakeResult = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@withContext IntakeResult.Failed("Couldn't open that PDF.")

        // PdfRenderer needs a seekable file; use a private temp copy and delete it straight after.
        val tmp = File.createTempFile("intake", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                if (password != null && Build.VERSION.SDK_INT >= 35) {
                    PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())
                } else {
                    PdfRenderer(pfd)
                }
            } catch (e: SecurityException) {
                pfd.close()
                return@withContext if (Build.VERSION.SDK_INT >= 35) IntakeResult.NeedsPassword
                else IntakeResult.Failed("This PDF is password protected. Scan the printed card instead.")
            }
            renderer.use { r ->
                val pages = minOf(r.pageCount, MAX_PDF_PAGES)
                val text = StringBuilder()
                val rendered = mutableListOf<Bitmap>()
                for (i in 0 until pages) {
                    r.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        text.append(recognise(bmp)).append('\n')
                        if (password != null) rendered += bmp else bmp.recycle()
                    }
                }
                val fields = IdParser.parse(text.toString())
                if (password != null) {
                    // A password-protected PDF could not be shown again without the password, so
                    // keep the rendered pages instead (the vault is their protection).
                    val image = if (rendered.size == 1) rendered[0] else stack(rendered)
                    val jpeg = ByteArrayOutputStream().use { out ->
                        image.compress(Bitmap.CompressFormat.JPEG, 88, out)
                        out.toByteArray()
                    }
                    IntakeResult.Ready(DocumentDraft(jpeg, "image/jpeg", pages, fields))
                } else {
                    IntakeResult.Ready(DocumentDraft(bytes, "application/pdf", r.pageCount, fields))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PDF intake failed: ${e.message}")
            IntakeResult.Failed("Couldn't read that PDF.")
        } finally {
            tmp.delete()
        }
    }

    /** Seals the draft's file in the vault and records it. Returns the new document's id. */
    suspend fun save(context: Context, draft: DocumentDraft, fields: ExtractedFields, title: String): Long = withContext(Dispatchers.IO) {
        val fileName = "doc-" + UUID.randomUUID().toString() + if (draft.mimeType == "application/pdf") ".pdf.bin" else ".jpg.bin"
        Vault.write(context, fileName, draft.bytes)
        val sealed = Vault.seal(context, fieldsToJson(fields).toString().toByteArray(), "doc-fields:$fileName")
        com.owlcoders.chitti.db.AppDatabase.getDatabase(context).personalDocumentDao().insert(
            PersonalDocument(
                kind = fields.kind.name,
                title = title,
                mimeType = draft.mimeType,
                fileName = fileName,
                pageCount = draft.pageCount,
                fieldsSealed = sealed
            )
        )
    }

    suspend fun delete(context: Context, document: PersonalDocument) = withContext(Dispatchers.IO) {
        Vault.delete(context, document.fileName)
        com.owlcoders.chitti.db.AppDatabase.getDatabase(context).personalDocumentDao().delete(document)
    }

    fun readFields(context: Context, document: PersonalDocument): ExtractedFields {
        val sealed = document.fieldsSealed ?: return ExtractedFields()
        return fieldsFromJson(JSONObject(String(Vault.open(context, sealed, "doc-fields:${document.fileName}"))))
    }

    fun fieldsToJson(f: ExtractedFields): JSONObject = JSONObject()
        .put("kind", f.kind.name).put("name", f.name).put("dateOfBirth", f.dateOfBirth)
        .put("gender", f.gender).put("fatherName", f.fatherName).put("address", f.address)
        .put("aadhaarNumber", f.aadhaarNumber).put("panNumber", f.panNumber)
        .put("rationCardNumber", f.rationCardNumber)

    fun fieldsFromJson(o: JSONObject) = ExtractedFields(
        kind = runCatching { DocumentKind.valueOf(o.optString("kind")) }.getOrDefault(DocumentKind.OTHER),
        name = o.optString("name"), dateOfBirth = o.optString("dateOfBirth"), gender = o.optString("gender"),
        fatherName = o.optString("fatherName"), address = o.optString("address"),
        aadhaarNumber = o.optString("aadhaarNumber"), panNumber = o.optString("panNumber"),
        rationCardNumber = o.optString("rationCardNumber")
    )

    // ---------------------------------------------------------------- helpers

    private suspend fun recognise(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCompleteListener { recognizer.close() }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun stack(pages: List<Bitmap>): Bitmap {
        val width = pages.maxOf { it.width }
        val out = Bitmap.createBitmap(width, pages.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(Color.WHITE)
        var y = 0f
        pages.forEach { canvas.drawBitmap(it, 0f, y, null); y += it.height }
        return out
    }
}
