plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("maven-publish") // 引入 maven 插件
}

android {
    namespace = "pan.lib.camera_record"
    compileSdk = 34

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("debug") {
            java.srcDirs(
                "src/main/java",
                "build/generated/data_binding_base_class_source_out/debug/out"
            )
        }
    }


    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.permissionx)

}


publishing { // 发布配置
    publications { // 发布的内容
        register<MavenPublication>("camera_record") { // 注册一个名字为 camera_record 的发布内容
            groupId = "com.github.PanPersonalProject"
            artifactId = "camera_record"
            version = "1.0.0-SNAPSHOT"

            afterEvaluate { // 在所有的配置都完成之后执行
                // 从当前 module 的 release 包中发布
                from(components["release"])
            }
        }
    }

    val isLocal = true // 是否是本地发布
    repositories {
        if (isLocal) {
            maven {
                //本地路径camera_record/build/repos
                val releasesRepoUrl = layout.buildDirectory.dir("repos/releases")
                val snapshotsRepoUrl = layout.buildDirectory.dir("repos/snapshots")
                url = uri(if (project.hasProperty("release")) releasesRepoUrl else snapshotsRepoUrl)

                //publishToMavenLocal 默认地址： // <home directory of the current user>/.m2/repository
            }
        }

    }

}

