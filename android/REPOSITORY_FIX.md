# MusicRepository Fix - Removed @Inject Annotation

## Issue

```
Unresolved reference: inject
MusicRepository.kt:14:14 Unresolved reference: inject
```

## Problem

The `MusicRepository` class still had the `@Inject` annotation and import from `javax.inject`, which were left over from the Hilt removal. Since we're using manual dependency injection now, these annotations are no longer needed.

## Solution

Removed:
- `import javax.inject.Inject`
- `@Inject` constructor annotation

Changed from:
```kotlin
import javax.inject.Inject

class MusicRepository @Inject constructor(
    private val api: MusicApi
) {
```

Changed to:
```kotlin
class MusicRepository(
    private val api: MusicApi
) {
```

Also added missing import:
- `import com.aura.music.data.mapper.toSongDtoMap` (used in repository methods)

## Result

- ✅ No more unresolved reference errors
- ✅ Repository uses regular constructor (dependencies provided by ServiceLocator)
- ✅ All mapper functions properly imported

## Verification

After this fix:
1. MusicRepository should compile successfully
2. Dependencies are provided by ServiceLocator.initialize()
3. Repository creation happens in ServiceLocator without annotations

