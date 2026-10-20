package com.lippezin.morseluz

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max

/** Measures bright changes near the centre of the camera image and converts light pulses to text. */
class FlashMorseAnalyzer(
    private val onLevel: (Float) -> Unit,
    private val onText: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decoder = PulseMorseDecoder { decoded ->
        mainHandler.post { onText(decoded) }
    }

    private var baseline = -1.0
    private var pulseActive = false
    private var lastUiUpdateMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val score = brightPixelScore(image)
            val now = SystemClock.elapsedRealtime()
            if (baseline < 0.0) baseline = score

            val onThreshold = max(10.0, baseline * 0.055)
            val offThreshold = max(6.0, baseline * 0.025)

            if (!pulseActive) {
                if (score > baseline + onThreshold && score > 45.0) {
                    pulseActive = true
                    decoder.onPulseStart(now)
                } else {
                    baseline = baseline * 0.98 + score * 0.02
                }
            } else if (score < baseline + offThreshold || score < 40.0) {
                pulseActive = false
                decoder.onPulseEnd(now)
                baseline = baseline * 0.85 + score * 0.15
            }

            decoder.onFrame(now, pulseActive)
            if (now - lastUiUpdateMs >= 120L) {
                lastUiUpdateMs = now
                val level = ((score - baseline) / max(35.0, baseline * 0.35)).toFloat().coerceIn(0f, 1f)
                mainHandler.post { onLevel(level) }
            }
        } catch (_: Exception) {
            // Skip a bad frame; CameraX will provide the next one.
        } finally {
            image.close()
        }
    }

    /** Brightest small tile in the centred ROI; this keeps a tiny LED from being averaged away. */
    private fun brightPixelScore(image: ImageProxy): Double {
        val plane = image.planes.first()
        val buffer = plane.buffer.duplicate()
        val width = image.width
        val height = image.height
        val startX = (width * 0.22).toInt()
        val endX = (width * 0.78).toInt()
        val startY = (height * 0.22).toInt()
        val endY = (height * 0.78).toInt()
        val tileSize = 32
        val tilesWide = ((endX - startX) + tileSize - 1) / tileSize
        val tilesHigh = ((endY - startY) + tileSize - 1) / tileSize
        val sums = LongArray(tilesWide * tilesHigh)
        val counts = IntArray(tilesWide * tilesHigh)
        val xStep = 3
        val yStep = 3

        var y = startY
        while (y < endY) {
            var x = startX
            while (x < endX) {
                val position = y * plane.rowStride + x * plane.pixelStride
                if (position >= 0 && position < buffer.limit()) {
                    val tileX = (x - startX) / tileSize
                    val tileY = (y - startY) / tileSize
                    val tileIndex = tileY * tilesWide + tileX
                    sums[tileIndex] += (buffer.get(position).toInt() and 0xFF).toLong()
                    counts[tileIndex]++
                }
                x += xStep
            }
            y += yStep
        }

        var brightestTile = 0.0
        for (index in sums.indices) {
            if (counts[index] > 0) {
                brightestTile = maxOf(brightestTile, sums[index].toDouble() / counts[index])
            }
        }
        return brightestTile
    }
}

private class PulseMorseDecoder(private val onTextChanged: (String) -> Unit) {
    private val symbols = StringBuilder()
    private val output = StringBuilder()
    private var unitMs = 180.0
    private var pulseStartMs = 0L
    private var lastPulseEndMs = 0L
    private var characterFinalizedForGap = false
    private var wordSpaceAddedForGap = false

    fun onPulseStart(nowMs: Long) {
        finishGap(nowMs)
        pulseStartMs = nowMs
    }

    fun onPulseEnd(nowMs: Long) {
        if (pulseStartMs == 0L) return
        val duration = (nowMs - pulseStartMs).toDouble().coerceAtLeast(1.0)
        val isDot = duration < unitMs * 2.0
        symbols.append(if (isDot) '.' else '-')

        val measuredUnit = if (isDot) duration else duration / 3.0
        if (measuredUnit in 55.0..450.0) {
            unitMs = (unitMs * 0.72 + measuredUnit * 0.28).coerceIn(65.0, 500.0)
        }
        lastPulseEndMs = nowMs
        characterFinalizedForGap = false
        wordSpaceAddedForGap = false
        pulseStartMs = 0L
    }

    fun onFrame(nowMs: Long, isPulseActive: Boolean) {
        if (!isPulseActive) finishGap(nowMs)
    }

    private fun finishGap(nowMs: Long) {
        if (lastPulseEndMs == 0L) return
        val gapMs = (nowMs - lastPulseEndMs).coerceAtLeast(0L).toDouble()

        if (symbols.isNotEmpty() && !characterFinalizedForGap && gapMs >= unitMs * 2.65) {
            val character = MorseCodec.characterFor(symbols.toString())
            if (character != null) output.append(character)
            symbols.clear()
            characterFinalizedForGap = true
            publish()
        }

        if (characterFinalizedForGap && !wordSpaceAddedForGap && gapMs >= unitMs * 6.3) {
            if (output.isNotEmpty() && output.last() != ' ') output.append(' ')
            wordSpaceAddedForGap = true
            publish()
        }
    }

    private fun publish() {
        onTextChanged(output.toString().trimEnd())
    }
}
