plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.starchen.wol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.no4num.wol"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "1.0.11"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 签名材料一律不进仓库，按下面顺序查找，全都没有就退回 debug 签名（依旧能打出可安装的包）：
        //   1. 环境变量 KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
        //   2. local.properties 里的 starwol.* （该文件已被 .gitignore 排除）
        //   3. 工程根目录的 .keystore/starwol.jks + local.properties 里的密码
        create("shared") {
            // local.properties 是 Properties 格式，这里只做最简解析：
            // Kotlin DSL 里写 java.util.* 会被解析成 JavaPluginExtension，干脆绕开。
            val props: Map<String, String> = run {
                val f = rootProject.file("local.properties")
                if (!f.exists()) return@run emptyMap()
                f.readLines().mapNotNull { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#") || !line.contains('=')) null
                    else line.substringBefore('=').trim() to line.substringAfter('=').trim()
                }.toMap()
            }
            fun pick(propKey: String, envKey: String): String? =
                System.getenv(envKey)?.takeIf { it.isNotBlank() }
                    ?: props[propKey]?.takeIf { it.isNotBlank() }

            val store = pick("starwol.storeFile", "KEYSTORE_PATH")
                ?: rootProject.file(".keystore/starwol.jks").takeIf { it.exists() }?.absolutePath
            val storePw = pick("starwol.storePassword", "KEYSTORE_PASSWORD")

            if (store != null && storePw != null) {
                storeFile = File(store)
                storePassword = storePw
                keyAlias = pick("starwol.keyAlias", "KEY_ALIAS") ?: "starwol"
                keyPassword = pick("starwol.keyPassword", "KEY_PASSWORD") ?: storePw
            }
        }
    }

    buildTypes {
        // shared 没有配好签名材料时，退回 AGP 内置的 debug 签名
        val signer = signingConfigs.getByName("shared").takeIf { it.storeFile != null }
            ?: signingConfigs.getByName("debug")

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signer
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signer
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation("junit:junit:4.13.2")
}

apply(plugin = "app.cash.paparazzi")
