package dev.gurpret.pyrite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildAbisTest {
    @Test
    fun mapsShippedAbis() {
        assertEquals("aarch64-linux-android", BuildAbis.tripletFor("arm64-v8a"))
        assertEquals("x86_64-linux-android", BuildAbis.tripletFor("x86_64"))
        assertNull(BuildAbis.tripletFor("armeabi-v7a"))
    }

    @Test
    fun picksFirstShippedAbi() {
        assertEquals("x86_64", BuildAbis.pick(arrayOf("x86", "x86_64", "arm64-v8a")))
        assertNull(BuildAbis.pick(arrayOf("armeabi-v7a", "x86")))
    }
}
