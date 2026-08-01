# Project State

_Last updated: 2026-08-01_

## Current architecture

- Native Android application built with Kotlin and Gradle.
- Android UI and onboarding logic are centered in `MainActivity.kt`.
- Input validation and normalization are separated into `Validation.kt`.
- Official NSE quote URL parsing and allowlisting are handled by `OfficialNseUrl.kt`.
- User configuration is stored through `ProfileStore.kt` using Android Keystore-backed encryption.
- GitHub Actions builds, tests, verifies, renames, hashes, and uploads the debug APK.
- The application remains signal-only: it does not log in to Groww, connect to a broker for execution, or place orders.
- Safety remains fail-closed through `NO_TRADE` behavior when required inputs or conditions are unsupported or ambiguous.

## Completed features

- Android onboarding flow for Telegram bot configuration and watchlist entry.
- Telegram bot token and numeric Chat ID validation.
- Clear onboarding guidance that the Telegram Chat ID is not a phone number.
- Rejection of 10-digit Indian mobile-number-like Chat IDs with the message: `Enter your Telegram Chat ID, not your phone number`.
- Multiline and comma-separated watchlist parsing.
- Leading and trailing whitespace trimming.
- Blank-line removal.
- Lowercase and mixed-case symbol normalization to uppercase.
- Canonical symbol support for `A-Z`, `0-9`, `&`, `-`, and `_`.
- Rejection of symbols containing internal spaces.
- Fail-closed rejection of malformed and unsupported URLs.
- Allowlisted official NSE quote URL support, with URLs normalized primarily to symbols.
- Explicit non-equity instrument classification:
  - `NIFTY` — index
  - `BANKNIFTY` — index
  - `SENSEX` — index
  - `CRUDEOILM` — commodity
- Regression tests for watchlist parsing, URL handling, instrument classification, Telegram Chat ID validation, and secret hygiene.
- GitHub Actions verification for source secrets, APK existence, `classes.dex`, prohibited packaged profile files, hardcoded Telegram credentials, and broker-execution surfaces.
- Preserved operating constraints:
  - Android Keystore encryption
  - 2% hard risk cap
  - Default timeframes: 5M, 15M, 1H, D, W
  - Five-trading-day swing guidance
  - Signal-only behavior
  - No Groww login
  - No broker execution
  - Fail-closed `NO_TRADE` behavior

## Pending work

- Review and merge pull request #2 into `main` after approval.
- Produce a signed release APK or Android App Bundle for production distribution; the current verified artifact is a debug APK.
- Add device or emulator UI tests for the complete onboarding interaction. Current regression coverage is unit-test focused.
- Add live exchange-instrument verification only if a trusted, authenticated data source is selected; local parsing currently validates syntax and explicit classifications, not live listing status.
- Decide whether BSE and commodity quote URL formats should receive separate, explicitly allowlisted parsers.
- Replace deprecated Android `getParcelableExtra` usage reported by the compiler when broader maintenance work is authorized.

## Known bugs and limitations

- Instrument classification is local metadata and does not confirm that a symbol is currently listed or tradable on a live exchange.
- URL support is intentionally restricted to approved NSE HTTPS quote hosts and paths; other finance URLs are rejected.
- The generated APK is a debug build and is not suitable as a production-signed release.
- The compiler reports a deprecation warning for `getParcelableExtra` in `MainActivity.kt`; it does not fail the build.
- The GitHub Actions artifact is retained for 30 days unless downloaded or rebuilt.
- Software validation and successful builds do not establish trading profitability or strategy validity.

## Build instructions

Prerequisites:

- JDK 17
- Android SDK platform 35
- Android build tools 35.0.0
- Gradle wrapper or Gradle 8.9 to generate the wrapper when absent

Run from the repository root:

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected debug APK location before packaging:

```text
app/build/outputs/apk/debug/
```

Verification requirements:

1. Confirm an APK exists and is non-empty.
2. Confirm `classes.dex` exists in the APK.
3. Confirm `.env`, `profile.json`, `resources.json`, and `alert-state.json` are not packaged.
4. Confirm no Telegram bot token or numeric Chat ID constant is embedded in source or `classes.dex`.
5. Rename the APK to `Institutional-Trading-System-Android.apk`.
6. Generate `Institutional-Trading-System-Android.apk.sha256` using SHA-256.

## Latest APK details

- Verified application commit: `7c3aa777dafb0d1428d8fae1d4dbc8f09f938e4b`
- Pull-request merge test SHA: `7170721ff101ca3c26585ca2cb5dc83fac4068e8`
- Workflow run: `30682804025`
- Artifact ID: `8812873652`
- GitHub Actions artifact name: `Institutional-Trading-System-Android-7170721ff101ca3c26585ca2cb5dc83fac4068e8`
- APK file inside artifact: `Institutional-Trading-System-Android.apk`
- Checksum file: `Institutional-Trading-System-Android.apk.sha256`
- APK SHA-256: `59d91706a90471b42d479504e9ca15487bbbad5108aa0d83381d5d5f6fd3d98e`
- Uploaded artifact archive size: 839,000 bytes
- Artifact retention: 30 days, expiring 2026-08-31 unless rebuilt

## Latest commit SHA

- Latest verified application-code commit: `7c3aa777dafb0d1428d8fae1d4dbc8f09f938e4b`
- Base `main` SHA used for the fix: `1b4837b13f60d256658ec8a2f31deaf243e3fd78`
- This state document is committed afterward on branch `fix/onboarding-watchlist-validation`; use the branch head for the newest documentation commit.

## Test status

**Status: passing.**

Command executed in GitHub Actions:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Results:

- `:app:testDebugUnitTest` — passed
- `:app:assembleDebug` — passed
- Gradle build — `BUILD SUCCESSFUL`
- 39 Gradle tasks executed successfully
- 12 regression tests passed, 0 failed
- Source secret scan — passed
- APK non-empty check — passed
- `classes.dex` check — passed
- Prohibited packaged profile-file check — passed
- Telegram token and Chat ID embedding checks — passed
- Artifact upload — passed
