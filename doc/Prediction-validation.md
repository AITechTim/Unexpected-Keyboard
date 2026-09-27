# Prediction validation

## Ranking, stable slots, and spacing — 2026-09-27

Implementation follows `6f9b172` on `feat/offline-word-predictions`. The installed
397 MB Qwen3 model and pinned llama.cpp revision are unchanged; no new model
installation is required. A larger model was not needed for the measured gains.

### Changes and checks

- Three word slots retain their bounds and surviving candidates retain their
  positions while a word is edited. Empty slots are disabled. Emoji and language
  controls reserve their widths. Pointer-down captures the candidate and freezes
  updates until release/cancel; a stale editor snapshot cannot be accepted.
- Phrase-row migration disables the row once, removing its height and model jobs.
  Later explicit opt-in persists. Learned next words remain available.
- A shared lexical pool includes literal and alias prefixes, exact words, actual
  Unicode edit distance, transpositions, nearby-key double substitutions and
  dictionary-validated inflections. Short forms receive reserved space. The pool
  is capped at 48; native scoring shares token prefixes with six active branches.
  Frequency and edit penalties are applied after contextual likelihood. The
  final prior is `frequency * .18 - edits * 6 - completion_length * .035`.
- Spaces owned by a successful completion move after immediately typed punctuation,
  including repeated punctuation, and are removed for Enter. Existing/manual
  spaces, pasted punctuation and quotes are preserved. Failed commits restore the
  cursor and do not enable completion undo.
- **87 Java tests pass** with Robolectric API 35 integration, including stable
  geometry, retained slots, press capture, phrase migration, typo/inflection pools,
  punctuation, long contexts, failed commits, cursor movement and existing privacy,
  language, learning, repeated-tap, undo and cancellation coverage.
- **9 real-model scoring checks** and **22 native boundary checks pass**. Scoring
  checks include cache reuse, candidate order, German agreement, shared-prefix
  competition, deadline/cancellation and input bounds.
- Android debug build succeeds. Lint remains at **309 errors / 169 warnings**:
  263 missing translations, 31 API issues and 15 class-lookup errors. These match
  the previous baseline. The APK retains `juloo.keyboard2.debug` and the existing
  debug signing key. The final APK passes `zipalign -c -P 16 4`; its SHA-256 is
  `194f5a383962d5e1fe86044573b810a015a8e346ba64f8ae0e2effc86b301e87`.

### Synthetic replay

`QualityReplay.java` uses the production Java pool/ranker, actual English/German
cdict files, active-layout neighbor geometry and the native runtime. The previous
pipeline baseline includes its strict model-word validation, case-insensitive
merging, dictionary queries and capitalization. No learned history is supplied.
Full visible results are in `prediction-runtime/benchmark/quality-results.tsv`.

| Expected word in top three | Previous pipeline | New pipeline | New rank one |
| --- | ---: | ---: | ---: |
| English | 19 / 29 | 28 / 29 | 24 / 29 |
| German | 21 / 27 | 25 / 27 | 24 / 27 |
| Total | 40 / 56 | 53 / 56 | 48 / 56 |

The reported `pred → predictions`, `How do I tur → turn`, `hiben → given`,
and `I was hiven → given` cases all rank first in the warm replay.

| Replay split | Previous warm p95 | New warm p95 | New cold inference p95 | Lexical lookup p95 | Timed-out warm requests |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tuning, 16 cases | 563 ms | 568 ms | 512 ms | 21.5 ms | 2 |
| Diagnostic, 20 cases | 578 ms | 547 ms | 505 ms | 7.8 ms | 1 |
| Regression, 20 cases | 556 ms | 550 ms | 504 ms | 3.5 ms | 1 |
| Final independent holdout, 20 cases | 565 ms | 562 ms | 504 ms | 8.3 ms | 2 |

Warm timings include the 50 ms debounce and new lexical lookup, but exclude model
loading, editor IPC and rendering. Cold timings exclude debounce and lexical work.
No split exceeds the 10% p95 regression limit. A deadline miss falls back to
lexical ranking; cold loading and rapid cancellation can therefore still reduce
grammar quality. These are shared-devbox timings, not Pixel 8 measurements.

The later corpora were initially separate, then used to diagnose missing umlaut
forms and shared-prefix starvation. The 56-case figures above are **regression results**, not
an independent held-out accuracy estimate. Cases are small and synthetic, with
some ambiguous targets; for example, the deliberately retained `Please helpe →
helper` expectation is not a grammatical sentence. These numbers do not establish
general grammar accuracy or a measured typing-speed improvement.

After the implementation was frozen, a **new 20-case holdout** was written and
run once without further code changes. Expected-word top-three coverage was
**20/20 versus 17/20** for the previous pipeline; 19/20 ranked first. Both languages
scored 10/10. Two requests used lexical fallback after the inference deadline.
These remain small synthetic samples, not a representative language evaluation.

