# Volta VPN — Free · Fast

Free Android VPN supported by one rewarded ad a day (AdMob). The pass resets at local midnight.

## Layout
| Path | What |
|---|---|
| `app/` | The app (Kotlin + Jetpack Compose): screens, daily pass, ads, server list |
| `vpnlib/` | OpenVPN engine: upstream [ics-openvpn](https://github.com/schwabe/ics-openvpn) in `vpnlib/ics`, built as a library (OpenVPN 2 only, no upstream UI) |
| `backend/` | Builds the signed server list from VPN Gate, with live leak/geo/MITM checks |
| `.github/workflows/servers.yml` | Runs the backend hourly and publishes to the `data` branch |

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

## Server tiers
| Tier | Source | Trust |
|---|---|---|
| VOLTA | Your own no-log servers (`server/install.sh`), listed via the `OWN_SERVERS` secret | High: you run them |
| NO-LOG | Riseup gateways | Run by a nonprofit whose stated policy is no logs |
| TESTED | VPN Gate servers that passed the hourly leak / geo / TLS checks | Hides you from websites; operators may log |
| UNTESTED | VPN Gate servers not tested this hour | Same as above, untested |

### Add a free private server (Oracle Cloud Always Free)
1. Create an Always Free Ubuntu VM, open UDP 1194 in its VCN Security List.
2. On the VM: `curl -fsSL https://raw.githubusercontent.com/OneXAPPS/volta-vpn/main/server/install.sh | sudo bash`
3. Put the printed JSON entry inside a list in the GitHub secret `OWN_SERVERS`, e.g. `[{"cc":"DE",...}]`.
