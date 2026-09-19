package com.owlcoders.chitti.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.PersonalDocument
import com.owlcoders.chitti.db.entities.UserProfile
import com.owlcoders.chitti.documents.DocumentDraft
import com.owlcoders.chitti.documents.DocumentIntake
import com.owlcoders.chitti.documents.DocumentKind
import com.owlcoders.chitti.documents.ExtractedFields
import com.owlcoders.chitti.documents.IdParser
import com.owlcoders.chitti.documents.Identity
import com.owlcoders.chitti.documents.IdentityStore
import com.owlcoders.chitti.documents.IntakeResult
import com.owlcoders.chitti.security.AppLock
import com.owlcoders.chitti.security.SecureScreen
import com.owlcoders.chitti.security.findActivity
import com.owlcoders.chitti.ui.components.BarIconButton
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetGroup
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.components.skeletonSection
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.screens.ChoiceCapsule
import com.owlcoders.chitti.ui.theme.Chitti
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun DocumentKind.icon(): ImageVector = when (this) {
    DocumentKind.AADHAAR -> Icons.Rounded.Fingerprint
    DocumentKind.PAN -> Icons.Rounded.CreditCard
    DocumentKind.RATION -> Icons.AutoMirrored.Rounded.ReceiptLong
    DocumentKind.BIRTH -> Icons.Rounded.Badge
    DocumentKind.OTHER -> Icons.Rounded.Description
}

