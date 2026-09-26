# Offline word and phrase predictions (experimental)

This fork offers English next-word predictions, word completions, and a separate
short-phrase row. Inference runs on the phone and requires Android 9 or newer
with a 64-bit ARM process. Optional local phrase learning also works without a
model, including on devices that cannot run the model. Model predictions and
learning are disabled by default; dictionary suggestions remain available.

## Setup and use

1. Open keyboard settings → Prediction model and learned phrases → Download model (105 MB).
2. Wait for the download and checksum verification to finish.
3. Enable **Offline word predictions (experimental)** in keyboard settings.
4. Select an English keyboard language/dictionary, and type in an ordinary text
   field. Tap a candidate or use a completion shortcut to accept it.

The model is SmolLM2-135M Base, quantized to Q4_K_M by QuantFactory. Its download
is 105,453,536 bytes (about 101 MiB). Model loading happens in the background;
dictionary suggestions remain available while it loads. Downloads can be
canceled or retried from settings. Removing the model also disables predictions.
License texts are available in the model settings screen and bundled APK.

After installation, inference works in airplane mode. The prediction feature
sends no typing to any server and logs no prompts or predictions. When local
learning is enabled, recurring phrase fragments are saved as described below. The model resides in private storage excluded from backup. It is
unavailable before device unlock.

Suggestions complete a partially typed word or insert the next word. At the end
of the field, accepting any word or phrase adds a space. If the completed word
already exists, tapping it advances without duplicating it. Existing ordinary
spaces are crossed without adding another; following punctuation is preserved. An immediate backspace undoes the
acceptance, provided the cursor and surrounding text still match the insertion.
Space-to-complete continues to use the dictionary candidate, never an LLM guess.

The feature excludes passwords, numbers, terminal input, URLs, email addresses,
selected text, the interior of words, and editors requesting no suggestions or
no personalized learning. It follows the existing split-layout restriction.
English is the only evaluated language; a selected non-English dictionary or
keyboard language disables LLM predictions.

## Local learning and phrases

Enable **Learn my writing** in the Suggestions settings to remember recurring
English wording. After typing a sequence twice, the keyboard can offer its next
word and a 2–5 word phrase. For example, recurring “see you tomorrow morning”
can produce “you” in the word strip and “you tomorrow morning” in the phrase row.
There is no model training or additional download.

- **Phrase suggestion row** reserves a separate row while predictions or learning
  are enabled. It can be turned off without disabling the three word choices.
  Tap the row to insert the whole visible phrase; Backspace immediately undoes it.
- **Pause learning** stops new collection while keeping saved suggestions.
  Turning **Learn my writing** off stops both collection and learned suggestions.
- **Prediction model and learned phrases → Clear learned phrases** removes saved
  fragments and clears pending collection and cached suggestions. Clearing does
  not disable subsequent learning; pause or turn learning off to keep it empty.
- Only successful individual keyboard keystrokes are collected. Pasted text,
  editor history, imported text, and accepted suggestions are not collected.
  A 700 ms quiet period validates pending observations before saving; immediate
  editing, undo, field changes, or changing the learning controls discards them.
- Fragments contain 2–6 completed words, frequency, and last-typed time. They
  are shared across eligible English fields on this phone. No full message or
  app history is stored. Passwords and editors requesting no personalized
  learning are excluded. Data lives in private storage outside Android backups.
- Storage holds at most 10,000 fragments; entries not typed for 90 days expire.
  Spaces and common sentence punctuation complete words for learning.
  Learning intentionally excludes the word preceding a correction/acceptance.

## Implementation and limits

- Java coordinates editor snapshots, settings and a single background worker;
  a separate Android library provides the JNI/llama.cpp runtime.
- Requests read at most 1,024 UTF-16 characters before the cursor, plus 32 after
  it for insertion validation. Only the preceding text is sent to the local
  model, capped at 256 tokens.
- A 50 ms debounce coalesces edits. There is one running request and at most one
  pending snapshot. New edits cancel old work; editor revisions and text/cursor
  checks prevent stale results from displaying or being inserted.
- Raw text continuation uses six beams, up to eight generated tokens, two CPU
  threads and a 250 ms inference deadline. Word boundaries and typed prefixes
  are checked on decoded bytes, including tokens that span words. The Java
  boundary validates complete Unicode words before displaying them.
- Phrase generation starts after word inference and at least 250 ms without
  edits. It uses one constrained continuation, up to 32 tokens, a 500 ms budget,
  and at most five complete words. New edits cancel it. Sentence boundaries stop
  generation; an unfinished last word is dropped. Learned phrases take priority.
  Phrases that cannot fit visibly in the row are not tappable.
- Dictionary acceptance also validates a fresh editor snapshot. Transactions end
  composition before replacing the actual word range and reject consumed or
  stale candidates. Cursor tracking uses UTF-16 offsets throughout.
