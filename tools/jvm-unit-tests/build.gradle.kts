plugins { kotlin("jvm") version "2.4.21" }

// Only sources with no android.* imports belong in these lists.
val appSrc = rootDir.resolve("../../app/src")
fun listed(name: String) = file(name).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

val copyMain by tasks.registering(Sync::class) {
    from(listed("main-sources.txt").map { appSrc.resolve("main/kotlin/$it") })
    into(layout.buildDirectory.dir("srcs/main"))
}
val copyTest by tasks.registering(Sync::class) {
    from(listed("test-sources.txt").map { appSrc.resolve("test/kotlin/$it") })
    into(layout.buildDirectory.dir("srcs/test"))
}
sourceSets {
    main { kotlin.setSrcDirs(listOf(copyMain)) }
    test { kotlin.setSrcDirs(listOf(copyTest)) }
}
dependencies { testImplementation("junit:junit:4.13.2") }
kotlin { jvmToolchain(21) }
tasks.test {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
