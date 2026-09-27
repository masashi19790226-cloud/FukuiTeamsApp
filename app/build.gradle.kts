plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fukuiteams.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fukuiteams.app"
        minSdk = 24
        targetSdk = 34
        // GitHub Actions の実行番号をバージョン番号にする(ビルドのたびに増えるので上書きインストールできる)
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    // 毎回同じ鍵で署名するための設定。
    // GitHub Actions が Secret(DEBUG_KEYSTORE_BASE64)から app/fukuispo-debug.jks を復元する。
    // 鍵ファイルが無いとき(手元でのビルドなど)は、Android標準のデバッグ鍵を使う。
    signingConfigs {
        getByName("debug") {
            val keystore = file("fukuispo-debug.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "fukuispo"
                keyAlias = "fukuispo"
                keyPassword = "fukuispo"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
