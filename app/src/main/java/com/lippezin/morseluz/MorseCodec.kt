package com.lippezin.morseluz

import java.text.Normalizer
import java.util.Locale

data class FlashPulse(
    val onDurationMs: Long,
    val gapAfterMs: Long
)

/** Converts text to International Morse and creates the timing used by the flash sender. */
object MorseCodec {
    private val codes: Map<Char, String> = buildMap {
        put('A', ".-"); put('B', "-..."); put('C', "-.-."); put('D', "-..")
        put('E', "."); put('F', "..-."); put('G', "--."); put('H', "....")
        put('I', ".."); put('J', ".---"); put('K', "-.-"); put('L', ".-..")
        put('M', "--"); put('N', "-."); put('O', "---"); put('P', ".--.")
        put('Q', "--.-"); put('R', ".-."); put('S', "..."); put('T', "-")
        put('U', "..-"); put('V', "...-"); put('W', ".--"); put('X', "-..-")
        put('Y', "-.--"); put('Z', "--..")
        put('0', "-----"); put('1', ".----"); put('2', "..---"); put('3', "...--")
        put('4', "....-"); put('5', "....."); put('6', "-...."); put('7', "--...")
        put('8', "---.."); put('9', "----.")
        put('.', ".-.-.-"); put(',', "--..--"); put('?', "..--.."); put('\'', ".----.")
        put('!', "-.-.--"); put('/', "-..-."); put('(', "-.--."); put(')', "-.--.-")
        put('&', ".-..."); put(':', "---..."); put(';', "-.-.-."); put('=', "-...-")
        put('+', ".-.-."); put('-', "-....-"); put('_', "..--.-"); put('"', ".-..-.")
        put('@', ".--.-.")
    }
    private val charactersByCode = codes.entries.associate { (character, code) -> code to character }

    fun normalize(input: String): String {
        val withoutDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
        val supported = withoutDiacritics.filter { it.isWhitespace() || codes.containsKey(it) }
        return supported.trim().replace(Regex("\\s+"), " ")
    }

    fun preview(input: String): String {
        val normalized = normalize(input)
        if (normalized.isBlank()) return "O código Morse aparecerá aqui"
        return normalized.split(' ').filter(String::isNotBlank).joinToString(" / ") { word ->
            word.mapNotNull { codes[it] }.joinToString("   ")
        }
    }

    fun codeFor(character: Char): String? = codes[character]

    fun characterFor(code: String): Char? = charactersByCode[code]

    fun transmission(input: String, unitMs: Int): List<FlashPulse> {
        val normalized = normalize(input)
        if (normalized.isBlank()) return emptyList()
        val unit = unitMs.coerceIn(100, 400).toLong()
        val pulses = mutableListOf<FlashPulse>()

        normalized.forEachIndexed { index, character ->
            if (character == ' ') return@forEachIndexed
            val code = codes[character] ?: return@forEachIndexed
            var nextIndex = index + 1
            while (nextIndex < normalized.length && normalized[nextIndex] == ' ') nextIndex++
            val endsWord = nextIndex >= normalized.length || nextIndex > index + 1

            code.forEachIndexed { symbolIndex, symbol ->
                val isLastSymbol = symbolIndex == code.lastIndex
                val gap = when {
                    !isLastSymbol -> unit
                    endsWord -> unit * 7
                    else -> unit * 3
                }
                pulses += FlashPulse(
                    onDurationMs = if (symbol == '.') unit else unit * 3,
                    gapAfterMs = gap
                )
            }
        }
        return pulses
    }
}
