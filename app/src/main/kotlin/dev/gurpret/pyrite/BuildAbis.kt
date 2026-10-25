package dev.gurpret.pyrite

/** Maps between Android ABI names and the GNU triplets python.org uses for its packages. */
object BuildAbis {
    val TRIPLETS = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "x86_64" to "x86_64-linux-android",
    )

    fun tripletFor(abi: String): String? = TRIPLETS[abi]

    /** Picks the first ABI from [supported] (most preferred first) that we ship. */
    fun pick(supported: Array<String>): String? = supported.firstOrNull { it in TRIPLETS }
}
