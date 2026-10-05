package com.owlcoders.chitti.services

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.UserProfile
import com.owlcoders.chitti.documents.Identity
import com.owlcoders.chitti.documents.IdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a form field asks for, worked out from its autofill hint, or failing that from its id, hint
 * text and HTML attributes (Indian government and bank forms rarely set autofill hints).
 */
object AutofillFields {

    /** ID numbers: filled only after the user unlocks with fingerprint / face / PIN. */
    val SENSITIVE = setOf("aadhaar", "pan", "ration")

    fun classify(raw: String): String? {
        val h = raw.lowercase().replace(Regex("[^a-z]"), "")
        return when {
            h.isEmpty() -> null
            // Credentials and one-time codes are never Chitti's to fill.
            listOf("password", "username", "otp", "securitycode", "verificationcode", "smscode", "captcha", "cvv", "pin").any { it in h } -> null
            "aadhaar" in h || "aadhar" in h || h == "uid" || "uidno" in h -> "aadhaar"
            h == "pan" || "panno" in h || "pannumber" in h || "pancard" in h -> "pan"
            "ration" in h -> "ration"
            "father" in h -> "father"
            "gender" in h || h == "sex" -> "gender"
            "given" in h || "firstname" in h || "fname" in h -> "first"
            "family" in h || "lastname" in h || "surname" in h || "lname" in h -> "last"
            "email" in h -> "email"
            "phone" in h || "mobile" in h || h.startsWith("tel") -> "phone"
            "postal" in h || "zip" in h || "pincode" in h -> null // no separate PIN stored; never stuff the full address in
            "address" in h || "street" in h -> "address"
            "birth" in h || h == "dob" || "dateofbirth" in h -> "dob"
            "name" in h -> "name"
            else -> null
        }
    }

    fun value(key: String, p: UserProfile?, id: Identity): String? {
        fun String?.orNull() = this?.takeIf { it.isNotBlank() }
        return when (key) {
            "aadhaar" -> id.aadhaarNumber.filter { it.isDigit() }.orNull()
            "pan" -> id.panNumber.orNull()
            "ration" -> id.rationCardNumber.orNull()
            "father" -> id.fatherName.orNull()
            "gender" -> id.gender.orNull()
            "first" -> p?.firstName.orNull()
            "last" -> p?.lastName.orNull()
            "name" -> "${p?.firstName.orEmpty()} ${p?.lastName.orEmpty()}".trim().orNull() ?: id.fullName.orNull()
            "email" -> p?.email.orNull()
            "phone" -> p?.phoneNumber.orNull()
            "address" -> p?.address.orNull() ?: id.address.orNull()
            "dob" -> p?.dateOfBirth.orNull() ?: id.dateOfBirth.orNull()
            else -> null
        }
    }
}

/**
 * Chitti as Android's autofill service: fills the user's name, contact details, address, date of
 * birth and, after a fingerprint, their Aadhaar / PAN / ration card numbers.
 *
 * Plain details fill straight away. If the form asks for an ID number, the whole suggestion is
 * locked: choosing it opens [AutofillUnlockActivity], which asks for the fingerprint and only then
 * decrypts the vault and returns the values. The ID numbers are never placed in the suggestion
 * itself, so a form cannot read them without the user unlocking.
 */
class ChittiAutofillService : AutofillService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val fields = linkedMapOf<AutofillId, String>()
        for (i in 0 until structure.windowNodeCount) traverse(structure.getWindowNodeAt(i).rootViewNode, fields)
        if (fields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        scope.launch {
            val response = try {
                buildResponse(fields)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) { callback.onSuccess(response) }
        }
    }

    private suspend fun buildResponse(fields: Map<AutofillId, String>): FillResponse? {
        val needsUnlock = fields.values.any { it in AutofillFields.SENSITIVE }
        val profile = AppDatabase.getDatabase(applicationContext).userProfileDao().getUserProfileSync()

        if (needsUnlock) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "Chitti · unlock to fill your ID details")
            }
            val intent = Intent(this, AutofillUnlockActivity::class.java)
                .putParcelableArrayListExtra(AutofillUnlockActivity.EXTRA_IDS, ArrayList(fields.keys))
                .putStringArrayListExtra(AutofillUnlockActivity.EXTRA_KEYS, ArrayList(fields.values))
            val sender = PendingIntent.getActivity(
                this, fields.hashCode(), intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            ).intentSender
            @Suppress("DEPRECATION") // the Presentations overload needs API 33; minSdk is 26
            val dataset = Dataset.Builder(presentation).apply {
                @Suppress("DEPRECATION")
                fields.keys.forEach { setValue(it, null) }
                setAuthentication(sender)
            }.build()
            return FillResponse.Builder().addDataset(dataset).build()
        }

        // Nothing sensitive asked for: the vault still holds useful non-ID details (father's name,
        // gender), but those only need the unlocked vault if the profile lacks them.
        val identity = runCatching { IdentityStore.load(applicationContext) }.getOrDefault(Identity())
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "Chitti · fill your details")
        }
        @Suppress("DEPRECATION") // the Presentations overload needs API 33; minSdk is 26
        val builder = Dataset.Builder(presentation)
        var any = false
        fields.forEach { (id, key) ->
            val v = AutofillFields.value(key, profile, identity) ?: return@forEach
            @Suppress("DEPRECATION")
            builder.setValue(id, AutofillValue.forText(v))
            any = true
        }
        return if (any) FillResponse.Builder().addDataset(builder.build()).build() else null
    }

    private fun traverse(node: AssistStructure.ViewNode, out: MutableMap<AutofillId, String>) {
        val id = node.autofillId
        if (id != null && isInput(node)) {
            val key = node.autofillHints?.firstNotNullOfOrNull { AutofillFields.classify(it) }
                ?: listOfNotNull(node.idEntry, node.hint, node.htmlInfo?.attributes?.firstOrNull { it.first == "name" }?.second,
                    node.htmlInfo?.attributes?.firstOrNull { it.first == "id" }?.second,
                    node.htmlInfo?.attributes?.firstOrNull { it.first == "autocomplete" }?.second)
                    .firstNotNullOfOrNull { AutofillFields.classify(it) }
            if (key != null) out[id] = key
        }
        for (i in 0 until node.childCount) traverse(node.getChildAt(i), out)
    }

    private fun isInput(node: AssistStructure.ViewNode): Boolean =
        node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT ||
            node.className?.contains("EditText") == true ||
            node.htmlInfo?.tag.equals("input", ignoreCase = true)

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
