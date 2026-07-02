# Android TV Automation Test — CLAUDE.md

## Project Overview
Appium + TestNG + Allure UI test suite for the **Android TV** build of the VPN app.
Sibling project to `Android-automation-test` (the phone suite) and follows the same conventions.

- **App package:** `com.free.vpn.super.hotspot.open`
- **Launch (leanback) activity:** `com.superunlimited.androidTv.presentation.splash.TvSplashActivity` (exported entry point Appium starts)
- **Main screen activity:** `com.superunlimited.androidTv.presentation.main.TvMainActivity` (not exported — used only as `appWaitActivity`)
- **Build:** Gradle 8.7, Java 21
- **Reporting:** Allure

## What's different from the phone framework

Android TV has **no touch input** — everything is driven by the remote's **D-pad**.
Instead of `element.click()` on coordinates, the model is:

1. Find the element (`By.id(...)`) — hierarchy dumps work as usual.
2. Move focus onto it with directional keys until `focused="true"` — see `DpadNavigator`.
3. Activate with the center key (`KEYCODE_DPAD_CENTER`).

`DpadNavigator` is the core new abstraction. TV page objects extend `TvBasePage`.

## Device connection

The TV box (e.g. Google TV Streamer) has no USB-device mode, so adb is **over the network**:

```bash
adb connect <ip>:5555          # e.g. 192.168.50.207:5555
adb devices -l                 # the ip:port string is the udid
```

Prefer wired Ethernet + a DHCP reservation for stable CI runs.

## Key Commands

```bash
# Run the smoke suite (udid comes from local.properties or -Dudid=)
./gradlew smokeTest -Dudid=192.168.50.207:5555

# Serve Allure report
allure serve build/allure-results

# List connected devices
adb devices -l
```

## Configuration

Runtime config is resolved in priority order (same as the phone project):
1. System properties (`-Dkey=value`)  2. Gradle properties  3. Env vars  4. `local.properties` (git-ignored)  5. Defaults in `build.gradle`

| Key | Default | Description |
|-----|---------|-------------|
| `udid` | — (required) | TV device udid, e.g. `192.168.50.207:5555` |
| `appiumHost` | `127.0.0.1` | Appium server host |
| `appiumPort` | `4732` | Appium server port |
| `manageAppium` | `false` | Auto start/stop Appium server |
| `environment` | `dev` | `dev` or `prod` |

## Project Structure

```
src/
├── main/java/
│   ├── apps/
│   │   ├── common/CommandsADB.java        # ADB wrapper (VPN consent, install check, device alive)
│   │   └── tv/pages/
│   │       ├── Wait.java                   # Fluent waits
│   │       ├── DpadNavigator.java          # D-pad focus navigation + OK  ← core TV abstraction
│   │       ├── TvBasePage.java             # Base page object (dpad + waits + screenshots)
│   │       └── ConnectTvPage.java          # Main screen: connect / status / location / protocol
│   ├── configs/
│   │   ├── RuntimeConfig.java              # Property resolution
│   │   ├── AppiumConfig.java               # Appium server lifecycle
│   │   ├── Port.java                       # Free-port finder (managed Appium)
│   │   ├── app/                            # App definitions (TvVpnApp)
│   │   ├── devices/                        # AndroidTv / Device / Model
│   │   └── platformConfig/AndroidTvConfig  # Driver init + TV capabilities
│   └── driver/                             # TestContext, AndroidContext
└── test/java/apps/
    ├── BaseTest.java                       # setup/teardown/driver, grants VPN consent
    └── tv/regression/
        ├── ConnectionSmokeTest.java        # connect-on-Auto smoke test
        └── smoke.xml                       # TestNG suite
```

## Adding New TV Tests

1. Create a page object under `apps/tv/pages/` extending `TvBasePage`.
2. Use `dpad.focus(target, direction, maxSteps)` + `center()` to activate elements.
3. Prefer `tv`-prefixed resource-ids (`tvConnectButton`, `tvConnectStatus`, ...) — the TV build ships dedicated layouts.
4. Create the test under `apps/tv/regression/` extending `BaseTest`, add Allure annotations.
5. Register the class in the relevant suite `.xml`.

## Known TV locators (verified on device)

Main screen (`TvMainActivity`):
- `tvConnectButton` — connect button (default focus)
- `tvConnectStatus` — status text: `CONNECT` / `CONNECTED`
- `vpn_location_selector` / `tv_fastest_server` — location row ("Fastest" / country)
- `connect_mode` — protocol grid: `Auto`, `IKEv2`, `OpenVPN`
- `tvOriginalIpValue`, `tvTimeConnectedValue` — info cards (timer idle = `--:--:--`)
- `tv_settings` — settings gear

Server list screen (`TvServerListActivity`):
- `tv_title` ("Select Server Location"), `tv_sort_option`, `tv_section_title`, `tv_name`, `tv_ping`, `tv_all_server_title`

## Known Gotchas

- **Network adb drops** — box reboot or DHCP lease change loses the session; re-`adb connect`. Pin the IP.
- **VPN consent dialog** — first connect shows Android's system consent. `BaseTest` pre-grants it via `appops set <pkg> ACTIVATE_VPN allow`.
- **App must be pre-installed** — `AndroidTvConfig` assumes the APK is already on the box (install via `adb install`).
- **`TvMainActivity` is not exported** — launching it directly fails with `SecurityException: not exported`. Always launch via the exported leanback `TvSplashActivity` (`appActivity`) and wait for `TvMainActivity` (`appWaitActivity`).
- **SauceLabs** — real-device cloud has no Android TV; TV tests run on physical boxes only.
