package com.lippezin.morseluz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodecTest {
    @Test
    fun normalizesAccentsAndCase() {
        assertEquals("OLA MUNDO", MorseCodec.normalize("Olá   mundo"))
    }

    @Test
    fun previewsWordsWithSlashSeparator() {
        assertEquals("...   ---   ... / .-   -...", MorseCodec.preview("SOS AB"))
    }

    @Test
    fun makesDotAndDashDurations() {
        val pulses = MorseCodec.transmission("ET", 180)
        assertEquals(2, pulses.size)
        assertEquals(180L, pulses[0].onDurationMs)
        assertEquals(540L, pulses[1].onDurationMs)
        assertEquals(540L, pulses[0].gapAfterMs)
        assertEquals(1260L, pulses[1].gapAfterMs)
    }

    @Test
    fun ignoresUnsupportedCharactersWithoutCrashing() {
        assertTrue(MorseCodec.transmission("🙂", 180).isEmpty())
    }
}
