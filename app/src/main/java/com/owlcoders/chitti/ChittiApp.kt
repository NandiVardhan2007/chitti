package com.owlcoders.chitti

import android.app.Application
import android.app.PendingIntent
import android.util.Log
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.services.ExtractionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChittiApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // In-memory cache for deep-linking directly back to the exact notification intent
    val replyIntents = mutableMapOf<Int, PendingIntent>()
    
    // Pre-warm the LLM to avoid cold-start delays during demo
    var extractionEngine: ExtractionEngine? = null
        private set
    
    private val appScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                extractionEngine = ExtractionEngine(this@ChittiApp)
            } catch (t: Throwable) {
                Log.w("ChittiApp", "LLM ExtractionEngine fallback mode: ${t.message}")
            }
        }
    }
}
