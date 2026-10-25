import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.SourceDirectories
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// --- Python (unpacked by scripts/fetch-python.sh) ------------------------------------------

// Android ABI -> python.org package triplet. These are the only ABIs python.org ships.
val pythonAbis = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "x86_64" to "x86_64-linux-android",
)
val crossBuildDir: File = rootProject.file("cross-build")
val pythonVersionFile = File(crossBuildDir, "python-version.txt")
if (!pythonVersionFile.exists()) {
    throw GradleException("Python for Android not found in $crossBuildDir: run scripts/fetch-python.sh")
}
val pyVersion = pythonVersionFile.readText().trim()             // e.g. 3.15.1
val pyMinor = pyVersion.split(".").take(2).joinToString(".") // e.g. 3.15
val pythonPrefixes = pythonAbis.mapValues { (_, triplet) -> File(crossBuildDir, "$triplet/prefix") }
for ((abi, prefix) in pythonPrefixes) {
    if (!File(prefix, "lib/libpython$pyMinor.so").exists()) {
        throw GradleException("Python $pyVersion for $abi missing at $prefix: run scripts/fetch-python.sh")
    }
}

android {
    namespace = "dev.gurpret.pyrite"
    compileSdk = 37
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "dev.gurpret.pyrite"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += pythonAbis.keys
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    // AGP uses ${} itself, so CMakeLists.txt substitutes {{triplet}}.
                    "-DPYTHON_PREFIX_DIR=${crossBuildDir.absolutePath}/{{triplet}}/prefix",
                    "-DPYTHON_VERSION=$pyMinor",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
        buildConfigField("String", "PYTHON_VERSION", "\"$pyVersion\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Files must survive between tests: the orchestrator runs each test in a new process.
        testInstrumentationRunnerArguments["clearPackageData"] = "false"
    }

    testOptions {
        // Each test in its own process, so tests can check behavior across process death.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

androidComponents.onVariants { variant ->
    // Standard library (+ our own Python helpers) as one zip asset, extracted on first launch.
    variant.sources.assets!!.addGeneratedSourceDirectory(
        tasks.register<PackPythonStdlib>("pack${variant.name.cap()}PythonStdlib") {
            pythonVersion.set(pyVersion)
            stdlibDirs.from(pythonPrefixes.values.map { File(it, "lib/python$pyMinor") })
            appPythonDir.set(file("src/main/python"))
        },
        PackPythonStdlib::outputDir,
    )
    // libpython and the libraries its extension modules link against.
    variant.sources.jniLibs!!.addGeneratedSourceDirectory(
        tasks.register<SyncDir>("generate${variant.name.cap()}PythonJniLibs") {
            into(outputDir)
            for ((abi, prefix) in pythonPrefixes) {
                into(abi) {
                    from(File(prefix, "lib"))
                    include("libpython$pyMinor.so", "lib*_python.so")
                }
            }
        },
        SyncDir::outputDir,
    )
}

fun String.cap() = replaceFirstChar(Char::uppercase)

abstract class SyncDir : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

/** Writes python/stdlib.zip and python/stdlib.stamp ("<version> <sha256 of zip>"). */
abstract class PackPythonStdlib : DefaultTask() {
    @get:Input abstract val pythonVersion: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stdlibDirs: ConfigurableFileCollection
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appPythonDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private fun excluded(rel: String): Boolean {
        val top = rel.substringBefore('/')
        val name = rel.substringAfterLast('/')
        return top in setOf("test", "idlelib", "tkinter", "turtledemo") ||
            top.startsWith("config-") ||  // static lib + makefiles for building C extensions
            "__pycache__" in rel.split('/') ||
            (top == "lib-dynload" && (name.startsWith("_test") || name.startsWith("xx") ||
                name.startsWith("_ctypes_test")))
    }

    @TaskAction
    fun pack() {
        val minor = pythonVersion.get().split(".").take(2).joinToString(".")
        val entries = sortedMapOf<String, File>()
        for (dir in stdlibDirs.files) {
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(dir).invariantSeparatorsPath
                // Both ABIs' trees are merged: .py files are identical, and extension
                // modules have the triplet in their names, so they don't collide.
                if (!excluded(rel)) entries.putIfAbsent("lib/python$minor/$rel", f)
            }
        }
        val appDir = appPythonDir.get().asFile
        appDir.walkTopDown().filter { it.isFile && "__pycache__" !in it.path }.forEach { f ->
            entries["lib/python$minor/site-packages/${f.relativeTo(appDir).invariantSeparatorsPath}"] = f
        }

        val out = File(outputDir.get().asFile, "python")
        out.deleteRecursively()
        out.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        ZipOutputStream(DigestOutputStream(File(out, "stdlib.zip").outputStream().buffered(), digest)).use { zip ->
            for ((name, file) in entries) {
                zip.putNextEntry(ZipEntry(name).apply { time = 315532800000L }) // fixed time: reproducible
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        File(out, "stdlib.stamp").writeText("${pythonVersion.get()} $sha\n")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.sora.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
