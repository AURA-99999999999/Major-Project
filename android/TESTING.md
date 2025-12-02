# Testing & Verification Checklist

## Automated
- `MusicRepositoryTest` (unit): verifies search success/error handling via `MockWebServer`.
- `SearchViewModelTest` (unit): validates debounced search state updates and playback preparation events.

Run from the `android` directory:
```
./gradlew test
```

## Manual (Device/Emulator)
1. **Search happy path**  
   - Launch the app, navigate to Search.  
   - Enter a valid query (e.g., "Monica").  
   - Confirm loading indicator appears, results list populates, and tapping a song opens the Player screen with playback starting.
2. **Empty/invalid query**  
   - Enter spaces or random characters.  
   - Verify debounce clears results and a “No results” message/snackbar appears.
3. **Offline mode**  
   - Disable network (airplane mode).  
   - Trigger a search to ensure the UI shows an error snackbar and state recovers when network returns.
4. **Slow server / buffering**  
   - Use `adb shell network delay` or throttle the backend.  
   - Observe that loading + playback indicators remain visible until completion and that retries succeed.
5. **Streaming fallback**  
   - From Home or Search, pick any track that lacks a `url` in the initial payload.  
   - Confirm playback still succeeds (service fetches the stream URL) and notification controls work.

Document any failures (with logs) before release.

