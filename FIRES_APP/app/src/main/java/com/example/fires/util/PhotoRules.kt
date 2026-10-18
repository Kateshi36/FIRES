package com.example.fires.util

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The rules for incident photos (D3). Pure Kotlin, so it runs as a plain JVM test.
 * The Android part (reading, rotating, compressing) is in PhotoProcessor and follows these rules.
 *
 * Accepted: JPEG, PNG, WebP, up to [MAX_SOURCE_BYTES] before shrinking.
 * Result: always a JPEG of at most [TARGET_MAX_BYTES] (about 200 KB), longest side at most [MAX_SIDE].
 */
object PhotoRules {

    const val MAX_SOURCE_BYTES = 10 * 1024 * 1024
    const val TARGET_MAX_BYTES = 200 * 1024
    const val MAX_SIDE = 1024

    val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    const val MSG_NOT_IMAGE = "That file isn't a photo we can read. Choose another one."
    const val MSG_BAD_TYPE = "Only JPEG, PNG or WebP photos are supported."
    const val MSG_TOO_LARGE = "That photo is too large (over 10 MB). Choose a smaller one."
    const val MSG_FAILED = "We couldn't prepare that photo. Try another one."

    /** One try at shrinking: scale the photo so its longest side is [maxSide], save at JPEG [quality]. */
    data class Attempt(val maxSide: Int, val quality: Int)

    /**
     * Tried in order until the result fits in [TARGET_MAX_BYTES]. A normal phone photo fits on the
     * first or second try; the later rows are for photos that are very detailed or noisy.
     * Sizes never go up and quality never goes up within a size.
     */
    val ATTEMPTS: List<Attempt> = listOf(
        Attempt(1024, 80), Attempt(1024, 65), Attempt(1024, 50),
        Attempt(800, 80), Attempt(800, 65), Attempt(800, 50),
        Attempt(640, 75), Attempt(640, 60), Attempt(640, 45),
        Attempt(480, 70), Attempt(480, 55), Attempt(480, 40)
    )

    /** The real type of the file (found by reading it, not from its name). Null means fine. */
    fun validateType(mimeType: String?): String? {
        val type = mimeType?.trim()?.lowercase()
        return when {
            type.isNullOrEmpty() -> MSG_NOT_IMAGE
            type == "image/jpg" -> null // a common spelling of image/jpeg
            type in ALLOWED_TYPES -> null
            type.startsWith("image/") -> MSG_BAD_TYPE // a real image, but GIF, HEIC and so on
            else -> MSG_NOT_IMAGE
        }
    }

    /** Size of the original file. Null means fine. */
    fun validateSourceSize(bytes: Long): String? = when {
        bytes <= 0L -> MSG_NOT_IMAGE
        bytes > MAX_SOURCE_BYTES -> MSG_TOO_LARGE
        else -> null
    }

    /** Fits [width] x [height] inside [maxSide] on the longest side, keeping the shape. Never enlarges. */
    fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
