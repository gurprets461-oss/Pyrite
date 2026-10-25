package dev.gurpret.pyrite

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test
    fun packageAndAbi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("dev.gurpret.pyrite", context.packageName)
        assertNotNull(BuildAbis.pick(android.os.Build.SUPPORTED_ABIS))
    }
}
