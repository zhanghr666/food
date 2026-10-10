plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// JVM 文件编码在 gradle.properties 统一设置为 UTF-8。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
android {
    namespace = "com.example.foodmanager"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.foodmanager"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 只打包 ARM 架构：离线语音库 libvosk.so 覆盖 4 个架构共约 39MB，
        // 手机都是 ARM（arm64-v8a / armeabi-v7a），去掉 x86 可省 ~20MB。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        // 验证包单独安装，不覆盖手机中的正式食物库存。
        create("validation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".validation"
            matchingFallbacks += "debug"
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
    androidResources {
        // 离线语音模型不能压缩（Vosk 直接按文件路径读取）
        noCompress += "model-cn"
        noCompress += "tflite"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // 本地数据库（SQLite + Room，数据全部保存在手机本地）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 后台提醒 + 协程
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // 离线 OCR（中英文文字识别，模型随 APK 打包，无需联网）
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mediapipe.tasks.vision)

    // 离线语音识别（Vosk，中文模型随 APK 打包，完全本机运行）
    implementation(libs.vosk.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
