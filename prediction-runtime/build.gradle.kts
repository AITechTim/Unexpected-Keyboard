plugins {
  id("com.android.library")
}

android {
  namespace = "juloo.keyboard2.prediction.runtime"
  compileSdk = 36
  defaultConfig {
    minSdk = 28
    consumerProguardFiles("consumer-rules.pro")
    ndk { abiFilters += "arm64-v8a" }
    externalNativeBuild {
      cmake {
        arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
        cppFlags += "-std=c++17"
      }
    }
  }
  ndkVersion = "28.2.13676358"
  externalNativeBuild {
    cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}
