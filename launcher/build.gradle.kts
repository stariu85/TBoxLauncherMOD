import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun otaPublicKey(propertyName: String): String =
    localProperties.getProperty(propertyName).orEmpty().trim()

android {
    namespace = "vad.dashing.tbox"
    compileSdk = 36

    defaultConfig {
        // Separate APK from TBox Monitor — installable side-by-side as HOME.
        applicationId = "ras.dashing.tbox.launcher"
        minSdk = 28
        targetSdk = 36
        versionCode = 61
        versionName = "0.5.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TBOX_PROXY_VERSION", "\"${libs.versions.tboxProxy.get()}\"")
        // Separate Yandex Disk folders from Monitor. Set in local.properties:
        //   launcher.update.releasePublicKey=https://disk.yandex.ru/d/...
        //   launcher.update.devPublicKey=https://disk.yandex.ru/d/...
        buildConfigField(
            "String",
            "UPDATE_RELEASE_PUBLIC_KEY",
            "\"${otaPublicKey("launcher.update.releasePublicKey")}\"",
        )
        buildConfigField(
            "String",
            "UPDATE_DEV_PUBLIC_KEY",
            "\"${otaPublicKey("launcher.update.devPublicKey")}\"",
        )
        buildConfigField("String", "UPDATE_SIGNING_CERT_SHA256", "\"\"")
    }
    flavorDimensions += "language"
    productFlavors {
        create("ru") {
            dimension = "language"
            versionNameSuffix = "-ru"
        }
        create("en") {
            dimension = "language"
            versionNameSuffix = "-en"
        }
    }
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    androidResources {
        ignoreAssetsPattern = "jetour720:models/web:.DS_Store:thumbs.db"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val validateLauncherCarRigAssets by tasks.registering {
    group = "verification"
    description = "Verifies that launcher GLB and pivot metadata contain required rig nodes."
    val glb = layout.projectDirectory.file("src/main/assets/models/jetour_dashing_site.glb")
    val pivots = layout.projectDirectory.file("src/main/assets/models/jetour_car_pivots.json")
    inputs.files(glb, pivots)

    doLast {
        val requiredNodes = listOf(
            "p_door01",
            "p_door02",
            "p_door03",
            "p_door04",
            "houbeimen_copy",
            "wheel_lungu01_L",
            "wheel_lungu01_R",
            "wheel_lungu02_L",
            "wheel_lungu02_R",
        )
        val glbText = glb.asFile.readBytes().toString(Charsets.ISO_8859_1)
        val pivotText = pivots.asFile.readText()
        val missingGlb = requiredNodes.filterNot(glbText::contains)
        val missingPivots = requiredNodes.filterNot(pivotText::contains)
        check(missingGlb.isEmpty() && missingPivots.isEmpty()) {
            "Launcher car rig asset contract broken. " +
                "Missing GLB nodes=$missingGlb, missing pivot references=$missingPivots"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(validateLauncherCarRigAssets)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.profileinstaller.profileinstaller)
    implementation(libs.sceneview)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.androidx.palette)
    implementation("com.github.jsparrow2006:tbox-proxy:v${libs.versions.tboxProxy.get()}")
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
