plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qi.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qi.player"
        minSdk = 26            // Android 8.0：自适应图标、AudioEffect、MediaSession 均齐备
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compose 编译器必须与 Kotlin 版本严格对应：Kotlin 1.9.22 -> 1.5.8
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 用 debug 签名给 release 包签名：目的是去掉 debuggable 标记
            //（debug 构建下 ART 只做 quicken，Compose 重组校验也全开，实机会明显掉帧）。
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Media3：ExoPlayer 播放内核 + 后台播放会话（通知栏 / 线控 / 蓝牙按键）
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Coil：加载 MediaStore 内嵌封面
    implementation("io.coil-kt:coil-compose:2.5.0")

    // DocumentFile：SAF 目录导入（用户手动指定曲库文件夹）
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 说明：本项目不引入 Room / KSP。歌单与收藏数据量小，用 JSON 持久化即可，
    // 少一个注解处理器就少一类构建失败面，也让增量编译更快。
}
