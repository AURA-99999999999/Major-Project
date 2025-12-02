## Networking & Environment Configuration

The Android client reads its base URL(s) from `local.properties` at build time. Three presets are available:

| Property | Default | Purpose |
| --- | --- | --- |
| `API_BASE_URL_EMULATOR` | `http://10.0.2.2:5000/api` | Emulator loopback to host |
| `API_BASE_URL_DEVICE` | `http://192.168.0.100:5000/api` | Placeholder for your PC’s LAN IP |
| `API_BASE_URL_CUSTOM` | same as emulator by default | Any arbitrary endpoint (staging, prod, etc.) |
| `API_ENV` | `EMULATOR` | Selects which URL is injected into `BuildConfig.API_BASE_URL` (`EMULATOR`, `DEVICE`, or `CUSTOM`) |

### Example local.properties (device on Wi‑Fi)
```
API_ENV=DEVICE
API_BASE_URL_DEVICE=http://192.168.1.42:5000/api
```

### Example local.properties (custom cloud endpoint)
```
API_ENV=CUSTOM
API_BASE_URL_CUSTOM=https://api.myapp.com/v1/
```

Rebuild the app after changing any of these values so the new `BuildConfig` is generated.

