package dev.gurpret.pyrite.settings

/** The recent-files list (pure, unit-tested). */
object RecentFiles {
    const val MAX = 10

    fun add(list: List<String>, path: String): List<String> = (listOf(path) + list.filter { it != path }).take(MAX)

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(s: String?): List<String> = s?.split('\n')?.filter { it.isNotBlank() }.orEmpty().take(MAX)
}
