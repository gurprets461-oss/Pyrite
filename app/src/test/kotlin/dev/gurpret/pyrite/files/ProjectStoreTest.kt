package dev.gurpret.pyrite.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { ProjectStore(File(tmp.root, "projects")) }

    @Test
    fun defaultFileCreatedOnceWithSample() {
        val f = store.defaultFile("print(1)\n")
        assertEquals("main.py", f.name)
        assertEquals("print(1)\n", store.read(f))
        store.write(f, "changed")
        assertEquals("changed", store.read(store.defaultFile("print(1)\n")))
    }

    @Test
    fun writeIsAtomicAndCreatesFolders() {
        val f = File(store.root, "pkg/mod.py")
        store.write(f, "x = 1\n")
        assertEquals("x = 1\n", f.readText())
        assertFalse(File(f.parentFile, ".mod.py.tmp").exists())
    }

    @Test
    fun containsRejectsOutsidePaths() {
        assertTrue(store.contains(File(store.root, "a/b.py")))
        assertFalse(store.contains(File(store.root, "../escape.py")))
        assertFalse(store.contains(File(tmp.root, "other.py")))
    }

    @Test
    fun relativePath() {
        assertEquals("pkg/mod.py", store.relativePath(File(store.root, "pkg/mod.py")))
    }
}
