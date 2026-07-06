# Android TV Automation Test — CLAUDE.md

> **Maintain this file.** Whenever you change the framework — add/rename a page object, test,
> locator, config key, or convention — update the relevant section of this CLAUDE.md in the
> same change. Keep it accurate; a stale CLAUDE.md is worse than none. This is a standing rule.

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
2. Move focus onto it until `focused="true"` — see `DpadNavigator`.
3. Activate with the center key (`KEYCODE_DPAD_CENTER`).

`DpadNavigator` is the core abstraction. TV page objects extend `BasePage`.

## Device connection

The TV box (e.g. Google TV Streamer) has no USB-device mode, so adb is **over the network**:

```bash
adb connect <ip>:5555          # e.g. 192.168.50.207:5555
adb devices -l                 # the ip:port string is the udid
```

Prefer wired Ethernet + a DHCP reservation for stable CI runs. Network adb drops often — a
self-heal (`CommandsADB.ensureDeviceOnline`) runs in `@BeforeSuite`.

## Key Commands

```bash
# Run the full regression suite
./gradlew test -Dudid=192.168.50.207:5555

# Run a single test class
./gradlew test --tests "apps.tv.regression.HelpSupportTest" -Dudid=192.168.50.207:5555

# Serve Allure report
allure serve build/allure-results

# List connected devices
adb devices -l
```

Appium is **not** managed by default (`manageAppium=false`) — start it manually
(`appium --port 4732`) before a run, or pass `-DmanageAppium=true`.

## Configuration

Runtime config is resolved in priority order (same as the phone project):
1. System properties (`-Dkey=value`)  2. Gradle properties  3. Env vars  4. `local.properties` (git-ignored)  5. Defaults in `build.gradle`

| Key | Default | Description |
|-----|---------|-------------|
| `udid` | — (required) | TV device udid, e.g. `192.168.50.207:5555` |
| `tvEmail` / `tvPassword` | — | Premium account for device-code sign-in (git-ignored) |
| `serverListKey` | — | AES key to decode the server-list API |
| `appiumHost` | `127.0.0.1` | Appium server host |
| `appiumPort` | `4732` | Appium server port |
| `manageAppium` | `false` | Auto start/stop Appium server |
| `environment` | `dev` | `dev` or `prod` |

**Never commit credentials.** `tvEmail`/`tvPassword`/`serverListKey` live only in git-ignored
`local.properties`; only `local.properties.example` (blanks) is tracked.

## Project Structure

```
src/
├── main/java/
│   ├── apps/
│   │   ├── common/CommandsADB.java        # ADB wrapper (device online/reconnect, VPN consent,
│   │   │                                  #   clear data, install/remove, egress-through-tunnel)
│   │   └── tv/
│   │       ├── api/
│   │       │   ├── WebAuth.java            # device-code sign-in (RestAssured + JWE)
│   │       │   └── serverlist/            # ServerList + V7 models + ResponseDecoder + localization
│   │       └── pages/
│   │           ├── Wait.java               # Fluent waits
│   │           ├── DpadNavigator.java      # D-pad focus navigation + OK  ← core TV abstraction
│   │           ├── BasePage.java           # Base page object (dpad + waits + screenshots)
│   │           ├── Navigator.java          # screen detection + navigation  ← see below
│   │           ├── Pages.java              # enum of detectable screens
│   │           ├── MainScreenPage.java     # main: connect / status / location / protocol / gear
│   │           ├── SignInPage.java         # welcome / sign-in / sign-up (+ API device-code login)
│   │           ├── ServerListPage.java     # server list: search / sort / select (clusters)
│   │           ├── SettingsMenuPage.java   # settings popup (gear) — opens the info screens
│   │           ├── HelpSupportPage.java    # Help & Support screen
│   │           ├── PrivacyNoticePage.java  # Privacy Notice screen
│   │           ├── TermsOfServicePage.java # Terms of Service screen
│   │           ├── SignOutPage.java         # Sign Out: confirm dialog → signed-out screen → welcome
│   │           └── Protocols.java          # protocol enum (Auto / IKEv2 / OpenVPN / ...)
│   ├── configs/                           # RuntimeConfig, AppiumConfig, Port, app/devices/platformConfig
│   └── driver/                            # TestContext, AndroidContext
└── test/java/apps/
    ├── BaseTest.java                       # @BeforeSuite: device online + Appium; @BeforeClass: initDriver
    │                                       #   + grant VPN consent. NO auto sign-in (see Navigation).
    ├── ConfigRecordingListener.java        # screenshots + video attachments
    └── tv/regression/
        ├── LoginTest.java, SignUpTest.java         # start from a clean slate (own precondition)
        ├── MainScreenPageTest.java, ProtocolsTest.java, ServerListTest.java
        ├── ReinstallTest.java                       # uninstall + install APK from apps/installation
        ├── HelpSupportTest.java, PrivacyNoticeTest.java, TermsOfServiceTest.java, SignOutTest.java
        └── regression.xml                           # TestNG suite (register new classes here)
```

## Navigation (screen detection — mirrors the phone `Navigator`)

Navigation lives in `Navigator` + the `Pages` enum, **not** in blind BACK-loops.

- `detectPage()` reads `getPageSource()` **once** and matches unique resource-ids → a `Pages`
  value. Order matters (a dialog overlays main; sub-screens before the screen they open from):
  `RECONNECT_DIALOG (action_cancel_btn)` → `SIGN_UP (iv_sign_up_qr)` → `SIGN_IN (tv_sign_in_code)`
  → `WELCOME (btn_sign_in)` → `SERVER_LIST (tv_title)` → `MAIN (tvConnectButton)`
  → `INFO_SCREEN (btn_go_back || tv_settings_help_support)` → `UNKNOWN`.
