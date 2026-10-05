package com.owlcoders.chitti.services

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.documents.IdentityStore
import com.owlcoders.chitti.security.AppLock
import kotlinx.coroutines.launch

/**
 * The fingerprint step in front of ID autofill. Asks for the phone's biometric / screen lock; only
 * on success does it decrypt the vault and hand the values back to Android's autofill framework.
 * Cancelling returns nothing, and the form stays empty.
 */
class AutofillUnlockActivity : FragmentActivity() {

    companion object {
        const val EXTRA_IDS = "com.owlcoders.chitti.autofill.IDS"
        const val EXTRA_KEYS = "com.owlcoders.chitti.autofill.KEYS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ids: List<AutofillId> = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(EXTRA_IDS, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_IDS)
        }.orEmpty()
        val keys = intent.getStringArrayListExtra(EXTRA_KEYS).orEmpty()
        if (ids.isEmpty() || ids.size != keys.size) {
            finishWith(null)
            return
        }

        lifecycleScope.launch {
            val outcome = AppLock.authenticate(this@AutofillUnlockActivity, "Fill your ID details", "Chitti will fill Aadhaar, PAN or ration card numbers")
            if (outcome != AppLock.Outcome.Unlocked) {
                finishWith(null)
                return@launch
            }
            val profile = AppDatabase.getDatabase(applicationContext).userProfileDao().getUserProfileSync()
            val identity = IdentityStore.load(applicationContext)
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "Chitti")
            }
            @Suppress("DEPRECATION") // the Presentations overload needs API 33; minSdk is 26
            val builder = Dataset.Builder(presentation)
            var any = false
            ids.zip(keys).forEach { (id, key) ->
                val v = AutofillFields.value(key, profile, identity) ?: return@forEach
                @Suppress("DEPRECATION")
                builder.setValue(id, AutofillValue.forText(v))
                any = true
            }
            finishWith(if (any) builder.build() else null)
        }
    }

    private fun finishWith(dataset: Dataset?) {
        if (dataset != null) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}
