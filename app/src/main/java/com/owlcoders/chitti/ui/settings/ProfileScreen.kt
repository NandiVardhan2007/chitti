package com.owlcoders.chitti.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.UserProfile
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch

/**
 * Profile: the details Chitti's autofill puts into forms in other apps. Laid out as an iOS form:
 * each group holds labelled fields, label on the left, value on the right. Save lives in the bar
 * and wakes up only when something has changed.
 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val db = remember { AppDatabase.getDatabase(context) }

    var saved by remember { mutableStateOf(UserProfile()) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val profile = db.userProfileDao().getUserProfileSync() ?: UserProfile()
        saved = profile
        firstName = profile.firstName
        lastName = profile.lastName
        email = profile.email
        phone = profile.phoneNumber
        dob = profile.dateOfBirth
        address = profile.address
    }

    val current = UserProfile(
        id = 1,
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        email = email.trim(),
        phoneNumber = phone.trim(),
        address = address.trim(),
        dateOfBirth = dob.trim()
    )
    val dirty = current != saved.copy(id = 1)

    LargeTitleScaffold(
        title = "Your details",
        subtitle = { LargeTitleSubtitle("Only used to fill forms, and only on this phone.") },
        actions = {
            LinkButton(
                text = "Save",
                enabled = dirty,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = Space.s),
                onClick = {
                    scope.launch {
                        db.userProfileDao().insertOrUpdateProfile(current)
                        saved = current
                        haptics.confirm()
                    }
                }
            )
        }
    ) {
        insetSection(key = "name", header = "Name") {
            row("first") { FieldRow("First", firstName, { firstName = it }, "First name", capitalize = true) }
            row("last") { FieldRow("Last", lastName, { lastName = it }, "Last name", capitalize = true) }
        }
        insetSection(key = "contact", header = "Contact") {
            row("email") { FieldRow("Email", email, { email = it }, "you@example.com", KeyboardType.Email) }
            row("phone") { FieldRow("Phone", phone, { phone = it }, "Number", KeyboardType.Phone) }
        }
        insetSection(key = "more", header = "More") {
            row("dob") { FieldRow("Birthday", dob, { dob = it }, "DD/MM/YYYY", KeyboardType.Number) }
            row("address") { FieldRow("Address", address, { address = it }, "Street, city, postcode", singleLine = false, capitalize = true) }
        }
        insetSection(
            key = "autofill",
            footer = "Set Chitti as Android's autofill service and it fills these into forms in other apps. They never leave this device."
        ) {
            row("autofill", Inset.iconInset) {
                InsetRow(
                    title = "Use Chitti for autofill",
                    icon = Icons.Rounded.Password,
                    chevron = true,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(android.net.Uri.parse("package:${context.packageName}"))
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (e2: Exception) {
                                Toast.makeText(context, "Could not open system settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

/** A labelled field inside a group row: label left, editable value filling the rest. */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    capitalize: Boolean = false
) {
    val colors = Chitti.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Inset.rowMinHeight)
            .padding(horizontal = Inset.textInset, vertical = Space.m),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textHigh, modifier = Modifier.width(96.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textHigh),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textLow)
                inner()
            }
        )
    }
}
