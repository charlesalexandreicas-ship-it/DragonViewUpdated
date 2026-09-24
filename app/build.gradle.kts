import java.util.Properties

plugins {
    id("com.android.application")
}

val dragonViewLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun dragonViewConfig(name: String): String =
    (System.getenv(name) ?: dragonViewLocalProperties.getProperty(name, ""))
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "ph.dragonview.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "ph.dragonview.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL",
            "\"${dragonViewConfig("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY",
            "\"${dragonViewConfig("SUPABASE_PUBLISHABLE_KEY")}\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        // Keep LiteRT model assets memory-mappable when the trained model is added.
        noCompress += "tflite"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity:1.12.4")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime:2.10.5")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.navigation:navigation-fragment:2.9.6")
    implementation("androidx.navigation:navigation-ui:2.9.6")

    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.ai.edge.litert:litert:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// Fallback compiler for restricted Windows hosts where Gradle worker loopback
// sockets are unavailable. Normal VS Code and Android Studio builds remain unchanged.
tasks.register("compileDebugJavaDirect") {
    dependsOn("dataBindingGenBaseClassesDebug", "generateDebugRFile",
        "generateDebugBuildConfig", "javaPreCompileDebug")

    doLast {
        val standardTask = tasks.named<JavaCompile>("compileDebugJavaWithJavac").get()
        val destination = standardTask.destinationDirectory.get().asFile
        val generatedSources = standardTask.options.generatedSourceOutputDirectory.get().asFile
        destination.mkdirs()
        generatedSources.mkdirs()

        val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
            ?: throw GradleException("A full Java 17 JDK is required to compile DragonView.")
        val compilerArguments = mutableListOf(
            "-source", "17",
            "-target", "17",
            "-encoding", "UTF-8",
            "-classpath", standardTask.classpath.asPath,
            "-processorpath", requireNotNull(standardTask.options.annotationProcessorPath).asPath,
            "-s", generatedSources.absolutePath,
            "-d", destination.absolutePath,
            "-XDstringConcat=inline"
        )
        compilerArguments.addAll(standardTask.source.files.map { it.absolutePath })

        val result = compiler.run(null, System.out, System.err, *compilerArguments.toTypedArray())
        if (result != 0) {
            throw GradleException("Direct Java compilation failed with exit code $result.")
        }
    }
}
