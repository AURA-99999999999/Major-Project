# SplashScreen Dependency Fix

## Issue

```
Unresolved reference: splashscreen
MainActivity.kt:15:22 Unresolved reference: splashscreen
```

## Solution

Added the missing SplashScreen library dependency.

## Change Made

**File:** `app/build.gradle.kts`

Added dependency:
```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

This provides the `androidx.core.splashscreen.SplashScreen` API used in `MainActivity.kt`.

## After Fix

The SplashScreen API will be available and the build error will be resolved.

## Verify

After syncing Gradle:
1. The unresolved reference error should disappear
2. `MainActivity.kt` should compile successfully
3. Splash screen functionality will work correctly

