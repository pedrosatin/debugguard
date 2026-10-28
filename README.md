# DebugGuard

Some banking and government apps refuse to run when they detect developer options or USB/Wi-Fi debugging. The usual fix is turning debugging off every time you open them, and Android also resets animation scale overrides when you disable developer options.

DebugGuard is a small local app for Samsung One UI and other Android devices (Android 10+, API 29). It hides the developer options flag in daily use, pauses ADB only while a sensitive app is open, and restores it once you leave. Animation scales stay where you set them.

Current version: **1.4.9** (`versionCode` 19). Uses the system light/dark theme and ships its own launcher icon.

Stable rollback: release [v1.4.4](https://github.com/pedrosatin/debugguard/releases/tag/v1.4.4). The pause/restore flow was also validated on [v1.4.7](https://github.com/pedrosatin/debugguard/releases/tag/v1.4.7).

## What it does

The app UI is in Portuguese; the original labels are in parentheses.

| Action | Effect |
| --- | --- |
| **Apply safe profile** (*Aplicar perfil seguro*) | `development_settings_enabled=0`, `adb_enabled=1`, `adb_wifi_enabled=1`, animations at `0.5×` |
| **Pause debugging** (*Pausar depurações*) | Turns off the developer options flag, USB ADB and Wi-Fi ADB, saving the previous values |
| **Restore** (*Restaurar*) | Writes back the three values saved by the pause |

The **Safe profile** (*Perfil seguro*) shortcut is meant to be fired by an automation when a sensitive app opens. It pauses ADB, relaunches the app (the shortcut steals focus and cancels its biometric prompt), and a foreground **Guard** service restores ADB when the app leaves the screen. You can also restore by hand from the notification.

Some apps check more than the developer options flag and only work with ADB fully off, which is why the shortcut pauses ADB instead of just hiding the flag.

## Requirements

- Android 10 or newer.
- A computer with `adb` for the one-time permission grants.
- `WRITE_SECURE_SETTINGS`, which can only be granted over ADB.
- Usage access (`GET_USAGE_STATS`), so the Guard can tell when the sensitive app leaves the foreground.

## Installation

```bash
adb install -r build/debugguard.apk
adb shell pm grant --user 0 dev.satin.debugguard android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant --user 0 dev.satin.debugguard android.permission.POST_NOTIFICATIONS
adb shell appops set --user 0 dev.satin.debugguard GET_USAGE_STATS allow
```

Open DebugGuard once afterwards so it can start the Guard service.

## Daily use

### Samsung One UI (Modes and Routines)

1. Open **Modes and Routines** → **Routines** → **+**.
2. **If**: **App opened** → pick the sensitive apps.
3. **Then**: **Apps** → **Open an app or do an app action** → **DebugGuard** → **Perfil seguro**.
4. Save. Open the app, go through biometrics, and close it when you're done. ADB comes back on its own.

### Other Android devices

Without One UI routines, use the buttons in the app: **Pause debugging** before opening the sensitive app, then **Restore** (in the app or the notification) when you're done. The Guard still restores ADB automatically if usage access is granted.

Automation apps such as Tasker or MacroDroid can launch the same shortcut, but `ApplySafeProfileActivity` only accepts callers from an allowlist (Samsung Routines, `com.android.shell`, `com.android.systemui` and DebugGuard itself). Add your automation app's package to `ALLOWED` in [`ApplySafeProfileActivity.java`](app/src/main/java/dev/satin/debugguard/ApplySafeProfileActivity.java) and rebuild.

More detail (in Portuguese): [docs/USO.md](docs/USO.md).

## Examples

Check the current values:

```bash
adb shell settings get global development_settings_enabled
adb shell settings get global adb_enabled
adb shell settings get global adb_wifi_enabled
adb shell settings get global animator_duration_scale
```

After **Apply safe profile**, expect `0`, `1`, `1` and `0.5`.

Trigger the actions from a shell (the shell is in the allowlist):

```bash
# Apply the safe profile / pause flow
adb shell am start -a dev.satin.debugguard.action.APPLY_SAFE_PROFILE \
  -n dev.satin.debugguard/.ApplySafeProfileActivity

# Restore the saved ADB values
adb shell am start -a dev.satin.debugguard.action.RESTORE \
  -n dev.satin.debugguard/.ApplySafeProfileActivity

# The same actions through deep links
adb shell am start -a android.intent.action.VIEW -d debugguard://safe-profile
adb shell am start -a android.intent.action.VIEW -d debugguard://restore
```

Remember that pausing ADB over Wi-Fi drops your own `adb` connection. Restore from the phone in that case.

Watch the logs while testing a routine:

```bash
adb logcat -s DebugGuard.ApplySafe DebugGuard.Guard DebugGuard.BankResume
```

### Protecting other apps

The list of protected apps is hardcoded. It currently covers Gov.br, CAIXA and FGTS (Brazilian government and banking apps). To add another app, append its package to `PACKAGES` in [`BankResume.java`](app/src/main/java/dev/satin/debugguard/BankResume.java), plus a fallback launch activity in `FALLBACK_ACTIVITIES`:

```java
static final String[] PACKAGES = {
        "br.com.gabba.Caixa",
        "br.gov.meugovbr",
        "br.gov.caixa.fgts.trabalhador",
        "com.example.mybank"
};
```

Find a package name and its launcher activity with:

```bash
adb shell pm list packages | grep -i mybank
adb shell cmd package resolve-activity --brief com.example.mybank
```

Then rebuild, reinstall and add the app to your routine.

## Build and tests

The build script uses the Android SDK command-line tools directly, without Gradle. It expects API 36 and build-tools 36 under `~/.cache/debugguard-sdk` (override with `DEBUGGUARD_SDK`) and JDK 17+.

```bash
bash scripts/test.sh
bash scripts/build.sh          # outputs build/debugguard.apk
# optional:
VERSION_NAME=1.0.1 VERSION_CODE=4 bash scripts/build.sh
```

The APK is signed with a local debug keystore generated on the first build.

## Comparison with Geto 1.17.3

Geto (F-Droid, GPLv3) is a generic per-app manager for `Settings.*` values, built with Compose and Room, with shortcuts that apply a template and open the target app. While reviewing it, we found that its templates reset the developer options flag to a fixed `1`, partial applies have no full rollback, and nothing restores the settings automatically when you leave the app.

**Borrowed as ideas only (our own code):** a panel with live values, a primary safe-profile action, and a permission status indicator.

**Left out:** the Compose/Hilt/Room stack, the app list, generic templates and Geto's "apply and open app" flow (on One UI, routines handle that better).

No Geto code was copied.

## Limitations

- `WRITE_SECURE_SETTINGS` is a broad permission and can only be granted over ADB.
- `ApplySafeProfileActivity` is exported but restricted to the allowlist above. Launching it cancels the sensitive app's biometric prompt, so DebugGuard relaunches that app right after.
- If the developer options flag is already `1` when the app opens, the app may run its check before the routine fires. Keeping the safe profile (flag at `0`) applied in daily use, including after boot, shrinks that window.
- Automatic restore relies on UsageStats. If it misses the exit, use **Restore** from the notification.
- The automatic flow has been tested on a Galaxy S25 with One UI. Other devices may behave differently around the biometric relaunch and foreground detection.
