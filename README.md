# Android TV Automation Test

Appium + TestNG + Allure UI test suite for the **Android TV** build of the VPN app
(`com.free.vpn.super.hotspot.open`). Sibling of the phone suite `Android-automation-test`,
same conventions. See [CLAUDE.md](CLAUDE.md) for full architecture, locators and gotchas.

## What's different from phone

Android TV has **no touch** — everything is driven by the remote's **D-pad**:
find element → move focus until `focused="true"` → press center (OK). The core abstraction is
[`DpadNavigator`](src/main/java/apps/tv/pages/DpadNavigator.java); TV page objects extend
`BasePage`. Screen detection + navigation live in
[`Navigator`](src/main/java/apps/tv/pages/Navigator.java) (detect current screen → step to target).

## Prerequisites

- JDK 21, Android SDK platform-tools, Appium 2/3 with the UiAutomator2 driver.
- An Android TV device reachable over network adb.

```bash
adb connect <ip>:5555        # e.g. 192.168.50.207:5555
adb devices -l               # the ip:port string is the udid
```

## Setup

Copy `local.properties.example` to `local.properties` and fill it in:

```properties
udid=192.168.50.207:5555
tvEmail=<premium account email>
tvPassword=<password>
serverListKey=<server-list AES key>
environment=dev          # dev | prod
```

`local.properties` is git-ignored — **credentials never get committed**.
The TV welcome gate requires a **premium** account (in the selected environment).

## Sign-in (device-code, no manual QR)

Reaching the main screen signs in automatically via the account API: reads the rotating on-screen
device code, approves it through `verify` → `userToken` → `authorize`
([`WebAuth`](src/main/java/apps/tv/api/WebAuth.java)). Environment is a first-class config —
`WebAuth.forEnvironment(testContext.getEnvironment())` selects dev/prod base URL + RSA key;
`environment=dev|prod` switches both login and the server-list endpoint.

## Running

```bash
# Full regression suite
./gradlew test -Dudid=192.168.50.207:5555

# A single class
./gradlew test --tests "apps.tv.regression.SplitTunnelingTest" -Dudid=192.168.50.207:5555

# Serve the Allure report (screenshots + video attached per run)
allure serve build/allure-results
```

Appium is started manually (`appium --port 4732`) unless you pass `-DmanageAppium=true`.

## Test coverage

Suite: [`regression.xml`](src/test/java/apps/tv/regression/regression.xml) — 11 classes, ~21 cases.

| Area | Class | What it checks |
|---|---|---|
| Installation | `ReinstallTest` | Reinstall the app from a local APK |
| Login | `SignInTest` | Welcome + sign-in (QR/code) screens, device-code login via API |
| Sign up | `SignUpTest` | Sign-up screen, "Sign In Instead" redirect |
| Main screen | `MainScreenPageTest` | All main-screen elements; debug menu (hold Connect ~3s) |
| Protocols | `ProtocolsTest` | Connect on each available protocol + real egress country |
| Server list | `ServerListTest` | Validation, sort, search+connect, select server + real egress |
| Menu → Help & Support | `HelpSupportTest` | Screen redirect: headline, URL, QR, Go back |
| Menu → Privacy Notice | `PrivacyNoticeTest` | Same for Privacy Notice |
| Menu → Terms of Service | `TermsOfServiceTest` | Same for Terms of Service |
| Menu → Sign Out | `SignOutTest` | Decline keeps signed in; Confirm → signed-out → welcome |
| Menu → Split Tunneling | `SplitTunnelingTest` | Screen + list; exclude one app (only it changes, persists); **real per-app traffic re-route** |

**Beyond UI — real traffic checks:** egress country is fetched from the device through the tunnel
(ip-api.com) and compared to the selected server. Split tunneling is verified per-app by measuring a
specific app's egress from its own uid (`run-as io.appium.settings`): real country (VPN off) →
server country (connected, included) → real country again (excluded + reconnect).
