# Prediction validation

## Word acceptance, learning, and phrases — 2026-09-26

Implementation follows `13b6412` on `feat/offline-word-predictions`. The pinned
model and llama.cpp runtime are unchanged.

- **65 Java tests pass**, with no skipped tests. New tests cover full-word
  acceptance (`to`, `at`, `by`), repeated taps, delayed selection notifications,
  stale cursor/selection state, composing regions, failed editor operations,
  UTF-16/emoji, punctuation, existing whitespace, phrase undo, and legacy editor
  query support. Editor integration uses Robolectric at API 35; it is not an
  Obsidian or Android 17 device test.
- Learning tests cover repeated observations, contextual ranking, preserved
  capitalization, prefix matching, retention/capacity, persistence after a cache
  reload, storage outside backups, clear, pause, sensitive fields, pasted and
  generated text, immediate undo, and pending learning invalidation.
- **19 native word/phrase boundary checks pass**. The real model also passes the
  benchmark's concurrent cancellation check.
- `:testDebugUnitTest assembleDebug --rerun-tasks` succeeds with the same isolated
  SDK/JDK and bounded build settings as below. Forced execution is important:
  incremental runs in this workspace previously omitted newly added test files.
- The debug APK passes `zipalign -c -P 16 4`. Its application ID remains
  `juloo.keyboard2.debug`; installing it updates the previous debug build.

Lint still fails on the repository baseline: **309 errors** remain (263 missing
translations, 31 API-level issues, 15 class-lookup issues). The API annotation on
the existing surrounding-text helper resolves two previous errors; no new lint
errors remain. The new phrase view has the same targeted class-lookup suppression
as the prediction settings activity because of this repository's source layout.

### Desktop smoke results

| Measurement | Result |
| --- | ---: |
| Real-model next-word top-three hits | 17 / 30 |
| Real-model two-letter completion hits | 28 / 30 |
| Dictionary two-letter completion hits | 9 / 30 |
| Real-model phrase availability | 17 / 30 prompts |
| Word warm p95 including 50 ms debounce | 302 ms |
| Phrase inference p95, excluding scheduling delay | 230 ms |
| Model load | 313 ms |
| Learned exact phrases after two typed occurrences | 5 / 5 |
| Manual keys after each replay phrase's first word | 95 |
| Ideal single-word completion taps for the same suffixes | 19 |
| Learned whole-phrase taps for those suffixes | 5 |
| Maximum lookup in the small learned replay | 6.84 ms |

The word corpus is the same 30 synthetic prompts as the original benchmark.
Phrase samples included “link to the article” and “in a few days.” Availability
is not an accuracy score. The new five-phrase replay is intentionally trained
on its own recurring phrases; it demonstrates memory and tap reduction, not
unseen-language generalization. Its single-word baseline assumes perfect
predictions, costing one tap per remaining word.

Timing was measured on the shared devbox and cannot establish phone speed.
The word p95 exceeds the original 200 ms goal. Phrase work runs after words and
at least 250 ms of quiet typing, so its inference time is not end-to-end latency.
No phone was connected to ADB. Obsidian on Pixel 8 / Android 17, actual keyboard
rendering/touch behavior, battery use, and device latency remain unverified.
Follow the expanded device checklist in `Offline-predictions.md`.

## Original implementation — 2026-09-25

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
