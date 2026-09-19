package com.owlcoders.chitti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.owlcoders.chitti.automation.ActionExecutor
import com.owlcoders.chitti.automation.AppLauncher
import com.owlcoders.chitti.automation.AssistantIntentDispatcher
import com.owlcoders.chitti.automation.AssistantResponse
import com.owlcoders.chitti.automation.ReminderScheduler
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.services.SpeechToTextManager
import com.owlcoders.chitti.services.TtsEngine
import com.owlcoders.chitti.ui.ChittiTabBar
import com.owlcoders.chitti.ui.Tab
import com.owlcoders.chitti.ui.TabBarHeight
import com.owlcoders.chitti.ui.TabBarBottomGap
import com.owlcoders.chitti.ui.components.GlassEnvironment
import com.owlcoders.chitti.ui.components.LocalBottomChrome
import com.owlcoders.chitti.ui.components.LocalGlass
import com.owlcoders.chitti.ui.components.Motion
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.VoiceAssistantState
import com.owlcoders.chitti.ui.components.VoiceOverlay
import com.owlcoders.chitti.ui.components.rememberReducedMotion
import com.owlcoders.chitti.ui.screens.AiLabScreen
import com.owlcoders.chitti.ui.screens.AskScreen
import com.owlcoders.chitti.ui.screens.ClearActions
import com.owlcoders.chitti.ui.screens.FoundScreen
import com.owlcoders.chitti.ui.screens.HistoryScreen
import com.owlcoders.chitti.ui.screens.KnowsScreen
import com.owlcoders.chitti.ui.screens.LibraryActions
import com.owlcoders.chitti.ui.screens.LibraryScreen
import com.owlcoders.chitti.ui.screens.PermissionsOnboardingScreen
import com.owlcoders.chitti.ui.screens.SettingsScreen
import com.owlcoders.chitti.ui.screens.StoredCounts
import com.owlcoders.chitti.ui.screens.TodayScreen
import com.owlcoders.chitti.ui.screens.checkAccessibilityPermission
import com.owlcoders.chitti.ui.screens.checkMicPermission
import com.owlcoders.chitti.ui.screens.checkNotificationPermission
import com.owlcoders.chitti.ui.settings.ProfileScreen
import com.owlcoders.chitti.ui.theme.Chitti
import com.owlcoders.chitti.ui.theme.ChittiTheme
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Every destination. Three are tabs; the rest are pushed on top of one. */
object Routes {
    const val TODAY = "today"
    const val ASK = "ask"
    const val LIBRARY = "library"
    const val HISTORY = "today/history"
    const val SETTINGS = "today/settings"
    const val PROFILE = "today/settings/profile"
    const val LAB = "today/settings/lab"
    const val FOUND = "library/found"
    const val KNOWS = "library/knows"

    val roots = setOf(TODAY, ASK, LIBRARY)
    fun tabOf(route: String?): Tab = when {
        route == null -> Tab.Today
        route == ASK || route.startsWith("$ASK/") -> Tab.Ask
        route == LIBRARY || route.startsWith("$LIBRARY/") -> Tab.Library
        else -> Tab.Today
    }
}

class MainActivity : ComponentActivity() {
    private var ttsEngine: TtsEngine? = null
    private var sttManager: SpeechToTextManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Content draws behind the status and navigation bars; the glass bars sit over it.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestBatteryOptimizationExemption()

        val tts = TtsEngine(this)
        ttsEngine = tts

