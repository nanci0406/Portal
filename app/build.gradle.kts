import com.android.build.api.dsl.ApplicationExtension
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "moe.fuqiuluo.portal"
    compileSdk = 35

    defaultConfig {
        applicationId = "moe.fuqiuluo.portal"
        minSdk = 26
        targetSdk = 35
        versionCode = getVersionCode()
        versionName = "1.0.4" + ".r${getGitCommitCount()}." + getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")   // 只保留 arm64
        }

        manifestPlaceholders["BUGLY_APPID"] = "222f9ef298"

        val publicIp = try {
            val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
            val process = if (isWindows) {
                Runtime.getRuntime().exec(arrayOf(
                    "powershell.exe", "-command",
                    "(Invoke-WebRequest -Uri 'https://api.ipify.org' -UseBasicParsing).Content," +
                            "(Invoke-WebRequest -Uri 'https://ifconfig.me' -UseBasicParsing).Content," +
                            "(Invoke-WebRequest -Uri 'https://icanhazip.com' -UseBasicParsing).Content," +
                            "(Invoke-WebRequest -Uri 'https://checkip.amazonaws.com' -UseBasicParsing).Content" +
                            " | Select-Object -First 1"
                ))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c",
                    "curl -s https://api.ipify.org || " +
                            "curl -s https://ifconfig.me || " +
                            "curl -s https://icanhazip.com || " +
                            "curl -s https://checkip.amazonaws.com"
                ))
            }
            val reader = process.inputStream.bufferedReader()
            val ip = reader.readLine()?.trim() ?: "unknown"
            if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+".toRegex())) ip else "unknown"
        } catch (e: Exception) {
            println("Error getting public IP address: ${e.message}")
            "unknown"
        }

        val deviceName = try {
            val process = Runtime.getRuntime().exec("hostname")
            val reader = process.inputStream.bufferedReader()
            reader.readLine()?.trim() ?: "unknown"
        } catch (e: Exception) {
            println("Error getting device name: ${e.message}")
            "unknown"
        }

        val buildPath = project.rootDir.absolutePath.replace("\\", "/")
        manifestPlaceholders["BUGLY_BUILD_ENV"] = "IP:$publicIp,DEVICE:$deviceName,PATH:$buildPath"
        manifestPlaceholders["APP_CHANNEL"] = "$publicIp-$deviceName"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["APP_VERSION"] = defaultConfig.versionName ?: "UnknownVersion"
            manifestPlaceholders["BUGLY_ENABLE_DEBUG"] = "false"
        }

        debug {
            manifestPlaceholders["APP_VERSION"] = "${defaultConfig.versionName}-debug"
            manifestPlaceholders["BUGLY_ENABLE_DEBUG"] = "true"
        }
    }

    // 输出 APK 文件名（只保留 arm64）
    applicationVariants.all {
        outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach {
                it.outputFileName = "Portal-v${versionName}-arm64.apk"
            }
    }

    flavorDimensions.add("mode")

    productFlavors {
        create("arm64") {
            dimension = "mode"
            isDefault = true                    // 关键：让 assembleRelease 能直接使用
            ndk {
                println("Compiling arm64 architecture only")
                abiFilters.add("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/armeabi/**"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*"
            excludes += "lib/armeabi/**"
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("libs")
    }

    configureAppSigningConfigsForRelease(project)
}

fun configureAppSigningConfigsForRelease(project: Project) {
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    if (keystorePath.isNullOrBlank()) {
        return
    }
    project.configure<ApplicationExtension> {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV2Signing = true
            }
        }
        buildTypes {
            release {
                signingConfig = signingConfigs.findByName("release")
            }
            debug {
                signingConfig = signingConfigs.findByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":xposed"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.okhttp)
    implementation(libs.fastjson)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.reflect)

    implementation(libs.bugly)
    implementation(libs.geotools)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ==================== 工具函数 ====================
fun getGitCommitCount(): Int {
    val out = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = out
    }
    return out.toString().trim().toInt()
}

fun getGitCommitHash(): String {
    val out = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = out
    }
    return out.toString().trim()
}

fun getVersionCode(): Int {
    return (System.currentTimeMillis() / 1000L).toInt()
}

fun getVersionName(): String {
    return getGitCommitHash()
}
