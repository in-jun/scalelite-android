package dev.injun.scalelite.core.protocol

/**
 * Turns a stream of live weight readings into confirmed weigh-ins for dialects without a
 * stability flag. A value is confirmed once it repeats [requiredRepeats] times in a row
 * (captures show the settled value repeating 16+ times at ~250 ms). Readings under
 * [minimumGrams] mean nobody is standing on the scale; they reset the run and re-arm the
 * detector so a second person stepping on during the same connection is also recorded.
 *
 * Not thread-safe: feed it from one coroutine.
 */
class StableWeightDetector(
    private val requiredRepeats: Int = DEFAULT_REPEATS,
    private val minimumGrams: Int = DEFAULT_MINIMUM_GRAMS,
) {
    private var lastGrams = -1
    private var repeats = 0
    private var confirmedGrams = -1

    /** The number of consecutive identical readings seen so far, for progress UI. */
    val progress: Int get() = repeats.coerceAtMost(requiredRepeats)

    /** Feeds one reading; returns the confirmed grams the moment a value settles, else null. */
    fun offer(grams: Int): Int? {
        if (grams < minimumGrams) {
            lastGrams = -1
            repeats = 0
            confirmedGrams = -1
            return null
        }
        if (grams == lastGrams) repeats++ else {
            lastGrams = grams
            repeats = 1
        }
        if (repeats < requiredRepeats || grams == confirmedGrams) return null
        confirmedGrams = grams
        return grams
    }

    fun reset() {
        lastGrams = -1
        repeats = 0
        confirmedGrams = -1
    }

    companion object {
        const val DEFAULT_REPEATS = 5
        const val DEFAULT_MINIMUM_GRAMS = 10_000
    }
}