- Weights stay loaded while the keyboard is visible and for up to 60 seconds
  after hiding it. Field changes clear token state. Memory pressure unloads the
  model and suspends inference until the next input view starts.
- The native runtime uses an Android 28 minimum; the app retains Android 21
  support through an explicit manifest library override and guarded loading.
  No native prediction code is loaded on unsupported devices.
- The runtime and model are pinned. Model revision, byte count and SHA-256 are
  constants in `ModelStore`. There is no automatic model update or arbitrary
  model import. Future model/runtime changes require repeating the validation.

## Reproducing checks

Run `devbox-mem` first in the shared devbox. Run one build at a time with bounded
parallelism. JDK 17 or newer, Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1
are required. Initialize all Git submodules first.

```sh
git submodule update --init
./gradlew --no-daemon --max-workers=2 testDebugUnitTest assembleDebug
```

The debug APK has a separate application ID and can coexist with the released
keyboard. The native CMake build limits compilation to two jobs and linking to
one job. The unit tests include Robolectric editor transactions and local database persistence,
plus prefix handling, stale snapshots, eligibility,
candidate merging, cancellation/coalescing, missing runtimes and download
integrity. Native boundary tests exercise partial UTF-8 and multi-word tokens.

Build the standalone decoder benchmark using the same pinned runtime:

```sh
cmake -S prediction-runtime/benchmark -B /tmp/prediction-bench -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/prediction-bench -j2
ctest --test-dir /tmp/prediction-bench --output-on-failure
/tmp/prediction-bench/prediction-bench /path/to/model.gguf prediction-runtime/benchmark/english.tsv /path/to/en_US.dict
```

Use the model URL and SHA-256 in `ModelStore.java`. The optional dictionary is
Unexpected Keyboard's decompressed `v1/en_US.dict` from its dictionary repository.
The benchmark prints aggregate counts, warm latency including the 50 ms debounce,
phrase inference latency, phrase availability, and a cancellation result. Its corpus contains 30 synthetic English phrases
under CC0; these are smoke tests, not a representative quality evaluation.
Completion accuracy uses two-letter prefixes. Simulated keystrokes count one tap
for an accepted word and include its trailing space. Omitting the dictionary
reports its hit count as -1.

For device benchmarking, cross-compile this same benchmark with the NDK CMake
toolchain (`ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-28`), push the binary,
model, corpus and dictionary to `/data/local/tmp/`, and run it through `adb shell`.
Also measure the installed keyboard: the standalone program does not include
InputConnection IPC, rendering, the app's baseline memory, or thermal effects.

The recurring-phrase replay uses five synthetic phrases, typed twice each:

```sh
mkdir -p /tmp/phrase-replay
javac -d /tmp/phrase-replay srcs/juloo.keyboard2/prediction/{PredictionSnapshot,PhraseMemory,PhraseLearner}.java prediction-runtime/benchmark/PhraseReplay.java
java -cp /tmp/phrase-replay juloo.keyboard2.prediction.PhraseReplay
```

Its single-word comparison assumes perfect predictions (one tap per remaining
word). It measures the tap savings from grouping known words; it does not claim
accuracy on previously unseen text.

## Device acceptance checklist

The release gate is a physical target phone, not desktop benchmark timing.
Until these checks pass, keep the experimental label and default-off setting.

- On **Pixel 8 / Android 17, Obsidian**, test first with the model disabled:
  type `to`, `at`, and `by` fully and tap the same word once. Expect one trailing
  space and no partial deletion. Rapid repeat taps must not create `tototo`.
  Repeat with the model enabled, with an existing following space, before a
  comma, after emoji, and after moving/selecting text. Check immediate undo.
- Enable learning, type a short phrase twice with a space at the end and a pause,
  then type its opening word. Verify word-by-word and whole-phrase suggestions,
  persistence after restart, pause behavior, and clearing without resurrection.
- Target warm end-to-end p95 ≤200 ms and additional steady-state memory <300 MiB.
  Record cold-load time, sustained typing latency, battery/thermal behavior,
  top-three accuracy, and keystrokes saved versus dictionary-only completion.
- Verify taps and all three completion shortcuts; space must never accept LLM
  guesses. Check insertion before punctuation/whitespace and backspace undo.
- Type rapidly, delete, paste, move/select text, switch fields/apps/languages,
  rotate, hide/show the keyboard, and accept while predictions change. No stale
  insertion or replacement of unrelated text is acceptable.
- Check passwords (including visible/web/numeric), URL/email/terminal fields,
  no-personalized-learning, no-suggestions, split layouts and direct boot.
- Download/cancel/retry, interrupt the network, exhaust storage, remove the model,
  and use airplane mode after installation. Restart during an unfinished
  download; its temporary file must never be treated as an installed model.
- Trigger memory pressure, verify unloading and recovery on the next field, and
  test an Android device with 16 KB pages. Unsupported devices must still type
  and use dictionaries normally.

See `Prediction-validation.md` for the checks performed in this implementation.
