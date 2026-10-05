package com.owlcoders.chitti.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.Task
import com.owlcoders.chitti.services.ExtractedData
import com.owlcoders.chitti.services.ImportanceScorer
import com.owlcoders.chitti.services.NotificationFilter
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.Inset
import com.owlcoders.chitti.ui.components.InsetRow
import com.owlcoders.chitti.ui.components.LargeTitleScaffold
import com.owlcoders.chitti.ui.components.LargeTitleSubtitle
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.insetSection
import com.owlcoders.chitti.ui.theme.Chitti
import com.owlcoders.chitti.ui.theme.category
import com.owlcoders.chitti.ui.theme.urgency
import kotlinx.coroutines.launch

/**
 * Hard cap on simulator input. The few-shot prompt already uses a few hundred
 * tokens and MediaPipe aborts the process natively (not catchable) when the prompt
 * exceeds max tokens, so unbounded pasted text must never reach extract().
 */
const val AI_LAB_MAX_INPUT_CHARS = 400

data class SimulationResult(
    val rawText: String,
    val passedFilter: Boolean,
    val extracted: ExtractedData?,
    val importanceScore: Int,
    val priority: Int,
    val latencyMs: Long,
    val mode: String
)

/** Sample notifications: the short button label paired with the text it loads. */
private val AI_LAB_SAMPLES = listOf(
    "Submission" to "Record submission tomorrow 10 AM, Lab 2.",
    "Fee (code-mixed)" to "25th lopu fee kattali, marchipovaddu.",
    "Deck deadline" to "Submit the hackathon deck by 10am tomorrow",
    "Standup" to "Team standup meeting today at 4 PM on Google Meet",
    "Casual chat" to "Hey bro, are you free this weekend?"
)

/**
 * The extraction lab (developer only, unlocked from Settings): paste a notification, run it
 * through the filter, the extractor and the scorer, and see exactly what comes out.
 */
