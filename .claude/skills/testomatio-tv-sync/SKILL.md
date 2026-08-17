---
name: testomatio-tv-sync
description: Keep Testomat.io (project android-f0d8b, TV subtree) in sync with the automated tests of the Android TV framework (android-tv-automation-test) — create, move and retire test cases and update src/main/resources/testomatio-mapping.json after the code changed. Use this skill whenever the user says things like "я добавил TV тесты — залей их в тестомато", "обнови тестомато для тв", "синкани тв тесты", "перенеси кейс тв", "почему в тестомато нет нового тв теста", "update Testomat.io for Android TV", "sync the TV tests", "add new TV tests to Testomat.io", "the TV tree does not match the Allure report". Trigger it also right after writing or renaming any @Test in apps/tv/regression — a new method is invisible to Testomat.io until it is added here.
---

# Testomat.io ↔ Android TV framework sync

Testomat.io project: **`android-f0d8b`** · TV automation root suite: **`1882fcfb`**
(🤖 `TV Regression (Auto and Manual)`, inside `Master` = `3200ef5c`).
Always address suites and cases by **id** — titles and positions in the tree change and mean nothing
to the integration.

This is the **TV** twin of the `testomatio-sync` skill (which owns the phone project
`Android-automation-test` and its root suite `fd90d89e`). Same rules, different repository, different
root. Never mix them: a TV case never goes into `fd90d89e` and vice versa.

Everything about the integration itself (options, run lifecycle, the v2 pre-filled run) is documented
in **`TESTOMATIO.md`** in the repository root — read it first if the task touches reporting rather than
the test tree.

**Never touch manual work.** `Manual` = **`5198b93f`** inside the TV root is QA's own subtree: never
rename it, never delete cases in it, never report it as stale. This skill owns only the suites that
correspond to a `@Feature` / `@Story` in the code.

---

## Step 0 — Load context

1. Read `TESTOMATIO.md` (integration contract, mapping format, how a run is created).
2. Read `CLAUDE.md` (framework structure, D-pad conventions, locators).
3. Load the MCP tools you will need in **one** call:

```
ToolSearch: select:mcp__remote-devices__testomatio__tests_create,mcp__remote-devices__testomatio__tests_update,mcp__remote-devices__testomatio__tests_delete,mcp__remote-devices__testomatio__suites_create,mcp__remote-devices__testomatio__suites_update,mcp__remote-devices__testomatio__suites_get
```

`mcp__remote-devices__testomatio__system_ping` confirms which project the MCP server points at — it
must answer `android-f0d8b`.

---

## Step 1 — Run the diff

The diff script ships with this skill as `scripts/testomatio_tv_diff.py`. Run it **with the repository
root as the working directory** — it resolves `src/test/java`, `src/main/java` and the mapping relative
to cwd:

```bash
cd /path/to/android-tv-automation-test

# copy bundled with this skill (works when the skill is installed in the profile)
python3 "<dir of this SKILL.md>/scripts/testomatio_tv_diff.py"

# the repository keeps the same copy — use whichever exists:
python3 .claude/skills/testomatio-tv-sync/scripts/testomatio_tv_diff.py

# --json when you want to process the result programmatically
```

In a Cowork session the repository is only visible from `device_bash` (a separate Linux VM where the
installed skill does **not** exist), so there run the repository copy.

If both copies exist and differ, the bundled one is the source of truth — copy it over the repo copy so
the team runs the same version.

The script parses `src/test/java/apps/tv/regression/regression.xml`, applies the Allure mapping rules
below and compares the result with `src/main/resources/testomatio-mapping.json`.

| Section | Meaning | What to do |
|---|---|---|
| `NEW` | `@Test` method with no case in Testomat.io | Step 3 |
| `REMOVED` | mapping entry whose method is gone | Step 4 |
| `COMMENTED_OUT` | case exists, but the `@Test` is commented out in the code | Step 4 — it can never run |
| `MOVED` | `@Feature` / `@Story` changed | Step 5 |
| `DATA_VALUES` | the per-protocol case set drifted | Step 6 |
| `ENUM_DRIFT` | `Protocols` enum has a constant that is neither in the known grid nor mapped | Step 6 |
| `NO_STORY` | test without `@Story` | Step 2 |
| `TITLE_REVIEW` | objective no longer resembles the case title | soft signal, judge by hand |

It also warns about classes listed in `regression.xml` but missing on disk, and about the same
`<class>` listed twice (TestNG would run it twice — a suite-config mistake, tell the user).

If every section is empty — say so and stop. Do not "refresh" cases that did not change.

---

## Step 2 — Allure mapping rules (the contract)

The Testomat.io tree must stay identical to the Allure `behaviors` tree of the TV suite:

