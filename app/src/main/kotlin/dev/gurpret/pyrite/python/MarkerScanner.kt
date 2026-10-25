package dev.gurpret.pyrite.python

/**
 * Splits a text stream on a sync marker, holding back any trailing text that
 * could be the start of a marker split across reads.
 */
class MarkerScanner(private val marker: String, private val onText: (String) -> Unit, private val onMarker: () -> Unit) {
    private val pending = StringBuilder()

    fun feed(chunk: String) {
        pending.append(chunk)
        while (true) {
            val i = pending.indexOf(marker)
            if (i < 0) break
            if (i > 0) onText(pending.substring(0, i))
            pending.delete(0, i + marker.length)
            onMarker()
        }
        // Emit everything that can't be the beginning of a marker.
        val keep = longestMarkerPrefixSuffix()
        val emit = pending.length - keep
        if (emit > 0) {
            onText(pending.substring(0, emit))
            pending.delete(0, emit)
        }
    }

    /** Flushes held-back text (at end of stream). */
    fun finish() {
        if (pending.isNotEmpty()) onText(pending.toString())
        pending.setLength(0)
    }

    private fun longestMarkerPrefixSuffix(): Int {
        val max = minOf(marker.length - 1, pending.length)
        for (len in max downTo 1) {
            if (pending.regionMatches(pending.length - len, marker, 0, len)) return len
        }
        return 0
    }

    private fun StringBuilder.regionMatches(start: Int, other: String, otherStart: Int, len: Int): Boolean {
        for (k in 0 until len) if (this[start + k] != other[otherStart + k]) return false
        return true
    }
}
