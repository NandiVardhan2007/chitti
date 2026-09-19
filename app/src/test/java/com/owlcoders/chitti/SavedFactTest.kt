package com.owlcoders.chitti

import com.owlcoders.chitti.automation.describeSavedFact
import com.owlcoders.chitti.automation.matchSavedFact
import com.owlcoders.chitti.db.entities.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedFactTest {
    private val branch = Memory(key = "My branch", value = "CSE - AIML")
    private val college = Memory(key = "My college", value = "Aditya University")
    private val mentor = Memory(key = "Project mentor", value = "Dr. Rao")

    @Test fun matchesTheFactNamedInTheQuestion() {
        assertEquals(branch, matchSavedFact("what is my branch", listOf(college, branch)))
        assertEquals(branch, matchSavedFact("what's my branch?", listOf(college, branch)))
        assertEquals(college, matchSavedFact("tell me my college", listOf(college, branch)))
    }

    @Test fun needsEveryWordOfTheFactName() {
        assertNull(matchSavedFact("who is my mentor", listOf(mentor)))
        assertEquals(mentor, matchSavedFact("who is my project mentor", listOf(mentor)))
    }

    @Test fun noMatchWhenNothingFits() {
        assertNull(matchSavedFact("what is my phone number", listOf(branch, college)))
        assertNull(matchSavedFact("what is my branch", emptyList()))
    }

    @Test fun phrasesTheAnswer() {
        assertEquals("Your branch is CSE - AIML.", describeSavedFact(branch))
        assertEquals("Project mentor: Dr. Rao.", describeSavedFact(mentor))
    }
}