        setContent {
            ChittiTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val app = application as ChittiApp

                val appLauncher = remember { AppLauncher(context) }
                val actionExecutor = remember { ActionExecutor(context, app.database) }
                val dispatcher = remember { AssistantIntentDispatcher(context, appLauncher, tts, actionExecutor) }

                // ----- Voice state
                val latestEvents = remember { mutableStateOf<List<com.owlcoders.chitti.db.CapturedEvent>>(emptyList()) }
                val latestMemories = remember { mutableStateOf<List<Memory>>(emptyList()) }
                // Bumped on every new voice session and on dismiss; a query that finishes for an
                // older generation is dropped. The job itself is cancelled too.
                var queryGen by remember { mutableIntStateOf(0) }
                var queryJob by remember { mutableStateOf<Job?>(null) }
                var voiceState by remember { mutableStateOf(VoiceAssistantState.IDLE) }
                var transcript by remember { mutableStateOf("") }
                var rmsLevel by remember { mutableFloatStateOf(0f) }
                var voiceResponse by remember { mutableStateOf<AssistantResponse?>(null) }

                val stt = remember {
                    SpeechToTextManager(
                        context = context,
                        onPartialResult = { partial -> transcript = partial },
                        onFinalResult = { finalQuery ->
                            transcript = finalQuery
                            voiceState = VoiceAssistantState.THINKING
                            val gen = ++queryGen
                            queryJob?.cancel()
                            queryJob = scope.launch {
                                val response = try {
                                    dispatcher.processQuery(finalQuery, latestEvents.value, latestMemories.value, shouldSpeak = true)
                                } catch (t: kotlinx.coroutines.CancellationException) {
                                    throw t
                                } catch (t: Throwable) {
                                    Log.e("ChittiMain", "processQuery failed: ${t.message}", t)
                                    AssistantResponse(
                                        message = "Something went wrong while doing that. Please try again.",
                                        actionSuccess = false,
                                        actionLabel = "Error"
                                    )
                                }
                                if (gen != queryGen) {
                                    tts.stop()
                                    return@launch
                                }
                                voiceResponse = response
                                voiceState = if (tts.isSpeaking) VoiceAssistantState.SPEAKING else VoiceAssistantState.RESULT
                            }
                        },
                        onRmsLevel = { rms -> rmsLevel = rms },
                        onErrorMessage = { errMsg ->
                            Log.w("ChittiMain", "STT Error: $errMsg")
                            // Errors can arrive after end-of-speech (e.g. NO_MATCH), when we are
                            // already THINKING; a real result never follows those.
                            if (voiceState == VoiceAssistantState.LISTENING || voiceState == VoiceAssistantState.THINKING) {
                                voiceState = VoiceAssistantState.RESULT
                                voiceResponse = AssistantResponse(message = errMsg, spokenText = errMsg, actionSuccess = false)
                            }
                        },
                        onStateChange = { state ->
                            when (state) {
                                SpeechToTextManager.SpeechState.READY,
                                SpeechToTextManager.SpeechState.LISTENING -> voiceState = VoiceAssistantState.LISTENING
                                SpeechToTextManager.SpeechState.PROCESSING -> {
                                    if (voiceState == VoiceAssistantState.LISTENING) voiceState = VoiceAssistantState.THINKING
                                }
                                else -> {}
                            }
                        }
                    )
                }
                SideEffect { sttManager = stt }
                DisposableEffect(tts) {
                    tts.onSpeechFinished = {
                        if (voiceState == VoiceAssistantState.SPEAKING) voiceState = VoiceAssistantState.RESULT
                    }
                    onDispose { tts.onSpeechFinished = null }
                }

                fun beginListening() {
                    transcript = ""
                    voiceResponse = null
                    rmsLevel = 0f
                    voiceState = VoiceAssistantState.LISTENING
                    stt.startListening()
                }

                val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) {
                        beginListening()
                    } else {
                        voiceState = VoiceAssistantState.RESULT
                        voiceResponse = AssistantResponse(
                            message = "Chitti needs the microphone to hear you.",
                            spokenText = "Chitti needs the microphone to hear you.",
                            actionSuccess = false
                        )
                    }
                }

                fun startVoiceInput() {
                    queryGen++
                    queryJob?.cancel()
                    tts.stop()
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) beginListening() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                fun cancelVoice() {
                    queryGen++
                    queryJob?.cancel()
                    queryJob = null
                    stt.stopListening()
                    tts.stop()
                    voiceState = VoiceAssistantState.IDLE
                }

                // ----- Data from Room
                val db = app.database
                val events by db.eventDao().getAllEvents().collectAsState(initial = emptyList())
                SideEffect { latestEvents.value = events }
                val tasks by db.taskDao().getAllTasks().collectAsState(initial = emptyList())
                val notifications by db.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
                val memories by db.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                SideEffect { latestMemories.value = memories }
                val memoryCategories by db.memoryDao().getCategories().collectAsState(initial = emptyList())
                val history by db.automationHistoryDao().getRecentHistory(100).collectAsState(initial = emptyList())
                val chatHistory by db.chatHistoryDao().getAllMessages().collectAsState(initial = emptyList())
                val profile by db.userProfileDao().getUserProfile().collectAsState(initial = null)
                val profileName = profile?.let { listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ") }?.takeIf { it.isNotBlank() }

                var onboarded by remember {
                    mutableStateOf(checkMicPermission(context) && checkNotificationPermission(context) && checkAccessibilityPermission(context))
                }

                val libraryActions = remember {
                    LibraryActions(
                        onMarkHandled = { n -> scope.launch { db.notificationDao().markProcessed(n.id) } },
                        onDeleteNotification = { n -> scope.launch { db.notificationDao().deleteNotification(n) } },
                        onSaveMemory = { existing, key, value, category ->
                            scope.launch {
                                val dao = db.memoryDao()
                                if (existing != null) {
                                    dao.updateMemory(existing.copy(key = key, value = value, category = category, updatedAt = System.currentTimeMillis()))
                                } else {
                                    val same = dao.findMemory(key, category)
                                    if (same != null) dao.updateMemory(same.copy(value = value, updatedAt = System.currentTimeMillis()))
                                    else dao.insertMemory(Memory(key = key, value = value, category = category))
                                }
                            }
                        },
                        onDeleteMemory = { m -> scope.launch { db.memoryDao().deleteMemory(m) } }
                    )
                }

                val clearActions = remember {
                    ClearActions(
                        found = { scope.launch { db.notificationDao().deleteAllNotifications() } },
                        known = { scope.launch { db.memoryDao().deleteAllMemories() } },
                        messages = { scope.launch { db.chatHistoryDao().deleteAllMessages() } },
                        did = { scope.launch { db.automationHistoryDao().deleteAllHistory() } },
                        everything = {
                            scope.launch {
                                db.eventDao().deleteAllEvents()
                                db.notificationDao().deleteAllNotifications()
                                db.chatHistoryDao().deleteAllMessages()
                                db.automationHistoryDao().deleteAllHistory()
                                // Cancel armed alarms before dropping their rows.
                                db.reminderDao().getUpcoming(0L).forEach { ReminderScheduler.cancel(context, it.taskId, "") }
                                db.reminderDao().deleteAll()
                                db.taskDao().deleteAllTasks()
                                db.memoryDao().deleteAllMemories()
                                app.replyIntents.clear()
                            }
                        }
                    )
                }

                // ----- Glass: one backdrop for the whole window. Bars stand down while the
                // voice overlay is up, so at most two glass surfaces ever composite.
                val hazeState = rememberHazeState()
                val glass = remember(hazeState) { GlassEnvironment(hazeState) }
                glass.suspended = voiceState != VoiceAssistantState.IDLE

                val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomChrome = if (onboarded) navInset + TabBarBottomGap + TabBarHeight + Space.s else navInset

                CompositionLocalProvider(LocalGlass provides glass, LocalBottomChrome provides bottomChrome) {
                    Box(modifier = Modifier.fillMaxSize().background(Chitti.colors.background)) {
                        if (onboarded) {
                            ChittiNavHost(navController = navController) {
                                composable(Routes.TODAY) {
                                    TodayScreen(
                                        events = events,
                                        history = history,
                                        profileInitial = profile?.firstName,
                                        onDone = { e -> scope.launch { db.eventDao().deleteEvent(e) } },
                                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                        onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } }
                                    )
                                }
                                composable(Routes.HISTORY) { HistoryScreen(history = history) }
                                composable(Routes.ASK) {
                                    AskScreen(
                                        events = events,
                                        notifications = notifications,
                                        chatHistory = chatHistory,
                                        memories = memories,
                                        dispatcher = dispatcher,
                                        ttsEngine = tts,
                                        onSaveMessage = { m -> scope.launch { db.chatHistoryDao().insertMessage(m) } },
                                        onStartVoice = { startVoiceInput() }
                                    )
                                }
                                composable(Routes.LIBRARY) {
                                    LibraryScreen(
                                        notifications = notifications,
                                        memories = memories,
                                        categories = memoryCategories,
                                        actions = libraryActions,
                                        onOpenFound = { navController.navigate(Routes.FOUND) { launchSingleTop = true } },
                                        onOpenKnows = { navController.navigate(Routes.KNOWS) { launchSingleTop = true } }
                                    )
                                }
                                composable(Routes.FOUND) { FoundScreen(notifications = notifications, actions = libraryActions) }
                                composable(Routes.KNOWS) { KnowsScreen(memories = memories, categories = memoryCategories, actions = libraryActions) }
                                composable(Routes.SETTINGS) {
                                    SettingsScreen(
                                        profileName = profileName,
                                        counts = StoredCounts(
                                            commitments = events.size,
                                            tasks = tasks.size,
                                            found = notifications.size,
                                            known = memories.size,
                                            messages = chatHistory.size,
                                            did = history.size
                                        ),
                                        clear = clearActions,
                                        onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                                        onOpenLab = { navController.navigate(Routes.LAB) { launchSingleTop = true } }
                                    )
                                }
                                composable(Routes.PROFILE) { ProfileScreen() }
                                composable(Routes.LAB) {
                                    AiLabScreen(onAddEvent = { event, task ->
                                        scope.launch {
                                            db.eventDao().insertEvent(event)
                                            db.taskDao().insertTask(task)
                                        }
                                    })
                                }
                            }

                            val backStack by navController.currentBackStackEntryAsState()
                            val route = backStack?.destination?.route
                            ChittiTabBar(
                                selected = Routes.tabOf(route),
                                onSelect = { tab -> navController.selectTab(tab, route) },
                                onMic = { startVoiceInput() },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        } else {
                            PermissionsOnboardingScreen(onAllPermissionsGranted = { onboarded = true })
                        }

                        VoiceOverlay(
                            state = voiceState,
                            transcript = transcript,
                            rmsLevel = rmsLevel,
                            assistantResponse = voiceResponse,
                            onMicClick = { startVoiceInput() },
                            onDismiss = { cancelVoice() },
                            onStopSpeech = {
                                tts.stop()
                                voiceState = VoiceAssistantState.RESULT
                            }
                        )
                    }
                }

                // Leaving the app mid-sentence stops the microphone rather than listening in the background.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && voiceState == VoiceAssistantState.LISTENING) cancelVoice()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager?.destroy()
        ttsEngine?.shutdown()
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        // Ask once per install, not on every launch/rotation: the system dialog is disruptive.
        val prefs = getSharedPreferences("chitti_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return
        prefs.edit().putBoolean("battery_exemption_asked", true).apply()
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: Exception) {
            Log.w("ChittiMain", "Battery optimisation dialog unavailable: ${e.message}")
        }
    }
}

