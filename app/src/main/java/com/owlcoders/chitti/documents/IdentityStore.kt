package com.owlcoders.chitti.documents

import android.content.Context
import com.owlcoders.chitti.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Identity details read from the user's documents. Kept only in the encrypted vault. */
data class Identity(
    val fullName: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val fatherName: String = "",
    val address: String = "",
    val aadhaarNumber: String = "",
    val panNumber: String = "",
    val rationCardNumber: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fullName", fullName).put("dateOfBirth", dateOfBirth).put("gender", gender)
        .put("fatherName", fatherName).put("address", address).put("aadhaarNumber", aadhaarNumber)
        .put("panNumber", panNumber).put("rationCardNumber", rationCardNumber)

    /** Fills blanks from a newly read document; never overwrites something the user already has. */
    fun mergedWith(f: ExtractedFields): Identity = copy(
        fullName = fullName.ifBlank { f.name },
        dateOfBirth = dateOfBirth.ifBlank { f.dateOfBirth },
        gender = gender.ifBlank { f.gender },
        fatherName = fatherName.ifBlank { f.fatherName },
        address = address.ifBlank { f.address },
        aadhaarNumber = aadhaarNumber.ifBlank { f.aadhaarNumber },
        panNumber = panNumber.ifBlank { f.panNumber },
        rationCardNumber = rationCardNumber.ifBlank { f.rationCardNumber }
    )

    companion object {
        fun fromJson(o: JSONObject) = Identity(
            fullName = o.optString("fullName"), dateOfBirth = o.optString("dateOfBirth"),
            gender = o.optString("gender"), fatherName = o.optString("fatherName"),
            address = o.optString("address"), aadhaarNumber = o.optString("aadhaarNumber"),
            panNumber = o.optString("panNumber"), rationCardNumber = o.optString("rationCardNumber")
        )
    }
}

/**
 * The single source of the user's identity details, sealed in [Vault]. Screens observe [identity];
 * the autofill service reads it through [load] after the user has unlocked with a fingerprint.
 */
object IdentityStore {
    const val FILE = "identity.bin"

    private val state = MutableStateFlow(Identity())
    private var loaded = false

    val identity: StateFlow<Identity> = state.asStateFlow()

    suspend fun load(context: Context): Identity = withContext(Dispatchers.IO) {
        if (!loaded) {
            state.value = Vault.read(context, FILE)?.let { Identity.fromJson(JSONObject(String(it))) } ?: Identity()
            loaded = true
        }
        state.value
    }

    suspend fun save(context: Context, identity: Identity) = withContext(Dispatchers.IO) {
        Vault.write(context, FILE, identity.toJson().toString().toByteArray())
        state.value = identity
        loaded = true
    }

    /** Forgets the cached copy, e.g. after a restore replaced the vault file. */
    fun invalidate() {
        loaded = false
        state.value = Identity()
    }
}
