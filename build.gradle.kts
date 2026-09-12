plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

android {
    namespace  = "com.turnstilekit"
    compileSdk = 34

    defaultConfig {
        minSdk    = 21
        targetSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.android.material:material:1.11.0")
}

// ── Maven publishing ──────────────────────────────────────────────────────────
// Replace the two YOUR_* placeholders below, then push a version tag.
// The release.yml workflow calls  gradle :turnstile-sdk:publish  automatically.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId    = "com.github.minatox-x"     // ← replace
                artifactId = "turnstile-sdk"
                version    = System.getenv("SDK_VERSION") ?: "1.0.0"
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url  = uri(
                    "https://maven.pkg.github.com/minatox-x/turnstilekit"  // ← replace
                )
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