@Composable
fun AiLabScreen(
    onAddEvent: (CapturedEvent, Task) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val colors = Chitti.colors
    val app = context.applicationContext as ChittiApp
    val scope = rememberCoroutineScope()

    var testInput by remember { mutableStateOf("Record submission tomorrow 10 AM, Lab 2.") }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SimulationResult?>(null) }
    var smartReply by remember { mutableStateOf<String?>(null) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    val engine = app.extractionEngine
    val simResult = result
    // Read the engine readouts through `result` first so the rows refresh after a run: the
    // engine's own fields are plain vars and never trigger recomposition on their own.
    val backend = simResult?.mode
        ?: engine?.lastInferenceMode
        ?: if (engine?.isLlmLoaded() == true) "Gemma 2B" else "Rule-based regex"
    val latencyMs = simResult?.latencyMs ?: engine?.lastInferenceLatencyMs ?: 0L

    fun priorityTint(p: Int) = when (p) {
        2 -> colors.danger
        1 -> colors.warning
        else -> colors.success
    }

    fun run() {
        isRunning = true
        isSaved = false
        smartReply = null
        // Clamp again at the call site so nothing longer than the cap can reach the LLM prompt.
        val input = testInput.take(AI_LAB_MAX_INPUT_CHARS)
        // extract() is a suspend fun that switches to IO internally; keep it on this scope.
        scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val passesFilter = NotificationFilter.shouldProcess(input)
                val activeEngine = app.extractionEngine
                val extracted = if (passesFilter) activeEngine?.extract(input) else null
                val latency = System.currentTimeMillis() - startTime
                val scoreFloat = if (extracted != null && extracted.what.isNotBlank()) {
                    ImportanceScorer.score(
                        extractedWhat = extracted.what,
                        extractedWhen = extracted.whenTime,
                        urgency = extracted.urgency,
                        category = extracted.category,
                        rawText = input
                    )
                } else 0f
                result = SimulationResult(
                    rawText = input,
                    passedFilter = passesFilter,
                    extracted = extracted,
                    importanceScore = (scoreFloat * 100).toInt(),
                    priority = ImportanceScorer.toPriority(scoreFloat),
                    latencyMs = latency,
                    mode = activeEngine?.lastInferenceMode ?: "Rule-based regex"
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Simulation failed: ${e.message ?: "unknown error"}", Toast.LENGTH_SHORT).show()
            } finally {
                isRunning = false
            }
        }
    }

    LargeTitleScaffold(
        title = "Extraction lab",
        subtitle = { LargeTitleSubtitle("Run a notification through the on-device pipeline.") }
    ) {
        insetSection(key = "engine", header = "Engine") {
            row("backend", Inset.iconInset) { InsetRow(title = "Model", icon = Icons.Rounded.Memory, iconTint = colors.purple, value = backend) }
            row("latency", Inset.iconInset) {
                InsetRow(
                    title = "Last run",
                    icon = Icons.Rounded.Speed,
                    iconTint = colors.warning,
                    value = if (latencyMs > 0) "$latencyMs ms" else "Not run yet"
                )
            }
            row("network", Inset.iconInset) { InsetRow(title = "Network", icon = Icons.Rounded.CloudOff, iconTint = colors.info, value = "Not used") }
        }

        insetSection(key = "input", header = "Notification", footer = "${testInput.length} of $AI_LAB_MAX_INPUT_CHARS characters") {
            row("field") {
                Column(modifier = Modifier.padding(Space.m)) {
                    ChittiTextField(
                        value = testInput,
                        onValueChange = {
                            testInput = it.take(AI_LAB_MAX_INPUT_CHARS)
                            isSaved = false
                            smartReply = null
                        },
                        placeholder = "Paste or type a notification",
                        singleLine = false
                    )
                    Spacer(Modifier.height(Space.s))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s)
                    ) {
                        AI_LAB_SAMPLES.forEach { sample ->
                            SecondaryButton(text = sample.first, onClick = {
                                testInput = sample.second.take(AI_LAB_MAX_INPUT_CHARS)
                                isSaved = false
                                smartReply = null
                            })
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    if (isRunning) {
                        LatticeLoader(status = LatticeStatus.WORKING, label = "Running extraction", color = colors.accent, modifier = Modifier.padding(vertical = Space.m))
                    } else {
                        PrimaryButton(text = "Run", icon = Icons.Rounded.PlayArrow, enabled = testInput.isNotBlank(), onClick = { run() })
                    }
                }
            }
        }

        if (simResult != null) {
            val ext = simResult.extracted
            val hasExtraction = ext != null && ext.what.isNotBlank()
            insetSection(
                key = "result",
                header = "Result",
                footer = if (!hasExtraction) "Filtered as chat noise: no task or commitment in it." else null
            ) {
                row("verdict") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(Space.m),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = if (simResult.passedFilter) "Passed filter" else "Filtered out",
                            tint = if (simResult.passedFilter) colors.success else colors.danger,
                            icon = if (simResult.passedFilter) Icons.Rounded.CheckCircle else Icons.Rounded.Block
                        )
                        if (ext != null && ext.category.isNotBlank()) StatusPill(text = ext.category, tint = colors.category(ext.category))
                        if (ext != null && ext.urgency.isNotBlank()) StatusPill(text = "${ext.urgency} urgency", tint = colors.urgency(ext.urgency))
                    }
                }
                if (ext != null && hasExtraction) {
                    row("task", Inset.iconInset) { InsetRow(title = "Task", subtitle = ext.what, icon = Icons.Rounded.TaskAlt, iconTint = colors.category(ext.category)) }
                    row("time", Inset.iconInset) { InsetRow(title = "When", value = ext.whenTime.ifBlank { "Not given" }, icon = Icons.Rounded.Schedule, iconTint = colors.info) }
                    row("who", Inset.iconInset) { InsetRow(title = "Who", value = ext.who.ifBlank { "Not given" }, icon = Icons.Rounded.Person, iconTint = colors.purple) }
                    row("confidence", Inset.iconInset) { InsetRow(title = "Confidence", value = "${(ext.confidence * 100).toInt()}%", icon = Icons.Rounded.Verified, iconTint = colors.accent) }
                    row("importance", Inset.iconInset) {
                        InsetRow(title = "Importance", value = "${simResult.importanceScore} / 100", icon = Icons.Rounded.Speed, iconTint = priorityTint(simResult.priority))
                    }
                    row("priority", Inset.iconInset) {
                        InsetRow(
                            title = "Priority",
                            icon = Icons.Rounded.Flag,
                            iconTint = priorityTint(simResult.priority),
                            value = when (simResult.priority) { 2 -> "High"; 1 -> "Medium"; else -> "Low" },
                            valueColor = priorityTint(simResult.priority)
                        )
                    }
                    row("actions") {
                        Row(modifier = Modifier.fillMaxWidth().padding(Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            PrimaryButton(
                                text = if (isSaved) "Added" else "Add to Today",
                                icon = Icons.Rounded.Add,
                                enabled = !isSaved,
                                fill = false,
                                onClick = {
                                    onAddEvent(
                                        CapturedEvent(
                                            sourceApp = "com.owlcoders.chitti.sim",
                                            rawText = simResult.rawText,
                                            extractedWhat = ext.what,
                                            extractedWhen = ext.whenTime,
                                            extractedWho = ext.who,
                                            category = ext.category,
                                            urgency = ext.urgency,
                                            status = "extracted",
                                            timestamp = System.currentTimeMillis()
                                        ),
                                        Task(
                                            title = ext.what,
                                            description = "Details: ${simResult.rawText}\nWhen: ${ext.whenTime}\nCategory: ${ext.category}",
                                            status = "pending",
                                            priority = simResult.priority,
                                            sourceType = "simulator",
                                            sourceId = 0
                                        )
                                    )
                                    isSaved = true
                                }
                            )
                            SecondaryButton(text = "Calendar", icon = Icons.Rounded.Event, onClick = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, ext.what)
                                    putExtra(CalendarContract.Events.DESCRIPTION, simResult.rawText)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                }
            }

            if (ext != null && hasExtraction) {
                insetSection(key = "reply", header = "Suggested reply", footer = "Drafted on this phone.") {
                    row("reply") {
                        Column(modifier = Modifier.fillMaxWidth().padding(Space.m)) {
                            val reply = smartReply
                            if (reply != null) {
                                Text(reply, style = MaterialTheme.typography.bodyLarge, color = colors.textHigh)
                                Spacer(Modifier.height(Space.s))
                            }
                            if (isGeneratingReply) {
                                LatticeLoader(status = LatticeStatus.WORKING, label = "Writing reply", color = colors.accent)
                            } else {
                                SecondaryButton(
                                    text = if (reply == null) "Draft a reply" else "Draft another",
                                    onClick = {
                                        isGeneratingReply = true
                                        scope.launch {
                                            try {
                                                smartReply = app.extractionEngine?.generateSmartReply(
                                                    CapturedEvent(
                                                        sourceApp = "simulator",
                                                        rawText = simResult.rawText,
                                                        extractedWhat = ext.what,
                                                        extractedWhen = ext.whenTime,
                                                        extractedWho = ext.who,
                                                        category = ext.category,
                                                        urgency = ext.urgency,
                                                        status = "extracted",
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                ) ?: "Engine not ready yet"
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not generate reply", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isGeneratingReply = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