- `@Feature` → **folder** suite, `@Story` → **file** suite, cases live inside the file suite;
- **no `@Feature`, only `@Story`** → Allure promotes the story to feature level, so in Testomat.io it
  is a **file suite directly under the TV root** `1882fcfb`. Existing ones: `2. Main screen`
  (`c8c72d8c`), `3. Protocols` (`d0f721e4`), `4. Server List` (`f2480833`);
- **no `@Story`** → Allure puts the test straight under the feature, which Testomat.io cannot express.
  Fix it in the code: add a `@Story`. Ask the user before editing test files, then keep the suite name
  identical to the new `@Story` value;
- `@Feature` / `@Story` on `@BeforeClass` / `@BeforeMethod` is Allure grouping noise for fixtures —
  **ignore it** for the tree; what the fixture does belongs in the case's *Preconditions*
  (`ProtocolsTest` and `ServerListTest` both annotate their `precondition()` — do not create suites
  from that);
- `@Epic("Android TV")` is on every class and is **not** part of the behaviors tree — ignore it.

**API constraint:** a test can only be created in a suite with `file_type: "file"`. Creating it in a
`folder` returns `422 "Can't save test into a folder suite"`. A new story needs
`suites_create` with `file_type: "file"` and `parent_id` of the feature folder.

### Current tree (ids)

```
🤖 TV Regression (Auto and Manual)  1882fcfb
├── Manual                          5198b93f   ← QA's, never touch
├── 1. Installation                 78167878 → 1. Reinstall app  a48427cc
├── 1. Sign up                      b500a3d4 → 1. Sign up        4fd0a8e6
├── 1. Login                        e4e4b333 → 1. Login          81f4b088
├── 4. Settings menu                f71c3f9e → Help & Support     770df00f
│                                              Privacy Notice     1266bd0b
│                                              Terms of Service   5a2361ca
│                                              Split Tunneling    2ee6c102
│                                              Sign Out           d3a9c900
├── 2. Main screen  (file)          c8c72d8c
├── 3. Protocols    (file)          d0f721e4
└── 4. Server List  (file)          f2480833
```

---

## Step 3 — Create cases for NEW tests

For every entry in `NEW`:

1. **Find the target suite.** Look up any existing case with the same `feature` + `story` in
   `testomatio-mapping.json` and reuse its `suite_id`. If the story does not exist yet, create it
   (`file_type: "file"`, parent = the feature folder; create the feature folder too if it is new —
   `file_type: "folder"`, parent `1882fcfb`).
2. **Read the test.** Open the test method **and the page-object methods it calls**
   (`src/main/java/apps/tv/pages/…`). You write the *Expected result* yourself, from what the code
   actually asserts — exact expected texts, resource ids, timeouts, assertion messages. Never invent
   an assertion that is not there; if one is commented out, say so instead of pretending it runs. The
   existing TV cases show the level of detail expected (e.g. "the headline text equals exactly
   `Help and support`", "status becomes `CONNECTED` within 45 s and the timer is no longer `--:--:--`").
3. **Create the case** with `tests_create`:
   - `title` — the `Objective:` line of `@Description`, capitalised, no trailing dot. If the objective
     is generic and would collide with siblings, use the `@Test(description=…)` attribute instead.
   - `suite_id` — from step 1.
   - `priority` — from `@Severity`: BLOCKER/CRITICAL → `high`, NORMAL → `normal`,
     MINOR/TRIVIAL → `low`, missing → `normal`.
   - `description` — exactly this shape, in **English**:

```
> 🤖 **Automated** · `Class#method` · `regression.xml` → *Android TV box* · severity: BLOCKER

**Preconditions**

1. TV box online over network adb, app pre-installed; `BaseTest` opens the Appium session (leanback `TvSplashActivity` → `TvMainActivity`) and pre-grants the VPN consent
2. <what the class @BeforeClass does, in plain language — e.g. "wipes app data → starts signed out", "opens the server list", "enables the 'all apps' master">

**Steps**

1. <steps from the Steps: section of @Description, capitalised; derive them from the body if absent>

**Expected result**

1. <your analysis of the assertions>
```

   - the app is **not signed in by the fixture**: reaching the main screen (and logging in with the
     premium `tvEmail` / `tvPassword` through the device-code API) happens inside
     `navigateToMainScreen()` — say that in the preconditions when the test relies on it;
   - a test that needs a premium account or VIP servers must say so;
   - data-driven method → one case per value (Step 6) with a `**Data row:**` line under the header;
   - `enabled = false` in the code → append ` · ⚠️ disabled in code` to the header line;
   - if the test asserts less than its name promises (or nothing at all in some branch), write that
     down as a ⚠️ line — the existing `sortServer` and `validationDebug` cases do exactly that.
4. **Record the returned `data.id`** — it goes into the mapping.

With more than ~20 new cases, split them across parallel subagents (Agent tool, `general-purpose`),
one batch per agent, each writing results to a JSON file, then merge.

---

## Step 4 — Handle REMOVED cases

Check the git history before acting:

- **renamed** → keep the case, change the mapping key (and `tests_update` the title if the objective
  changed);
- **moved to another class** → same, update the key; `tests_update` `suite_id` if the story changed;
- **genuinely deleted** → **ask the user** whether to delete the case (`tests_delete`) or keep it as
  manual/deprecated. Never silently delete: cases carry run history and links.

**Commented-out tests are a separate story.** A `@Test` inside a `/* */` block still exists in the
file, it is only switched off, so the script reports it as `COMMENTED_OUT`, never as `REMOVED`, and it
is never reported as `NEW` either (`ServerListTest#selectServer` is such a case and has no
Testomat.io case on purpose). When a case *does* exist for a commented-out test, ask the user:
keep it (and say "⚠️ commented out in the code" in its description) or delete it. Never decide alone —
the test is usually meant to come back.

