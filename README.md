# Volta VPN — Free · Fast

Free Android VPN supported by one rewarded ad a day (AdMob). The pass resets at local midnight.

## Layout
| Path | What |
|---|---|
| `app/` | The app (Kotlin + Jetpack Compose): screens, daily pass, ads, server list |
| `vpnlib/` | OpenVPN engine: upstream [ics-openvpn](https://github.com/schwabe/ics-openvpn) in `vpnlib/ics`, built as a library (OpenVPN 2 only, no upstream UI) |
| `backend/` | Builds the signed server list from VPN Gate, with live leak/geo/MITM checks |
| `.github/workflows/servers.yml` | Runs the backend every 4 h and publishes to the `data` branch |

## Build
Requirements: Android Studio, plus **NDK (Side by side) 30.0.16248370** and **CMake** installed from SDK Manager → SDK Tools.

```
gradlew :app:assembleDebug
```

## Server list
1. `cd backend && pip install cryptography && python make_keys.py` (done once; already embedded in `app/build.gradle.kts`).
2. Push the repo to GitHub and add `backend/keys/private.pem` content as the secret `SERVER_LIST_KEY`. The workflow publishes to the `data` branch.
3. `SERVER_LIST_URLS` in `app/build.gradle.kts` lists the mirrors (raw.githubusercontent.com, jsDelivr).

Without a URL the app reads VPN Gate's public API directly (unverified, and blocked in some countries).

## Before publishing
- Real AdMob IDs are set for release builds only; debug builds keep Google test IDs.
- In AdMob → Blocking controls, set the max ad content rating to **G** and block sensitive categories.
- Turn on mediation so the highest-paying network fills each request.
- Play Console: fill in the VpnService declaration and Data safety, and publish a privacy policy.
- GPLv2: ics-openvpn is GPL, so this app's source must be published.