/**
 * Personal details: what Chitti's autofill puts into forms, and the ID documents it came from.
 *
 * Security, WhatsApp-style:
 *  - the screen opens only after the phone's fingerprint / face / PIN, and relocks after a minute;
 *  - it is hidden from screenshots, screen recordings and the recent-apps preview;
 *  - Aadhaar and PAN are shown masked until tapped;
 *  - documents and ID numbers live only in the encrypted vault.
 *
 * "+" adds a document: scan it with the camera (Google's on-device scanner finds the edges,
 * straightens and cleans the page), or pick a photo or a PDF. Chitti reads it on the phone and
 * shows what it found for the user to check before anything is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenDocument: (Long) -> Unit = {}) {
    SecureScreen()
    val context = LocalContext.current
    val colors = Chitti.colors
    val activity = context.findActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()

    var unlocked by remember { mutableStateOf(AppLock.sensitiveUnlocked()) }
    var noScreenLock by remember { mutableStateOf(false) }
    suspend fun unlock() {
        if (activity == null) return
        when (AppLock.authenticate(activity, "Unlock personal details", "Your ID documents are protected")) {
            AppLock.Outcome.Unlocked -> unlocked = true
            AppLock.Outcome.NoScreenLock -> {
                noScreenLock = true
                unlocked = true
            }
            AppLock.Outcome.Cancelled -> Unit
        }
    }
    LaunchedEffect(Unit) { if (!unlocked) unlock() }

    if (!unlocked) {
        LockedPlaceholder(onUnlock = { scope.launch { unlock() } })
        return
    }
    PersonalDetailsContent(noScreenLock = noScreenLock, onOpenDocument = onOpenDocument)
}

@Composable
private fun LockedPlaceholder(onUnlock: () -> Unit) {
    val colors = Chitti.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.textMid, modifier = Modifier.heightIn(min = 48.dp))
        Spacer(Modifier.height(Space.m))
        Text("Personal details are locked", style = MaterialTheme.typography.headlineSmall, color = colors.textHigh, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text("Your ID documents stay encrypted until you unlock.", style = MaterialTheme.typography.bodyMedium, color = colors.textMid, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Unlock", onClick = onUnlock, fill = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDetailsContent(noScreenLock: Boolean, onOpenDocument: (Long) -> Unit) {
    val context = LocalContext.current
    val colors = Chitti.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val db = remember { AppDatabase.getDatabase(context) }

    // ---- profile fields (plain details used by autofill)
    var saved by remember { mutableStateOf(UserProfile()) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val p = db.userProfileDao().getUserProfileSync() ?: UserProfile()
        saved = p
        firstName = p.firstName; lastName = p.lastName; email = p.email
        phone = p.phoneNumber; dob = p.dateOfBirth; address = p.address
        IdentityStore.load(context)
    }
    val current = UserProfile(1, firstName.trim(), lastName.trim(), email.trim(), phone.trim(), address.trim(), dob.trim())
    val dirty = current != saved.copy(id = 1)

    // ---- identity + documents
    val identity by IdentityStore.identity.collectAsState()
    val documentsOrNull by db.personalDocumentDao().observeAll().collectAsState(initial = null)
    val documents = documentsOrNull.orEmpty()
    var revealAadhaar by remember { mutableStateOf(false) }
    var revealPan by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf(false) }

    // ---- adding a document
    var addSheet by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<DocumentDraft?>(null) }
    var pdfNeedingPassword by remember { mutableStateOf<Uri?>(null) }

    fun handle(result: IntakeResult, pdfUri: Uri? = null) {
        working = null
        when (result) {
            is IntakeResult.Ready -> {
                haptics.confirm()
                draft = result.draft
            }
            IntakeResult.NeedsPassword -> pdfNeedingPassword = pdfUri
            is IntakeResult.Failed -> {
                haptics.reject()
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        AppLock.endExternalTask()
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.map { it.imageUri }.orEmpty()
        if (pages.isEmpty()) return@rememberLauncherForActivityResult
        working = "Reading the document"
        scope.launch { handle(DocumentIntake.fromImages(context, pages)) }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        AppLock.endExternalTask()
        if (uri == null) return@rememberLauncherForActivityResult
        working = "Reading the photo"
        scope.launch { handle(DocumentIntake.fromImages(context, listOf(uri))) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        AppLock.endExternalTask()
        if (uri == null) return@rememberLauncherForActivityResult
        working = "Reading the PDF"
        scope.launch { handle(DocumentIntake.fromPdf(context, uri), uri) }
    }

    fun startScan() {
        val activity = context.findActivity() ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(false)
            .setPageLimit(2)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener {
                AppLock.beginExternalTask()
                scanLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "The scanner isn't available on this phone. Choose a photo instead.", Toast.LENGTH_LONG).show()
            }
    }

    LargeTitleScaffold(
        title = "Personal details",
        subtitle = { LargeTitleSubtitle("Encrypted on this phone. Used to fill forms for you.") },
        actions = {
            BarIconButton(icon = Icons.Rounded.Add, contentDescription = "Add a document", onClick = { addSheet = true })
            if (dirty) {
                LinkButton(
                    text = "Save",
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
        }
    ) {
        if (noScreenLock) {
            insetSection(key = "warning") {
                row("warn", Inset.iconInset) {
                    InsetRow(
                        title = "Set a screen lock to protect these",
                        subtitle = "Without a PIN, pattern or fingerprint on your phone, anyone holding it can open this screen.",
                        subtitleLines = 3,
                        icon = Icons.Rounded.Warning,
                        iconTint = colors.warning,
                        chevron = true,
                        onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    )
                }
            }
        }

        insetSection(
            key = "ids",
            header = "ID numbers",
            footer = "Tap a number to show it. Chitti asks for your fingerprint before filling these into another app."
        ) {
            row("aadhaar", Inset.iconInset) {
                InsetRow(
                    title = "Aadhaar",
                    icon = Icons.Rounded.Fingerprint,
                    iconTint = colors.accent,
                    value = identity.aadhaarNumber.ifBlank { "Not added" }.let { if (revealAadhaar) it else IdParser.maskAadhaar(it) },
                    onClick = if (identity.aadhaarNumber.isNotBlank()) ({ revealAadhaar = !revealAadhaar }) else null
                )
            }
            row("pan", Inset.iconInset) {
                InsetRow(
                    title = "PAN",
                    icon = Icons.Rounded.CreditCard,
                    iconTint = colors.info,
                    value = identity.panNumber.ifBlank { "Not added" }.let { if (revealPan) it else IdParser.maskPan(it) },
                    onClick = if (identity.panNumber.isNotBlank()) ({ revealPan = !revealPan }) else null
                )
            }
            row("ration", Inset.iconInset) {
                InsetRow(
                    title = "Ration card",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    iconTint = colors.success,
                    value = identity.rationCardNumber.ifBlank { "Not added" }
                )
            }
            row("edit") {
                InsetRow(title = "Edit ID details", chevron = true, onClick = { editingIdentity = true })
            }
        }

        if (documentsOrNull == null) skeletonSection("docs-loading", rows = 2)
        else insetSection(key = "docs", header = "Documents", footer = "Stored encrypted. Only viewable after unlocking.") {
            if (documents.isEmpty()) {
                row("none") {
                    InsetRow(
                        title = "Add your Aadhaar, PAN or ration card",
                        subtitle = "Scan it with the camera, or choose a photo or PDF",
                        icon = Icons.Rounded.DocumentScanner,
                        iconTint = colors.accent,
                        onClick = { addSheet = true }
                    )
                }
            } else {
                rows(documents, key = { it.id }, separatorInset = Inset.iconInset) { doc -> DocumentRow(doc, onClick = { onOpenDocument(doc.id) }) }
            }
        }

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
            footer = "Set Chitti as Android's autofill service and it fills these into forms in other apps. They never leave this device unencrypted."
        ) {
            row("autofill", Inset.iconInset) {
                InsetRow(
                    title = "Use Chitti for autofill",
                    icon = Icons.Rounded.Password,
                    chevron = true,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                    }
                )
            }
        }
    }

    // ---- sheets and dialogs
    if (addSheet) {
        ModalBottomSheet(onDismissRequest = { addSheet = false }, containerColor = colors.background) {
            InsetGroup(header = "Add a document", modifier = Modifier.navigationBarsPadding().padding(bottom = Space.l)) {
                row("scan", Inset.iconInset) {
                    InsetRow(
                        title = "Scan with camera",
                        subtitle = "Finds the edges and straightens the card",
                        icon = Icons.Rounded.DocumentScanner,
                        iconTint = colors.accent,
                        onClick = { addSheet = false; startScan() }
                    )
                }
                row("photo", Inset.iconInset) {
                    InsetRow(title = "Choose a photo", icon = Icons.Rounded.PhotoLibrary, iconTint = colors.info, onClick = {
                        addSheet = false
                        AppLock.beginExternalTask()
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
                row("pdf", Inset.iconInset) {
                    InsetRow(title = "Choose a PDF", subtitle = "e-Aadhaar works too", icon = Icons.Rounded.PictureAsPdf, iconTint = colors.danger, onClick = {
                        addSheet = false
                        AppLock.beginExternalTask()
                        pdfLauncher.launch(arrayOf("application/pdf"))
                    })
                }
            }
        }
    }

    working?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.surface,
            confirmButton = {},
            text = { LatticeLoader(status = LatticeStatus.WORKING, label = label, color = colors.accent, fontSize = 16) }
        )
    }

    pdfNeedingPassword?.let { uri ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pdfNeedingPassword = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = colors.surface,
            title = { Text("This PDF has a password", style = MaterialTheme.typography.titleLarge, color = colors.textHigh) },
            text = {
                Column {
                    Text(
                        "For e-Aadhaar it is the first 4 letters of your name in capitals, then your birth year, e.g. RAVI1998.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMid
                    )
                    Spacer(Modifier.height(Space.m))
                    ChittiTextField(value = password, onValueChange = { password = it }, placeholder = "Password", keyboardType = KeyboardType.Password)
                }
            },
            confirmButton = {
                LinkButton(text = "Open", enabled = password.isNotBlank(), style = MaterialTheme.typography.titleLarge, onClick = {
                    pdfNeedingPassword = null
                    working = "Reading the PDF"
                    scope.launch {
                        val r = DocumentIntake.fromPdf(context, uri, password)
                        handle(if (r == IntakeResult.NeedsPassword) IntakeResult.Failed("That password didn't open the PDF.") else r, uri)
                    }
                })
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = { pdfNeedingPassword = null }) }
        )
    }

    draft?.let { d ->
        ReviewSheet(
            draft = d,
            onDismiss = { draft = null },
            onSave = { fields, title, useForAutofill ->
                scope.launch {
                    DocumentIntake.save(context, d, fields, title)
                    if (useForAutofill) applyToProfile(context, fields)
                    haptics.confirm()
                    draft = null
                    Toast.makeText(context, "Saved and encrypted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (editingIdentity) {
        IdentityEditor(
            initial = identity,
            onDismiss = { editingIdentity = false },
            onSave = { updated ->
                scope.launch {
                    IdentityStore.save(context, updated)
                    haptics.confirm()
                    editingIdentity = false
                }
            }
        )
    }
}

/** Saves what the document says into the identity vault, and fills blank profile fields. */
private suspend fun applyToProfile(context: android.content.Context, f: ExtractedFields) {
    val identity = IdentityStore.load(context)
    IdentityStore.save(context, identity.mergedWith(f))
    val dao = AppDatabase.getDatabase(context).userProfileDao()
    val p = dao.getUserProfileSync() ?: UserProfile()
    val parts = f.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    dao.insertOrUpdateProfile(
        p.copy(
            id = 1,
            firstName = p.firstName.ifBlank { parts.dropLast(1).joinToString(" ").ifBlank { parts.firstOrNull().orEmpty() } },
            lastName = p.lastName.ifBlank { if (parts.size > 1) parts.last() else "" },
            dateOfBirth = p.dateOfBirth.ifBlank { f.dateOfBirth },
            address = p.address.ifBlank { f.address }
        )
    )
}

