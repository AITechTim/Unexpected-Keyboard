# Pixel 8 backspace investigation

Diagnostic update 2.1.2 (versionCode 58), based on the custom 2.1.0 keyboard.
This is an experiment for the Android 17 Chrome renderer crash; a permanent fix
requires device results. Open Settings → Keyboard diagnostics to select a mode:

- Original behavior: existing prediction pipeline with lifecycle cleanup.
- No native predictions: unload the model; preserve optional editor reads.
- No optional text reads: suppress InputConnection queries, predictions and
  automatic capitalisation; still deliver deletion events.
- Deferred text reads: deliver key events before prediction reads, coalesce
  refreshes, and share a post-edit snapshot across prediction sources.

Close/reopen the keyboard between mode changes. Compare plain input, static
combobox, dynamic combobox and the actual work-item query editor, then repeat
held backspace and sort-order edits. Export each run before clearing the trace.
Compare against installed 2.1.0 first when practical.

Recording is off by default. Its 256-event local cache contains only timing,
field flags, selection offsets and memory samples. It never contains editor
text, key characters, clipboard data or prediction output. Explicit export uses
the Android document picker; nothing is automatically uploaded. Clearing deletes
the cache. The diagnostic wrapper and recording can affect timing.

The signed ARM64 debug APK updates `juloo.keyboard2.debug` without uninstalling.
Preserve the existing debug signing identity when building; do not commit keys
or APKs. Record APK hash and certificate fingerprint alongside distribution.

Validated with 79 Robolectric/unit tests, including five diagnostic regressions.
Build (Java 21, Android SDK installed):

```sh
./gradlew testDebugUnitTest assembleDebug \
  -PdiagnosticArm64=true --max-workers=2 --no-daemon \
  --no-watch-fs --no-configuration-cache \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:ActiveProcessorCount=4'
```

Verify that `TEST-juloo.keyboard2.KeyboardDiagnosticTest.xml` includes all five
tests, and verify APK version/package/signature before distribution. Desktop
or Robolectric results cannot establish that Android's native crash is fixed.

2.1.1 was accidentally built with the IDE-only ABI property, which injected
`android:testOnly=true`; Android's normal installer rejects it. Version 2.1.2
uses `-PdiagnosticArm64=true` instead. Before publishing, run
`scripts/verify-installable-apk.py` with the APK, `--build-tools` SDK directory,
expected `--version-name`, `--version-code` and `--certificate` SHA-256. This
checks the actual packaged manifest, signature, ABI and ZIP alignment. Never
publish an APK with the testOnly flag or bypass it with an adb-only install.
