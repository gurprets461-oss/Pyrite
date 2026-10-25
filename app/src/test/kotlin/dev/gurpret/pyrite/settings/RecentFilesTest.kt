package dev.gurpret.pyrite.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesTest {
    @Test
    fun mostRecentFirstWithoutDuplicates() {
        var l = emptyList<String>()
        l = RecentFiles.add(l, "/a.py")
        l = RecentFiles.add(l, "/b.py")
        l = RecentFiles.add(l, "/a.py")
        assertEquals(listOf("/a.py", "/b.py"), l)
    }

    @Test
    fun capped() {
        var l = emptyList<String>()
        repeat(15) { l = RecentFiles.add(l, "/$it.py") }
        assertEquals(RecentFiles.MAX, l.size)
        assertEquals("/14.py", l.first())
    }

    @Test
    fun roundTrip() {
        val l = listOf("/x y.py", "/z.py")
        assertEquals(l, RecentFiles.decode(RecentFiles.encode(l)))
        assertEquals(emptyList<String>(), RecentFiles.decode(null))
    }
}