@Composable
private fun DocumentRow(doc: PersonalDocument, onClick: () -> Unit) {
    val colors = Chitti.colors
    val kind = runCatching { DocumentKind.valueOf(doc.kind) }.getOrDefault(DocumentKind.OTHER)
    val date = remember(doc.createdAt) { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(doc.createdAt)) }
    val pages = if (doc.pageCount > 1) " · ${doc.pageCount} pages" else ""
    InsetRow(
        title = doc.title,
        subtitle = "${if (doc.mimeType == "application/pdf") "PDF" else "Scan"}$pages · $date",
        icon = kind.icon(),
        iconTint = colors.accent,
        chevron = true,
        onClick = onClick
    )
}

/** Shows what was read, for the user to correct before anything is saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(
    draft: DocumentDraft,
    onDismiss: () -> Unit,
    onSave: (ExtractedFields, String, Boolean) -> Unit
) {
    val colors = Chitti.colors
    val f = draft.fields
    var kind by remember { mutableStateOf(f.kind) }
    var title by remember { mutableStateOf(f.kind.label) }
    var name by remember { mutableStateOf(f.name) }
    var dob by remember { mutableStateOf(f.dateOfBirth) }
    var gender by remember { mutableStateOf(f.gender) }
    var father by remember { mutableStateOf(f.fatherName) }
    var address by remember { mutableStateOf(f.address) }
    var aadhaar by remember { mutableStateOf(f.aadhaarNumber) }
    var pan by remember { mutableStateOf(f.panNumber) }
    var ration by remember { mutableStateOf(f.rationCardNumber) }
    var useForAutofill by remember { mutableStateOf(true) }
    val aadhaarOk = aadhaar.isBlank() || IdParser.isValidAadhaar(aadhaar)
    val panOk = pan.isBlank() || pan.trim().uppercase().matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Space.xl)
        ) {
            Text(
                if (f.isEmpty) "Check the details" else "Chitti read this",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textHigh,
                modifier = Modifier.padding(horizontal = Space.gutter)
            )
            Text(
                if (f.isEmpty) "Nothing could be read automatically. Type the details, or save the document as it is."
                else "Read on this phone. Correct anything that's wrong before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)
            )
            Row(
                modifier = Modifier.padding(horizontal = Space.gutter).padding(top = Space.s),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs)
            ) {
                DocumentKind.entries.forEach { k ->
                    ChoiceCapsule(k.label.substringBefore(" "), selected = k == kind) {
                        kind = k
                        if (title == DocumentKind.entries.firstOrNull { it.label == title }?.label || title.isBlank()) title = k.label
                    }
                }
            }
            Column(Modifier.padding(horizontal = Space.gutter), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)) {
                Spacer(Modifier.height(Space.s))
                ChittiTextField(title, { title = it }, "Title", label = "Title")
                ChittiTextField(name, { name = it }, "Full name", label = "Name")
                ChittiTextField(dob, { dob = it }, "DD/MM/YYYY", label = "Date of birth", keyboardType = KeyboardType.Number)
                if (kind != DocumentKind.PAN && kind != DocumentKind.RATION || gender.isNotBlank()) ChittiTextField(gender, { gender = it }, "Gender", label = "Gender")
                ChittiTextField(father, { father = it }, "Father's name", label = "Father's name")
                if (kind == DocumentKind.AADHAAR || kind == DocumentKind.RATION || address.isNotBlank()) {
                    ChittiTextField(address, { address = it }, "Address", label = "Address", singleLine = false)
                }
                if (kind == DocumentKind.AADHAAR || aadhaar.isNotBlank()) {
                    ChittiTextField(aadhaar, { aadhaar = it }, "0000 0000 0000", label = if (aadhaarOk) "Aadhaar number" else "Aadhaar number: this doesn't look right", keyboardType = KeyboardType.Number)
                }
                if (kind == DocumentKind.PAN || pan.isNotBlank()) {
                    ChittiTextField(pan, { pan = it.uppercase() }, "ABCDE1234F", label = if (panOk) "PAN" else "PAN: should be 5 letters, 4 digits, 1 letter")
                }
                if (kind == DocumentKind.RATION || ration.isNotBlank()) {
                    ChittiTextField(ration, { ration = it.uppercase() }, "Card number", label = "Ration card number")
                }
            }
            InsetGroup(footer = "Adds anything missing to your details, so autofill can use it. Nothing you already have is overwritten.") {
                row("use") {
                    InsetRow(
                        title = "Use for autofill",
                        onClick = { useForAutofill = !useForAutofill },
                        trailing = { androidx.compose.material3.Switch(checked = useForAutofill, onCheckedChange = { useForAutofill = it }) }
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = "Save securely",
                enabled = aadhaarOk && panOk && title.isNotBlank(),
                modifier = Modifier.padding(horizontal = Space.gutter),
                onClick = {
                    onSave(
                        ExtractedFields(
                            kind = kind,
                            name = name.trim(),
                            dateOfBirth = dob.trim(),
                            gender = gender.trim(),
                            fatherName = father.trim(),
                            address = address.trim(),
                            aadhaarNumber = if (aadhaar.isBlank()) "" else IdParser.formatAadhaar(aadhaar),
                            panNumber = pan.trim().uppercase(),
                            rationCardNumber = ration.trim()
                        ),
                        title.trim(),
                        useForAutofill
                    )
                }
            )
        }
    }
}

/** Edit the identity details directly (for fixing a misread, or typing them in). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityEditor(initial: Identity, onDismiss: () -> Unit, onSave: (Identity) -> Unit) {
    val colors = Chitti.colors
    var i by remember { mutableStateOf(initial) }
    val aadhaarOk = i.aadhaarNumber.isBlank() || IdParser.isValidAadhaar(i.aadhaarNumber)
    val panOk = i.panNumber.isBlank() || i.panNumber.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Space.gutter).padding(bottom = Space.xl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)
        ) {
            Text("ID details", style = MaterialTheme.typography.headlineMedium, color = colors.textHigh)
            ChittiTextField(i.fullName, { i = i.copy(fullName = it) }, "As printed on your ID", label = "Name on ID")
            ChittiTextField(i.dateOfBirth, { i = i.copy(dateOfBirth = it) }, "DD/MM/YYYY", label = "Date of birth", keyboardType = KeyboardType.Number)
            ChittiTextField(i.gender, { i = i.copy(gender = it) }, "Gender", label = "Gender")
            ChittiTextField(i.fatherName, { i = i.copy(fatherName = it) }, "Father's name", label = "Father's name")
            ChittiTextField(i.address, { i = i.copy(address = it) }, "Address", label = "Address", singleLine = false)
            ChittiTextField(i.aadhaarNumber, { i = i.copy(aadhaarNumber = it) }, "0000 0000 0000", label = if (aadhaarOk) "Aadhaar number" else "Aadhaar number: this doesn't look right", keyboardType = KeyboardType.Number)
            ChittiTextField(i.panNumber, { i = i.copy(panNumber = it.uppercase()) }, "ABCDE1234F", label = if (panOk) "PAN" else "PAN: 5 letters, 4 digits, 1 letter")
            ChittiTextField(i.rationCardNumber, { i = i.copy(rationCardNumber = it.uppercase()) }, "Card number", label = "Ration card number")
            Spacer(Modifier.height(Space.s))
            PrimaryButton(text = "Save", enabled = aadhaarOk && panOk, onClick = {
                onSave(i.copy(aadhaarNumber = if (i.aadhaarNumber.isBlank()) "" else IdParser.formatAadhaar(i.aadhaarNumber)))
            })
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
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textLow)
                    inner()
                }
            }
        )
    }
}