---

## Step 5 — Handle MOVED cases

`tests_update` with the new `suite_id` (create the suite first if needed), then fix `suite_id`,
`suite_title` and `feature` in the mapping.

If a whole story was renumbered/renamed in the code, **rename the suite** (`suites_update`) instead of
creating a new one and moving cases — that keeps the history. If a story suite ends up empty **and this
sync created it**, delete it with `suites_delete` so the tree keeps matching Allure. Leave every other
empty suite alone.

---

## Step 6 — Protocols (the only data-driven method)

`ProtocolsTest#checkProtocol` takes its rows from the **screen**, not from the enum:
`getProtocols()` reads the protocol grid and `removeIf(… Auto)` drops `Auto`. So:

- the expected case set is the grid content, kept in the script as `GRID_PROTOCOLS`
  (verified on the Google TV Streamer, 17.08.2026): `IKEv2`, `Super`, `OpenVPNTCP`, `OpenVPNUDP`,
  `V2Ray`;
- `Protocols.java` additionally holds `OpenVPN` and `supx_v22`, which the build does not show — they
  intentionally have **no** cases. That is what `ENUM_DRIFT` is about: it only asks you to check the
  box, it is not a defect;
- mapping keys are the **enum constant names** (`OpenVPNTCP`), because the listener looks a row up by
  `String.valueOf(protocol)`. The case *title* may use the on-screen label (`OpenVPN TCP`);
- case title format: `Connect while keeping the selected server's country — <label>`;
- when the grid really changed (a run logs `[testomatio] no case for … with value "X"`): create the
  case, add it to `parametrized`, and add the value to `GRID_PROTOCOLS` in the script — otherwise the
  next diff will call it stale. For a value that disappeared, ask the user before deleting: a protocol
  removed from a build often comes back.

If a **new** data-driven method appears, decide per the general rule: value set fixed in the code
(enum, hardcoded list) → one case per value; set discovered at runtime → **one case per method**, and
say so in the description, otherwise the cases sit "not run" and lie about coverage.

---

## Step 7 — Update the mapping

`src/main/resources/testomatio-mapping.json`:

```json
{
  "project": "android-f0d8b",
  "root_suite": "1882fcfb",
  "generated_from": ["src/test/java/apps/tv/regression/regression.xml"],
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

- `/<parameterCount>` keeps overloads apart — always include it;
- a case whose story was promoted from `@Story` (no `@Feature`) has **no** `feature` key;
- never write Testomat.io ids into the Java sources. The mapping file is the only link.

Re-run the diff afterwards — it must come back clean.

---

## Step 8 — Verify against Allure (only when the tree is in question)

There is **no Jenkins job for Android TV**, so there is no server-side Allure report to compare with:
generate it locally (`./gradlew regressionTest …` then `allure serve build/allure-results`) and look at
the *Behaviors* tab, or reason from the annotations — they are the same source Allure reads.

Expect legitimate count differences: Testomat.io counts **cases**, Allure counts **invocations** (the
protocol method runs once per protocol the build exposes).

---

## Guardrails

- Reporting uses `create: false`, so a run can never create cases by itself — an unmapped test is
  reported by title and shows up as `[testomatio] not mapped: …`. That is the signal to run this skill.
- Do not change `@Feature` / `@Story` values just to make the tree prettier — they drive the Allure
  report the team reads. (The TV numbering is odd — three features start with "1." — leave it.)
- Ask before: deleting cases, editing test sources, renaming existing suites.
- Never write into `fd90d89e` (the phone subtree) or into `5198b93f` (`Manual`).
- After a sync, tell the user the counts (created / moved / retired) and the link to the root suite:
  https://app.testomat.io/projects/android-f0d8b/suite/1882fcfb