Next-word
free generation remains unchanged; the measured gains concern partial-word
completion and correction. There is no universal grammar guarantee.

### Device validation

ADB found no connected device. Pixel 8 / Android 17 checks in Obsidian and the
Roamgate PWA remain pending: repeated full-word taps, punctuation/Enter, rapid
`help → helpe` edits and taps, language changes during a press, manual spaces,
editor selection callbacks, cold-load latency, sustained latency and battery use.
The model remains experimental and opt-in. Use the existing 397 MB model when
installing this APK; do not redownload it for this update.

## English/German suggestions — 2026-09-26

Implementation follows `4c38d0b` on `feat/offline-word-predictions`.
The runtime remains pinned to the same llama.cpp revision. The new model is
DevQuasar Qwen3-0.6B-Base Q4_K_M, revision
`eef7489626f22a4fc10a12ef1b3c5f8852d448e3`. The downloaded 396,704,512-byte file
matches SHA-256 `7f2f66f6b4438bb69c44b8aefa72004a46712b118b7d005c0f3c520238d991a9`.

### Automated checks

- **74 Java tests pass**, no failures or skips. New coverage includes the visible
  EN/DE control with three candidates, preference persistence without a layout
  change, separate dictionary variants and legacy English selection migration,
  language changes during inference and partial-word acceptance, German spelling,
  language-isolated counts, German pause behavior, other-language exclusions,
  the shared retention limit, transactional database
  migration, clearing both languages, and legacy-model/partial-download fallback.
  Existing integrity, cancellation, pause, editor privacy, spacing, repeated taps,
  and undo tests still pass. Android integration uses Robolectric API 35.
- **22 native boundary checks pass**, including German umlauts, ß, compounds and
  multi-word phrases. Both real-model language runs pass concurrent cancellation.
- `:testDebugUnitTest assembleDebug` tasks succeeded in the forced validation run;
  the subsequent `lintDebug` task fails on the unchanged **309 errors / 169 warnings**
  baseline (263 missing translations, 31 API-level issues, 15 class-lookup issues).
- The updated debug APK passes `zipalign -c -P 16 4` and retains the
  `juloo.keyboard2.debug` application ID. A final forced test/build run after
  preserving the other dictionary languages also succeeded (74 tests).
- Deterministic learned replays recover **5/5 English and 5/5 German phrases**
  after two repetitions. Remaining manual keys: 95 English / 105 German;
  ideal single-word taps: 19 each; learned phrase taps: 5 each. Maximum lookup
  was 8.48 ms English / 6.42 ms German. This measures repeated phrases, not unseen
  text or model accuracy.

### Multilingual model smoke results

| Measurement | English | German |
| --- | ---: | ---: |
| Next-word top-three hits | 16 / 30 | 16 / 30 |
| Two-character completion top-three hits | 29 / 30 | 19 / 30 |
| Raw dictionary completion top-three hits | 9 / 30 | 10 / 30 |
| Cold-context word availability | 30 / 30 | 30 / 30 |
| Phrase availability | 18 / 30 | 16 / 30 |
| Warm word p95, including 50 ms debounce | 554 ms | 569 ms |
| Cold-context inference p95, excluding debounce/load | 434 ms | 530 ms |
| Phrase inference p95, excluding scheduling | 383 ms | 556 ms |
| Model load | 848 ms | 1,033 ms |
| Standalone peak resident memory | 705 MiB | 711 MiB |

Each corpus contains 30 public synthetic prompts. Runs use the same retained
language cue and 500 ms word / 1,000 ms phrase budgets as the Android runtime.
Completion prefixes count Unicode characters, including umlauts. The raw native
dictionary baseline does not reproduce the Java keyboard's character substitutions
or casing adjustments. Phrase availability is not an accuracy score; samples can
be repetitive. Timing budgets can be exceeded by tokenization and an in-flight
native computation step.

These shared-devbox measurements do not establish phone performance. Latency and
standalone memory exceed the original 200 ms / 300 MiB goals. The APK continues
to offer dictionary and learned suggestions before model results arrive, and
model predictions remain experimental and opt-in. Installing the bilingual model
is an explicit 397 MB download; the older model remains an English-only fallback
until that download completes.

Roamgate's composer change is separate:
https://github.com/Cancilico/roamgate/pull/6. Its production web build and full
pre-commit gate passed (format, lint, type checks, 2,145 tests; one existing live
SSH test skipped). It is committed and pushed, but not merged or deployed.

### Device checks still required

No phone was connected to ADB. Pixel 8 / Android 17 testing in Obsidian and the
Roamgate PWA composer remains pending, including actual touch behavior, cold
loading, language switching during typing, memory, sustained latency and battery
use. Keep the experimental label and default-off model setting. Roamgate's direct
terminal remains outside prediction eligibility; compose prose in its composer
before inserting or sending it.


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
