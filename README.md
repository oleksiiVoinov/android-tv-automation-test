# Android TV Automation Test

Appium + TestNG + Allure UI test suite for the **Android TV** build of the VPN app
(`com.free.vpn.super.hotspot.open`). Sibling of the phone suite `Android-automation-test`,
same conventions. See [CLAUDE.md](CLAUDE.md) for full architecture and locator notes.

## What's different from phone

Android TV has **no touch** — everything is driven by the remote's **D-pad**:
find element → move focus with directional keys until `focused="true"` → press center (OK).
The core abstraction is [`DpadNavigator`](src/main/java/apps/tv/pages/DpadNavigator.java);
TV page objects extend `TvBasePage`.

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
tvEmail=<premium staging account email>
tvPassword=<password>
```

`local.properties` is git-ignored — **credentials never get committed**.
The TV welcome gate requires a **premium** account.

## Sign-in precondition

A clean-slate run wipes app data, so the app starts on the welcome screen.
The precondition then signs in via the account API (device-code approve, no manual QR):
`verify` → `userToken` → `authorize` — see
[`TvWebAuth`](src/main/java/apps/tv/api/TvWebAuth.java).

## Running

```bash
# Smoke suite (udid can also come from local.properties)
./gradlew smokeTest -Dudid=192.168.50.207:5555

# Serve the Allure report
allure serve build/allure-results
```