/**
 * Tab behaviour as on iOS: switching tabs keeps each tab's own stack; tapping the tab you are
 * already in pops back to its root.
 */
private fun NavHostController.selectTab(tab: Tab, currentRoute: String?) {
    if (Routes.tabOf(currentRoute) == tab) {
        if (currentRoute != tab.route) popBackStack(tab.route, inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun isRoot(entry: NavBackStackEntry) = entry.destination.route in Routes.roots

/**
 * Transitions follow "enter and exit along the same path":
 *  - between tabs, a quick cross-fade: tabs are peers, so there is no direction to imply;
 *  - a pushed screen slides in from the right edge while the one beneath shifts a quarter left,
 *    and going back retraces exactly that path (the system back gesture drives it predictively).
 * Under reduced motion every change is a plain cross-fade.
 */
@Composable
private fun ChittiNavHost(
    navController: NavHostController,
    builder: androidx.navigation.NavGraphBuilder.() -> Unit
) {
    val reduce = rememberReducedMotion()
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduce || (isRoot(initialState) && isRoot(targetState)) -> fadeIn(Motion.fade(160))
            else -> slideInHorizontally(Motion.standard()) { it }
        }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduce || (isRoot(initialState) && isRoot(targetState)) -> fadeOut(Motion.fade(120))
            else -> slideOutHorizontally(Motion.standard()) { -it / 4 } + fadeOut(Motion.fade(300, easing = androidx.compose.animation.core.LinearEasing), 0.6f)
        }
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when {
            reduce -> fadeIn(Motion.fade(160))
            else -> slideInHorizontally(Motion.standard()) { -it / 4 } + fadeIn(Motion.fade(300), 0.6f)
        }
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when {
            reduce -> fadeOut(Motion.fade(120))
            else -> slideOutHorizontally(Motion.standard()) { it }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
        builder = builder
    )
}
