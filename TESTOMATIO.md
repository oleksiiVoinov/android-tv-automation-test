# Testomat.io integration — Android TV

Test-case management for the **Android TV** suite lives in the same Testomat.io project as the phone
suite, in its own subtree.

- project: **`android-f0d8b`**
- automation root suite: **🤖 `TV Regression (Auto and Manual)`** = **`1882fcfb`** (inside `Master` = `3200ef5c`)
- manual TV checks: **`Manual`** = **`5198b93f`**, a child of that folder — the sync never touches it
- link: https://app.testomat.io/projects/android-f0d8b/suite/1882fcfb

The phone project (`Android-automation-test`) has the very same integration under
`Regression (Auto and Manual)` = `fd90d89e`; the Java code here is a copy of it with the package
renamed to `apps.tv.api.testomatio`, so fixes port over one-to-one.

---

## 1. What was exported

**24 test cases** — every `@Test` of the 11 classes listed in
`src/test/java/apps/tv/regression/regression.xml`:

- 19 plain cases,
- 5 per-protocol cases for the single data-driven method `ProtocolsTest#checkProtocol`.

Not exported (on purpose): `apps/tv/api/WebAuthTryTest`, `apps/tv/temp/SampleTest` (not in the suite
XML) and `ServerListTest#selectServer` (commented out in the code).

The tree mirrors the Allure **behaviors** report one to one — `@Feature` → folder, `@Story` → file
suite, and a class without a `@Feature` gets its story promoted to feature level:

```
Master
└── 🤖 TV Regression (Auto and Manual)          1882fcfb
    ├── Manual                                  5198b93f   (manual checks, not synced)
    ├── 1. Installation                         78167878
    │   └── 1. Reinstall app                    a48427cc   1 case
    ├── 1. Sign up                              b500a3d4
    │   └── 1. Sign up                          4fd0a8e6   2 cases
    ├── 1. Login                                e4e4b333
    │   └── 1. Login                            81f4b088   3 cases
    ├── 4. Settings menu                        f71c3f9e
    │   ├── Help & Support                      770df00f   1 case
    │   ├── Privacy Notice                      1266bd0b   1 case
    │   ├── Terms of Service                    5a2361ca   1 case
    │   ├── Split Tunneling                     2ee6c102   3 cases
    │   └── Sign Out                            d3a9c900   2 cases
    ├── 2. Main screen  (file)                  c8c72d8c   2 cases   — MainScreenPageTest, no @Feature
    ├── 3. Protocols    (file)                  d0f721e4   5 cases   — ProtocolsTest, no @Feature
    └── 4. Server List  (file)                  f2480833   3 cases   — ServerListTest, no @Feature
```

Every case carries, in English: a header line with `Class#method` and the severity, **Preconditions**
(what `BaseTest` / `@BeforeClass` set up), **Steps** (from `@Description`) and **Expected result**
written from what the code actually asserts — exact expected texts, timeouts and the assertion
messages. Priority comes from `@Severity`: BLOCKER/CRITICAL → high, NORMAL → normal,
MINOR/TRIVIAL → low.

Two honesty notes are written into the cases themselves:

- `ServerListTest#sortServer` asserts only the **sort label**, not that the rows were reordered;
- `MainScreenPageTest#validationDebug` asserts nothing at all if `environment` is neither `dev` nor
  `prod` — the test body only handles those two.

### Protocols (data-driven)

`ProtocolsTest#checkProtocol` gets its values from the **on-screen grid** (`getProtocols()` minus
`Auto`), not from the enum. Exported as one case per protocol the TV build actually shows:

| Data-provider value (enum constant) | Case |
|---|---|
| `IKEv2` | `ebb66b83` |
| `Super` | `ecee139c` |
| `OpenVPNTCP` | `84b1208f` |
| `OpenVPNUDP` | `00d35aac` |
| `V2Ray` | `794350eb` |

The listener looks a row up by `String.valueOf(protocol)`, i.e. the **enum constant name**
(`OpenVPNTCP`), not the on-screen label (`OpenVPN TCP`). `OpenVPN` and `supx_v22` exist in the enum
but are not in the grid, so they have no case; if a build starts exposing them the console will say
`[testomatio] no case for … with value "…"` — that is the signal to add one.

---

## 2. The mapping is the only link

`src/main/resources/testomatio-mapping.json` — **no Testomat.io id is ever written into Java code**:

```json
{
  "project": "android-f0d8b",
  "root_suite": "1882fcfb",
  "tests": {
    "apps.tv.regression.SignOutTest#signOut/0": {
      "id": "87547ef0", "title": "…", "suite_id": "d3a9c900",
      "suite_title": "Sign Out", "feature": "4. Settings menu"
    }
  },
  "parametrized": {
    "apps.tv.regression.ProtocolsTest#checkProtocol/1": {
      "V2Ray": { "id": "794350eb", "title": "…", "suite_id": "d0f721e4", "suite_title": "3. Protocols" }
    }
  }
}
```

The key is `<fully.qualified.Class>#<method>/<parameter count>`. An unmapped test does **not** break
the run: it is reported by title and logged as

```
[testomatio] not mapped: apps.tv.regression.SomeNewTest#someMethod — reported by title "…"
```

Requests carry `create: false`, so a run can never create cases by itself — the tree is managed
explicitly. That log line is the signal to run the sync (see §5).

---

## 3. Running with reporting

Reporting is **off** by default; with `testomatio=false` not a single request is sent.

There is **no Jenkins job for Android TV** — the suite is started from a terminal on the machine that
sees the box.

**One-time setup.** Everything lives in git-ignored `local.properties`, so the key never lands on a
command line and the switch is one line to flip:

```properties
# true -> a run is created and every result is reported; false -> nothing is sent
testomatio=false
# close the automated half when the suite ends (safe here: the TV suite runs in one JVM)
testomatioFinishRun=true
testomatioApiKey=tstmt_…
```

`local.properties` beats the built-in default but loses to `-Dtestomatio=…` on the command line, so a
single run can always be forced either way without editing the file.

**Every run** — the box online, an Appium server, one gradle command:

```bash
adb connect 192.168.50.207:5555 && adb devices -l
appium --port 4732        # or add -DmanageAppium=true to the command below

./gradlew regressionTest -Dudid=192.168.50.207:5555            # uses testomatio from local.properties
./gradlew regressionTest -Dudid=192.168.50.207:5555 -Dtestomatio=true    # force reporting on
./gradlew regressionTest -Dudid=192.168.50.207:5555 -Dtestomatio=false   # force it off
```

With the switch on, that single command creates the run (mixed, pre-filled with the whole TV folder),
reports every test into it and closes the **automated** half when the suite ends. The run stays open
for the review — a human presses **Finish Run** in the UI. `testomatioFinishRun=true` is safe here
because the TV suite runs in one JVM (in the phone project two device suites share a run, so there it
must not be used).

Expected console lines:

```
[testomatio] mapping loaded: 19 tests + 5 parametrized cases, project android-f0d8b
[testomatio] run pre-filled from suites [1882fcfb]: 24 cases, manual checks included
[testomatio] run created: https://app.testomat.io/projects/android-f0d8b/runs/…
```

`falling back to the reporter API` instead of the second line means the project key has no v2 API
rights — the run is then created without the manual folder.

**Splitting it up** (e.g. to run a single class into an existing run):

```bash
RUN_ID=$(./gradlew -q testomatioCreateRun -DtestomatioRunTitle="Android TV regression" \
  | grep TESTOMATIO_RUN_ID= | cut -d= -f2)
./gradlew regressionTest -Dudid=192.168.50.207:5555 -Dtestomatio=true -DtestomatioRunId=$RUN_ID
./gradlew -q testomatioFinishRun -DtestomatioRunId=$RUN_ID   # closes the automated half
```

### All options

| Option | Env | Default | Description |
|---|---|---|---|
| `testomatio` | `TESTOMATIO_ENABLED` | `false` | master on/off switch |
| `testomatioApiKey` | `TESTOMATIO_API_KEY`, `TESTOMATIO` | — | project API key (`tstmt_…`) |
| `testomatioRunId` | `TESTOMATIO_RUN_ID` | — | report into an existing run instead of creating one |
| `testomatioRunTitle` | `TESTOMATIO_TITLE` | `<suite> — dd.MM.yyyy HH:mm` | run title |
| `testomatioRunGroup` | `TESTOMATIO_RUNGROUP_TITLE` | — | run group (ignored for a pre-filled run) |
| `testomatioEnv` | `TESTOMATIO_ENV` | value of `environment` | environment label of the run |
| `testomatioRunKind` | `TESTOMATIO_RUN_KIND` | `mixed` | `mixed`/`manual` → results can be re-checked by hand; `automated` → read-only |
| `testomatioRunSuites` | `TESTOMATIO_RUN_SUITES` | `root_suite` (`1882fcfb`) | suites the run is pre-filled with; `none` → only reported tests |
| `testomatioFinishRun` | `TESTOMATIO_FINISH_RUN` | `false` | **false → the run stays open**, a human closes it |
| `testomatioRunFile` | `TESTOMATIO_RUN_FILE` | — | file used to share one run between JVMs |
| `testomatioBaseUrl` | `TESTOMATIO_URL` | `https://app.testomat.io` | instance URL |

