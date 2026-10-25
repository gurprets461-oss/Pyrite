package dev.gurpret.pyrite.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileOpsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val root by lazy { File(tmp.root, "projects").apply { mkdirs() } }
    private val ops by lazy { FileOps(root) }

    @Test
    fun listsFoldersFirstSortedAndHidesTempFiles() {
        ops.createFile(root, "b.py")
        ops.createFile(root, "A.py")
        ops.createFolder(root, "zeta")
        ops.createFolder(root, "alpha")
        File(root, ".x.py.tmp").writeText("")
        File(root, "__pycache__").mkdirs()
        assertEquals(listOf("alpha", "zeta", "A.py", "b.py"), ops.list(root).map { it.name })
    }

    @Test
    fun createRenameDelete() {
        val proj = ops.createFolder(root, "proj")
        val f = ops.createFile(proj, "main.py", "print(1)")
        assertEquals("print(1)", f.readText())
        val renamed = ops.rename(f, "app.py")
        assertFalse(f.exists())
        assertEquals("print(1)", renamed.readText())
        ops.delete(proj)
        assertFalse(proj.exists())
    }

    @Test
    fun refusesDuplicatesBadNamesAndEscapes() {
        ops.createFile(root, "a.py")
        assertThrows(IOException::class.java) { ops.createFile(root, "a.py") }
        assertThrows(IOException::class.java) { ops.createFile(root, "x/y.py") }
        assertThrows(IOException::class.java) { ops.createFolder(root, "..") }
        assertThrows(IOException::class.java) { ops.delete(root) }
        assertThrows(IOException::class.java) { ops.list(tmp.root) }
        assertThrows(IOException::class.java) { ops.createFile(File(root, ".."), "evil.py") }
    }

    @Test
    fun validateName() {
        assertNull(ops.validateName("ok.py"))
        assertNotNull(ops.validateName(""))
        assertNotNull(ops.validateName("a/b"))
    }

    @Test
    fun uniqueNames() {
        assertEquals("a.py", ops.uniqueName(root, "a.py"))
        ops.createFile(root, "a.py")
        assertEquals("a (1).py", ops.uniqueName(root, "a.py"))
        ops.createFile(root, "a (1).py")
        assertEquals("a (2).py", ops.uniqueName(root, "a.py"))
        ops.createFolder(root, "data")
        assertEquals("data (1)", ops.uniqueName(root, "data"))
    }

    @Test
    fun editableByExtension() {
        assertTrue(FileOps.isEditable(File("x.py")))
        assertTrue(FileOps.isEditable(File("notes.TXT")))
        assertTrue(FileOps.isEditable(File("Makefile")))
        assertFalse(FileOps.isEditable(File("img.png")))
    }
}
