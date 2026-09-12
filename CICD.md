# TurnstileKit — CI/CD Guide

Everything builds entirely in GitHub Actions.  
**No local Gradle installation, no `gradlew` generation, nothing to set up locally.**

---

## How the two workflows fit together

```
Every push / PR  ──►  build.yml     builds AAR → uploads as artifact
Tag  v1.0.0      ──►  release.yml   builds AAR → GitHub Release + GitHub Packages
```

---

## Step 1 — Two things to replace in `turnstile-sdk/build.gradle.kts`

Open the file and find the Maven block at the bottom. Replace the two placeholders:

```kotlin
groupId = "com.github.YOUR_GITHUB_USERNAME"     // ← your GitHub username
url     = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME")  // ← same + repo name
```

Commit and push to `main`.

---

## Step 2 — Push to GitHub

```bash
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

The `build.yml` workflow fires automatically.  
After ~2 minutes go to **Actions → Build SDK → Artifacts → turnstile-sdk-aar** to download the AAR.

---

## Step 3 — Cut a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

That is the only command you run. The `release.yml` workflow will:

1. Install Gradle 8.4 on the runner (no local setup needed)
2. Build `turnstile-sdk-release.aar`
3. Rename it to `turnstile-sdk-1.0.0.aar`
4. Create a **GitHub Release** at that tag with auto-generated notes and the AAR attached
5. Publish the AAR to **GitHub Packages** (Maven) so it can be used as a Gradle dependency

---

## Using the published SDK in another project

Once published via GitHub Packages, any Android project can depend on it:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPO")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull  ?: System.getenv("GITHUB_TOKEN")
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

Store a GitHub Personal Access Token (`read:packages` scope) in
`~/.gradle/gradle.properties`:
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_xxxxxxxxxxxx
```

---

## Release checklist

```
[ ] Replace the two YOUR_* placeholders in turnstile-sdk/build.gradle.kts
[ ] git add . && git commit -m "chore: release v1.0.0"
[ ] git tag v1.0.0
[ ] git push origin main --tags
[ ] Actions → Release SDK → all steps green (~2 min)
[ ] Releases page → TurnstileKit v1.0.0 → AAR is attached ✓
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `release.yml` fails at "Publish" with 401 | Make sure the job has `permissions: packages: write` (already set) |
| `release.yml` fails at "Create GitHub Release" with 403 | Make sure the job has `permissions: contents: write` (already set) |
| Build fails with "SDK location not found" | Do **not** commit `local.properties` — add it to `.gitignore` |
| Gradle cache miss on every run | `gradle/actions/setup-gradle@v3` handles caching automatically; no extra config needed |
| Tag pushed but workflow didn't trigger | Confirm the tag matches `v[0-9]+.[0-9]+.[0-9]+` (e.g. `v1.0.0`, not `1.0.0`) |