Every option is resolved the same way as the rest of the framework — `-Dkey=value` → gradle properties
→ env → **`local.properties`** → the default in `build.gradle`. So any row of this table can be pinned
in `local.properties` and overridden per run with `-D…`; `local.properties.example` ships the
Testomat.io block pre-commented.

Where to get the key: Testomat.io → project → **Settings → Project → Project Reporting API key**.
Keep it in git-ignored `local.properties` (`testomatioApiKey=tstmt_…`) or in the environment.

---

## 4. Run lifecycle — two halves, on purpose

The run is created as **`mixed`** so a human can re-check a broken autotest and set the real status
(open the run → **Continue** → PASSED / FAILED / SKIPPED + a result message). An `automated` run is
read-only in the UI.

A mixed run has **two halves**, and Testomat.io keeps its status at `running` until both are closed:

| Half | Field in the run JSON | Who closes it |
|---|---|---|
| manual | `manual-part-finished` | a human: **Finish Run** in the UI |
| automated | `automated-part-finished` | only the Reporter API: `PUT /api/reporter/{uid}` → our `testomatioFinishRun` |

If nobody closes the automated half, the run hangs at *"Running · NOT FINISHED · automated part not
finished"* and **no UI button can fix it** — that is why step 3 above exists. Since the TV suite runs
in a single JVM, `-DtestomatioFinishRun=true` is also safe here: the automated half then closes when
the suite ends and the human only presses **Finish Run**.

### Why the run is created through the v2 API

`POST /api/reporter` accepts no suites, so a run created by the reporter would contain **only** the
automated results — no `Manual` folder. Therefore `TestomatioClient.createRunWithSuites()` uses the
v2 API:

```
POST   /api/v2/{project}/sessions          → {"data": {"hash": "…"}}   (write session)
POST   /api/v2/{project}/runs              → {title, env, kind, suite_ids: ["1882fcfb"]}
DELETE /api/v2/{project}/sessions/{hash}
```

Same project key for both APIs: the Reporter API takes it as the `api_key` query parameter, the v2 API
as an `Authorization: Bearer` header plus `X-Session-Hash` on every mutation. If the v2 call fails the
code falls back to `POST /api/reporter` and logs it — a regression is never blocked by Testomat.io.

---

## 5. When new TV tests are added

1. create the case in the right file suite (by hand or via the Testomat.io MCP),
2. add an entry to `src/main/resources/testomatio-mapping.json`.

The `testomatio-sync` skill (installed in Oleksii's Claude profile) does both and understands this
project too — point it at `regression.xml` and root suite `1882fcfb`.

---

## 6. What the integration consists of

| File | Role |
|---|---|
| `src/main/java/apps/tv/api/testomatio/TestomatioConfig.java` | resolves every option through `RuntimeConfig` |
| `…/TestomatioClient.java` | HTTP client: Reporter API + the two v2 API calls |
| `…/TestomatioMapping.java` | loads `testomatio-mapping.json`, looks test ids up |
| `…/TestomatioReporter.java` | run lifecycle, sharing a run between JVMs, sending results |
| `…/TestomatioRunCli.java` | CLI behind `testomatioCreateRun` / `testomatioFinishRun` |
| `src/test/java/apps/listeners/TestomatioListener.java` | TestNG listener (`ISuiteListener` + `ITestListener`) |
| `src/main/resources/testomatio-mapping.json` | `Class#method → test id` map (19 plain + 5 per-value) |
| `build.gradle` | `testomatio*` options, listener registration, two tasks, `org.json` dependency |

The listener is registered for **all** Test tasks, but with `testomatio=false` it does nothing — not a
single network call. Any API failure is logged and ignored: reporting must never fail a test run.
