# TurnstileKit — GitHub Actions CI/CD Guide

This guide explains the three workflow files and the exact steps to go from
source code to a downloadable SDK.

---

## Overview

```
Every push / PR  ──►  build.yml            builds AAR, uploads as artifact
Tag  v1.0.0      ──►  release.yml          builds AAR, creates GitHub Release
Tag  v1.0.0      ──►  publish-packages.yml publishes to GitHub Packages (optional)
```

---

## Step 0 — One-time local setup

The workflows rely on the `gradlew` wrapper script. Generate it once and
commit it:

```bash
# Inside the TurnstileKit root
gradle wrapper --gradle-version 8.4
git add gradlew gradlew.bat gradle/wrapper/
git commit -m "chore: add Gradle wrapper"
```

> If you do not have Gradle installed locally, download it from
> https://gradle.org/releases/ or use `sdk install gradle 8.4` (via SDKMAN).

Then push to GitHub:

```bash
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

The `build.yml` workflow fires immediately on the first push.

---

## Workflow 1 — `build.yml` (CI, runs on every push)

**Trigger:** any push to `main` or `develop`, or a pull request targeting `main`.

**What it does:**
1. Checks out the code
2. Installs JDK 17
3. Restores the Gradle cache (cuts ~2 min off subsequent runs)
4. Runs `./gradlew :turnstile-sdk:assembleRelease`
5. Uploads `turnstile-sdk-release.aar` as a workflow artifact

**Where to find the AAR:**
Go to **Actions → Build SDK → (click the run) → Artifacts → turnstile-sdk-aar**
and click to download. The file is kept for 30 days.

---

## Workflow 2 — `release.yml` (Release, runs on version tags)

**Trigger:** a Git tag matching `v1.2.3`.

**What it does:**
1–5. Same as `build.yml`
6. Renames the AAR to `turnstile-sdk-1.2.3.aar`
7. Creates a GitHub Release at that tag with auto-generated release notes
8. Attaches the versioned AAR to the release

### How to cut a release

```bash
# Tag the commit you want to release
git tag v1.0.0
git push origin v1.0.0
```

The workflow starts automatically. After ~2 minutes you will see:

```
Releases
  TurnstileKit v1.0.0
    📦  turnstile-sdk-1.0.0.aar   (download)
```

Anyone can download the AAR directly from the Releases page without needing
access to Actions.

---

## Workflow 3 — `publish-packages.yml` (optional, GitHub Packages Maven)

This publishes the SDK to GitHub's Maven registry so other projects can pull
it with a regular Gradle dependency instead of downloading the AAR manually.

### Extra setup required

**1. Add `maven-publish` to `turnstile-sdk/build.gradle.kts`:**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`                        // ← add this line
}

// ... your existing android { } block ...

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "com.github.YOUR_GITHUB_USERNAME"
                artifactId = "turnstile-sdk"
                version    = System.getenv("SDK_VERSION") ?: "1.0.0"
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url  = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
```

**2. Replace the placeholders** (`YOUR_GITHUB_USERNAME`, `YOUR_REPO_NAME`) in
both `publish-packages.yml` and `build.gradle.kts`.

**3. Push a version tag** — the publish job runs automatically alongside
`release.yml`.

### Consuming the published SDK

In any other Android project:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPO")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                             ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                             ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.YOUR_USERNAME:turnstile-sdk:1.0.0")
}
```

> GitHub Packages requires authentication even for public packages. Each
> consumer must supply a GitHub Personal Access Token with `read:packages`
> scope (store it in `~/.gradle/gradle.properties` as `gpr.key=ghp_xxx`).

---

## Typical release checklist

```
[ ] Bump versionName in turnstile-sdk/build.gradle.kts
[ ] git add . && git commit -m "chore: release v1.0.0"
[ ] git tag v1.0.0
[ ] git push origin main --tags
[ ] Watch Actions → all jobs green
[ ] Open Releases → verify AAR is attached
[ ] (optional) Share the release URL with consumers
```

---

## Workflow run time (approximate)

| Step | Cold (no cache) | Warm (with cache) |
|---|---|---|
| Checkout | 5 s | 5 s |
| Setup JDK | 20 s | 5 s |
| Gradle cache restore | — | 30 s |
| `assembleRelease` | 3–5 min | 45–90 s |
| Upload artifact | 5 s | 5 s |
| **Total** | **~5 min** | **~2 min** |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `gradlew: Permission denied` | The `chmod +x gradlew` step is in the workflow; if it still fails, commit `gradlew` with execute bit: `git update-index --chmod=+x gradlew` |
| `SDK location not found` | The Android SDK is pre-installed on `ubuntu-latest`; do not commit a `local.properties` file (add it to `.gitignore`) |
| Release not created | Check the job has `permissions: contents: write` |
| Publish fails with 401 | Ensure `packages: write` is in the job's `permissions` block |
| Cache miss every run | Make sure `**/*.gradle.kts` glob matches your file locations |