- `detectCurrentPage(retry, poll)` polls, absorbing the loading/splash screen.
- `toMainScreen()` / `toWelcome()` loop "detect → one corrective step": login on WELCOME/SIGN_IN,
  cancel the reconnect dialog, BACK out of SERVER_LIST / SIGN_UP / **INFO_SCREEN**, wait on UNKNOWN.
- `MainScreenPage.navigateToMainScreen()` and `SignInPage.navigateToWelcome()` delegate to it.

**Why `INFO_SCREEN` matters:** tests that open a menu screen leave the app on a sub-screen; the
next session doesn't route back to main, so the Navigator must recognise the "Go back" screens
(Help/Privacy/Terms + the settings popup) and press BACK to recover. Always leave a test on a
clean screen (chain `.goBack()`).

## Adding New TV Tests

1. Create a page object under `apps/tv/pages/` extending `BasePage`.
2. Drive elements with `dpad.focusOnAndSelect(By)` (geometry-based, direction-free). Keep
   `verify*` assertions inside the page object; expose a fluent chain.
3. Reach the main screen with `new MainScreenPage(testContext).navigateToMainScreen()` — the
   `Navigator` handles login/dialogs/sub-screens. Don't re-implement navigation in tests.
4. Prefer `tv`-prefixed resource-ids — the TV build ships dedicated layouts.
5. Create the test under `apps/tv/regression/` extending `BaseTest`; add Allure annotations
   (`@Epic`, `@Feature`, `@Story`, `@Severity`, `@Description` with Objective + Steps).
6. Register the class in `regression.xml`.
7. **Update the relevant sections of this CLAUDE.md** (locators, structure, conventions).

## Known TV locators (verified on device)

Main screen (`TvMainActivity`):
- `tvConnectButton` (default focus), `tvConnectStatus` (`CONNECT` / `CONNECTED`)
- `vpn_location_selector` / `tv_fastest_server` — location row
- `connect_mode` — protocol grid: `Auto`, `IKEv2`, `Super`, `OpenVPN TCP`, `OpenVPN UDP`, `V2Ray`
- `tvOriginalIpValue`, `tvTimeConnectedValue` (timer idle = `--:--:--`)
- `tv_settings` — settings gear

Settings popup (opened from the gear) and its info screens:
- Menu items: `tv_settings_help_support`, `tv_settings_split_tunneling`,
  `tv_settings_privacy_notice`, `tv_settings_terms_service`, `tv_settings_sign_out`
- Help & Support (`TvHelpSupportActivity`): `tv_help_headline`, `tv_help_support_site`
  (`https://vpnsuper.com/support`), `iv_help_support_qr`, `btn_go_back`
- Privacy Notice (`TvPrivacyNoticeActivity`): `tv_privacy_headline`, `tv_privacy_notice_site`
  (`https://vpnsuper.com/privacy-notice`), `iv_privacy_notice_qr`, `btn_go_back`
- Terms of Service (`TvServiceTermsActivity`): `tv_terms_service_headline`, `terms_service_site`
  (`https://vpnsuper.com/terms-of-service` — note: **no** `tv_` prefix), `iv_terms_service_qr`, `btn_go_back`
- Sign Out: confirm dialog `tv_dialog_title` ("Are you sure you want to sign out?"),
  `action_positive_btn` (Confirm), `action_negative_btn` (Decline). Confirm → `TvSignOutActivity`
  (`sign_out_title` "You've been signed out", `sign_out_desc`, `btn_ok` "Okay") → welcome (signed out).

Welcome / sign-in (`TvWelcomeActivity` / `TvSignInActivity` / `TvSignUpActivity`):
- `tv_welcome_headline`, `btn_sign_in`, `btn_sign_up`
- `tv_sign_in_code` (rotating device code), `tv_sign_in_link`, `iv_sign_in_qr`, `btn_back`
- `iv_sign_up_qr`, `btn_signIn_instead`

Server list (`TvServerListActivity`):
- `tv_title` ("Select Server Location"), `tv_sort_option`, `tv_section_title`, `tv_name`,
  `tv_ping`, `tv_all_server_title`. Reconnect-on-protocol-change dialog: `tv_dialog_title`,
  `action_ok_btn` (Reconnect), `action_cancel_btn` (Cancel).

## Login (device-code, no manual QR)

Reaching main logs in automatically via `SignInPage.ensureSignedIn` (driven by `Navigator`).
It reads the rotating `tv_sign_in_code`, approves it through the account API (`WebAuth`, staging
backend), and waits for the main screen. Requires a **premium** `tvEmail`/`tvPassword`.

## Known Gotchas

- **Network adb drops** — box reboot or DHCP lease change loses the session; re-`adb connect`. Pin the IP.
- **VPN consent dialog** — `BaseTest` pre-grants it via `appops set <pkg> ACTIVATE_VPN allow`.
- **App must be pre-installed** — `AndroidTvConfig` assumes the APK is already on the box.
- **`TvMainActivity` is not exported** — launch via the exported leanback `TvSplashActivity`
  (`appActivity`) and wait for `TvMainActivity` (`appWaitActivity`); launching main directly → `SecurityException`.
- **Flaky `SessionNotCreatedException: instrumentation process cannot be initialized`** — occasional
  UiAutomator2/box hiccup on driver startup (box memory is tight). Re-running the class clears it; not a test-logic bug.
- **`uiautomator dump` is flaky on the box** (sometimes "Killed") — rebooting the box helps.
- **SauceLabs** — real-device cloud has no Android TV; TV tests run on physical boxes only.
