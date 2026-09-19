package com.owlcoders.chitti

import com.owlcoders.chitti.automation.asksForTimeOrDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeQuestionTest {
    @Test fun fullQuestions() {
        listOf("what time is it", "what's the time", "what is the time now", "tell me the time",
            "what day is it", "today's date", "what's the date").forEach { assertTrue(it, asksForTimeOrDate(it)) }
    }

    @Test fun fragmentsLeftBySpeechRecognition() {
        listOf("time it is", "time is it", "time now", "time").forEach { assertTrue(it, asksForTimeOrDate(it)) }
    }

    @Test fun otherQuestionsAreNotTimeQuestions() {
        listOf("remind me in 30 minutes", "who wrote hamlet", "what is my branch", "next time open youtube")
            .forEach { assertFalse(it, asksForTimeOrDate(it)) }
    }
}
