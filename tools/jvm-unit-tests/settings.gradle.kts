// Runs the Android-free unit tests on a plain JVM, without the Android SDK.
// For machines that can't reach Google's Maven/SDK hosts. Usage:
//   gradle -p tools/jvm-unit-tests test      (any local Gradle 8.x+)
pluginManagement { repositories { maven("https://repo1.maven.org/maven2"); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { maven("https://repo1.maven.org/maven2") } }
rootProject.name = "pyrite-jvm-unit-tests"
