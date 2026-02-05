# Build Workflow Fix Documentation

## Issue Description

The GitHub Actions workflow `build-release.yml` was failing during the "Get version info" step with exit code 1.

### Root Cause

The workflow was attempting to extract version information from `app/build.gradle.kts` using `grep` with the following patterns:

```bash
VERSION_NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
VERSION_CODE=$(grep -oP 'versionCode = \K\d+' app/build.gradle.kts)
```

However, the `build.gradle.kts` file uses dynamic function calls to determine version information:

```kotlin
versionCode = getGitCommitCount()
versionName = getVersionName()
```

Since these are function calls rather than static string assignments, the `grep` patterns failed to find any matches, causing the workflow to fail.

## Solution

### 1. Added Gradle Task for Version Info

A new Gradle task `printVersionInfo` was added to `app/build.gradle.kts`:

```kotlin
// Task to print version information for CI/CD
tasks.register("printVersionInfo") {
    doLast {
        println("VERSION_NAME=${android.defaultConfig.versionName}")
        println("VERSION_CODE=${android.defaultConfig.versionCode}")
    }
}
```

This task evaluates the Kotlin functions at runtime and outputs the actual version values in a format that's easy to parse.

### 2. Updated Workflow Script

The workflow step was updated to use the new Gradle task:

```yaml
- name: Get version info
  id: version
  run: |
    # Extract version info from Gradle task output
    VERSION_INFO=$(./gradlew -q printVersionInfo)
    VERSION_NAME=$(echo "$VERSION_INFO" | grep "VERSION_NAME=" | cut -d'=' -f2)
    VERSION_CODE=$(echo "$VERSION_INFO" | grep "VERSION_CODE=" | cut -d'=' -f2)
    echo "version_name=$VERSION_NAME" >> $GITHUB_OUTPUT
    echo "version_code=$VERSION_CODE" >> $GITHUB_OUTPUT
    echo "date=$(date +'%Y%m%d_%H%M%S')" >> $GITHUB_OUTPUT
```

## Benefits of This Approach

1. **Dynamic Version Resolution**: The Gradle task properly evaluates the Kotlin functions, ensuring the correct version information is extracted regardless of how it's computed.

2. **Maintainability**: If the version calculation logic changes in the future, the workflow doesn't need to be updated since it relies on the evaluated output rather than parsing the source code.

3. **Consistency**: The version information comes from the same source that Gradle uses during the build, ensuring perfect consistency between the APK version and the workflow's version.

4. **Simplicity**: The solution is straightforward and doesn't require complex parsing or external tools.

## Testing

The fix was tested locally and confirmed to work correctly:

```bash
$ ./gradlew -q printVersionInfo
VERSION_NAME=1.0.2
VERSION_CODE=2
```

## Files Modified

1. `app/build.gradle.kts` - Added `printVersionInfo` task
2. `.github/workflows/build-release.yml` - Updated version extraction logic

## Date

Fixed: February 5, 2026
