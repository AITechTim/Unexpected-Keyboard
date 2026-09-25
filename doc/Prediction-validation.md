# Prediction validation — 2026-09-25

Implementation base: `7e632fb` (Unexpected Keyboard 2.1.0).
Runtime: llama.cpp `e85e15cf6d810cd1268498c2e5b657bb3ece47bc`.
Model: QuantFactory SmolLM2-135M Base Q4_K_M,
revision `d948db3614be18259a175aafd7689a70f1cb4e2f`.
The downloaded 105,453,536-byte artifact matched SHA-256
`b38d0237afc63fa77f35b9f82f394ac2116e77609514461d6bc51deec02a9c91`.

## Automated checks

- 37 Java unit tests passed with no failures or skipped tests. This includes
  field eligibility, UTF-16 snapshots, prefix constraints, candidate merging,
  retention of a separate dictionary candidate for space, replacement of pending
  work, cancellation of in-flight results, model failure recovery, and download
  size/checksum/cancellation checks.
- 11 native word-boundary checks passed, including partial UTF-8, apostrophes,
  whitespace, mismatched prefixes and tokens spanning multiple words.
- The real native model passed the benchmark's concurrent cancellation check.
- Debug APK builds successfully with JDK 21.0.2, Android SDK 36,
  NDK 28.2.13676358 and CMake 3.22.1. Its separate package ID is
  `juloo.keyboard2.debug`.
- APK ZIP alignment passes `zipalign -c -P 16 4`. Native prediction ELF load
  segments have `0x4000` alignment. All six expected JNI entry points are
  exported. This is a binary alignment check, not a 16 KB device smoke test.

The final unit test run forced recompilation (`--rerun-tasks`) because an earlier
incremental build omitted newly added tests. Builds used `--no-daemon`,
`--no-watch-fs`, `--no-configuration-cache`, two Gradle workers and a 2 GiB JVM
heap. Native builds used at most two compiler jobs. Devbox memory stayed below
its warning threshold throughout these jobs.

## Desktop smoke benchmark

The actual decoder was run on the devbox CPU with two inference threads and the
30 synthetic English phrases in `prediction-runtime/benchmark/english.tsv`.
It used the pinned model and the keyboard's decompressed English US dictionary.
The benchmark warms each phrase's context before timing prediction; these are
not cold-field or real-phone timings.

| Measurement | Result |
| --- | ---: |
| Model load | 153 ms |
| Warm p95, including simulated 50 ms debounce | 210 ms |
| Next-word top-three hits | 17 / 30 |
| Completion top-three hits, two-letter prefix | 28 / 30 |
| Existing dictionary top-three hits, same prefixes | 9 / 30 |
| Simulated keystrokes saved | 115 / 181 |
| Peak standalone process RSS | 151 MiB |
| Concurrent cancellation | Passed |

The synthetic corpus is small and deliberately simple. These results establish
that the pipeline runs and can improve these completions; they do not establish
quality for everyday typing. Standalone RSS excludes the Android app, and desktop
CPU timings cannot establish phone latency, battery use or thermal behavior.
Even this desktop p95 is slightly above the proposed 200 ms target.

## Lint comparison

`lintDebug` does not pass on the original branch or this feature branch. A clean,
detached checkout of `7e632fb` reports 311 errors: 263 missing translations,
33 existing API-level issues, and 15 class-lookup failures associated with the
repository's Java source layout. The final feature branch reports the same 311
error messages, with no new lint errors.

The new personal-fork settings are explicitly English-only. The new settings
activity has a targeted class-lookup suppression; APK inspection confirms it is
present. Three additional warnings concern the conservative free-space check
and the candidate touch listener. The listener returns false so standard
TextView click/accessibility handling remains in control; it only remembers the
candidate present on touch-down.

## Remaining device validation

ADB reported no connected devices. Consequently, the installed keyboard's real
InputConnection behavior, tap/shortcut interactions, direct boot, rotation,
16 KB device execution, memory pressure, end-to-end latency, battery use and
sustained typing have not been exercised on a phone. Follow the device checklist
in [Offline-predictions.md](Offline-predictions.md) before removing the
experimental label or enabling the feature by default.
