package com.owlcoders.chitti.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetGroup
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.LinkButton
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.theme.Chitti
import com.owlcoders.chitti.ui.theme.ChittiTheme
import kotlinx.coroutines.delay

/**
 * LinkGuard: the screen every tapped link passes through when Chitti is the phone's link opener
 * (see [BrowserRouter]), and the target of Chitti's own "suspicious link" alerts.
 *
 *  1. Checking: the link is checked on the phone and, when online, against Google Safe Browsing.
 *  2. Safe: says so, says how it was checked, and hands the link to the user's browser a moment
 *     later (or at once with "Open now").
 *  3. Caution / Danger: explains every reason in plain words. "Go back" is the primary action;
 *     opening anyway stays possible, because the user is always in charge.
 */
class LinkGuardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "com.owlcoders.chitti.EXTRA_SCAN_URL"

        /** Launch LinkGuard for a URL from anywhere inside the app. */
        fun open(context: Context, url: String) {
            context.startActivity(
                Intent(context, LinkGuardActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
            ?: intent.dataString
            ?: run { finish(); return }

        setContent {
            ChittiTheme {
                LinkGuardScreen(
                    url = url,
                    browserLabel = BrowserRouter.preferredBrowser(this)?.label,
                    onOpen = { forward(url) },
                    onGoBack = { finish() }
                )
            }
        }
    }

    // "Open now" can be tapped while the automatic hand-off is pending: open the link once.
    private var forwarded = false

    private fun forward(url: String) {
        if (forwarded || isFinishing) return
        forwarded = true
        if (BrowserRouter.open(this, url) == null) {
            Toast.makeText(this, "No app available to open this link", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

/** How long a safe verdict stays on screen before the browser opens: long enough to read. */
private const val SAFE_HOLD_MS = 1400L

@Composable
private fun LinkGuardScreen(
    url: String,
    browserLabel: String?,
    onOpen: () -> Unit,
    onGoBack: () -> Unit
) {
    val colors = Chitti.colors
    val haptics = rememberHaptics()
    var verdict by remember { mutableStateOf<LinkChecker.Verdict?>(null) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        val v = LinkChecker.check(context, url)
        verdict = v
        when (v.level) {
            LinkScanner.RiskLevel.SAFE -> {
                haptics.confirm()
                delay(SAFE_HOLD_MS)
                onOpen()
            }
            LinkScanner.RiskLevel.CAUTION -> haptics.threshold()
            LinkScanner.RiskLevel.DANGER -> haptics.reject()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = verdict,
            transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(120)) },
            label = "linkVerdict"
        ) { v ->
            if (v == null) Checking(url) else Result(v, browserLabel, onOpen, onGoBack)
        }
    }
}

@Composable
private fun Checking(url: String) {
    val colors = Chitti.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LatticeLoader(status = LatticeStatus.WORKING, label = "Checking this link", color = colors.accent, fontSize = 17)
        Spacer(Modifier.height(Space.l))
        Text(
            hostOf(url),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textHigh,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Result(
    v: LinkChecker.Verdict,
    browserLabel: String?,
    onOpen: () -> Unit,
    onGoBack: () -> Unit
) {
    val colors = Chitti.colors
    val (icon: ImageVector, tint: Color, headline: String) = when (v.level) {
        LinkScanner.RiskLevel.SAFE -> Triple(Icons.Rounded.GppGood, colors.success, "This link looks safe")
        LinkScanner.RiskLevel.CAUTION -> Triple(Icons.Rounded.GppMaybe, colors.warning, "Be careful with this link")
        LinkScanner.RiskLevel.DANGER -> Triple(Icons.Rounded.GppBad, colors.danger, "This link is not safe")
    }
    val safe = v.level == LinkScanner.RiskLevel.SAFE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Space.xxxl, bottom = Space.xl)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(Space.l))
            Text(
                headline,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textHigh,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                v.host.ifBlank { v.url },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMid,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // What was checked, and what each check found.
        InsetGroup(
            header = if (safe) "How Chitti checked it" else "Why Chitti stopped it",
            footer = checkedFooter(v)
        ) {
            if (safe) {
                row("local", Inset.iconInset) {
                    InsetRow(
                        title = "No warning signs",
                        subtitle = "Checked on this phone: the address, the domain and known scam patterns",
                        icon = Icons.Rounded.CheckCircle,
                        iconTint = colors.success
                    )
                }
                row("google", Inset.iconInset) { GoogleRow(v.google) }
            } else {
                v.reasons.forEachIndexed { i, reason ->
                    // "Claim: what it means" reads as a bold line and an explanation under it.
                    val claim = reason.substringBefore(": ", reason).trimEnd('.')
                    val detail = reason.substringAfter(": ", "").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                    row("reason-$i", Inset.iconInset) {
                        InsetRow(
                            title = claim,
                            subtitle = detail,
                            titleLines = Int.MAX_VALUE,
                            subtitleLines = Int.MAX_VALUE,
                            icon = if (i == 0 && v.level == LinkScanner.RiskLevel.DANGER) Icons.Rounded.GppBad else Icons.Rounded.Warning,
                            iconTint = if (v.level == LinkScanner.RiskLevel.DANGER) colors.danger else colors.warning
                        )
                    }
                }
                if (v.google != LinkChecker.GoogleStatus.Listed) {
                    row("google", Inset.iconInset) { GoogleRow(v.google, stopped = true) }
                }
            }
        }

        InsetGroup(header = "The full link") {
            row("url") {
                Text(
                    v.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid,
                    modifier = Modifier.padding(horizontal = Inset.textInset, vertical = Space.m)
                )
            }
        }

        Spacer(Modifier.height(Space.xxl))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (safe) {
                PrimaryButton(text = "Open now" + (browserLabel?.let { " in $it" } ?: ""), onClick = onOpen)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Opening automatically…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid
                )
                LinkButton(text = "Cancel", onClick = onGoBack)
            } else {
                PrimaryButton(text = "Go back", onClick = onGoBack)
                Spacer(Modifier.height(Space.s))
                if (v.level == LinkScanner.RiskLevel.DANGER) {
                    LinkButton(text = "I understand the risk, open anyway", color = colors.danger, onClick = onOpen)
                } else {
                    SecondaryButton(text = "Open anyway", onClick = onOpen)
                }
            }
        }
    }
}

@Composable
private fun GoogleRow(status: LinkChecker.GoogleStatus, stopped: Boolean = false) {
    val colors = Chitti.colors
    when (status) {
        // On a stopped link a green tick would read as a contradiction: say what it means instead.
        LinkChecker.GoogleStatus.NotListed if stopped -> InsetRow(
            title = "Not on Google's list yet",
            subtitle = "New scam sites are often not listed yet. Chitti caught this one on the phone.",
            subtitleLines = Int.MAX_VALUE,
            icon = Icons.Rounded.Info,
            iconTint = colors.textMid
        )
        LinkChecker.GoogleStatus.NotListed -> InsetRow(
            title = "Not on Google's unsafe list",
            subtitle = "Google Safe Browsing does not list this page as phishing or malware",
            icon = Icons.Rounded.CheckCircle,
            iconTint = colors.success
        )
        LinkChecker.GoogleStatus.Listed -> InsetRow(
            title = "On Google's unsafe list",
            icon = Icons.Rounded.GppBad,
            iconTint = colors.danger
        )
        LinkChecker.GoogleStatus.Offline -> InsetRow(
            title = "Google check unavailable",
            subtitle = "No connection, so only the on-phone check ran",
            icon = Icons.Rounded.Info,
            iconTint = colors.textMid
        )
        LinkChecker.GoogleStatus.NotConfigured -> InsetRow(
            title = "Google check not set up",
            subtitle = "Only the on-phone check ran",
            icon = Icons.Rounded.Info,
            iconTint = colors.textMid
        )
        LinkChecker.GoogleStatus.NotApplicable -> InsetRow(
            title = "Checked on this phone",
            subtitle = "Google only checks web links",
            icon = Icons.Rounded.Info,
            iconTint = colors.textMid
        )
    }
}

private fun checkedFooter(v: LinkChecker.Verdict): String = when (v.google) {
    LinkChecker.GoogleStatus.Listed, LinkChecker.GoogleStatus.NotListed ->
        "Checked on this phone and with Google Safe Browsing. Only the link was sent to Google, nothing else."
    else -> "Checked on this phone. Nothing was sent anywhere."
}

private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host }.getOrNull()?.removePrefix("www.") ?: url
