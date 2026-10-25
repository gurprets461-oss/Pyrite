package dev.gurpret.pyrite.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SitePackagesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dist(dir: String, metadata: String) {
        File(tmp.root, dir).apply { mkdirs() }.resolve("METADATA").writeText(metadata)
    }

    @Test
    fun listsDistInfoSorted() {
        dist("requests-2.33.0.dist-info", "Metadata-Version: 2.1\nName: requests\nVersion: 2.33.0\n\nName: not-this\n")
        dist("Rich-14.0.0.dist-info", "Metadata-Version: 2.4\nName: rich\nVersion: 14.0.0\n")
        dist("broken.dist-info", "Metadata-Version: 2.1\n")
        File(tmp.root, "requests").mkdirs()
        assertEquals(
            listOf(InstalledPackage("requests", "2.33.0"), InstalledPackage("rich", "14.0.0")),
            SitePackages.list(tmp.root),
        )
    }

    @Test
    fun missingDirIsEmpty() {
        assertEquals(emptyList<InstalledPackage>(), SitePackages.list(File(tmp.root, "nope")))
    }

    @Test
    fun pipCommands() {
        assertEquals(listOf("install", "requests", "rich"), PipCommand.parse("pip install requests rich"))
        assertEquals(listOf("list"), PipCommand.parse("  %pip list"))
        assertEquals(listOf("install", "x>=1,<2"), PipCommand.parse("!pip3 install 'x>=1,<2'"))
        assertEquals(emptyList<String>(), PipCommand.parse("pip"))
        assertNull(PipCommand.parse("pipe = 1"))
        assertNull(PipCommand.parse("print('pip install')"))
        assertNull(PipCommand.parse("pips install x"))
    }
}
