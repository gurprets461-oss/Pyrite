package dev.gurpret.pyrite.python

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerScannerTest {
    private val text = StringBuilder()
    private var markers = 0
    private val scanner = MarkerScanner("<M>", { text.append(it) }, { markers++ })

    @Test
    fun passesTextThrough() {
        scanner.feed("hello ")
        scanner.feed("world")
        assertEquals("hello world", text.toString())
        assertEquals(0, markers)
    }

    @Test
    fun detectsMarkerSplitAcrossChunks() {
        scanner.feed("45\n<")
        assertEquals("45\n", text.toString())
        scanner.feed("M")
        scanner.feed(">tail")
        assertEquals("45\ntail", text.toString())
        assertEquals(1, markers)
    }

    @Test
    fun releasesFalsePrefix() {
        scanner.feed("a<")
        scanner.feed("x")
        assertEquals("a<x", text.toString())
    }

    @Test
    fun multipleMarkersInOneChunk() {
        scanner.feed("1<M>2<M>3")
        assertEquals("123", text.toString())
        assertEquals(2, markers)
    }

    @Test
    fun finishFlushesHeldText() {
        scanner.feed("end<M")
        scanner.finish()
        assertEquals("end<M", text.toString())
    }
}
