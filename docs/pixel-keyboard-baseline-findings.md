# Pixel baseline findings, 2026-09-27

Device evidence from diagnostic keyboard 2.1.2: Pixel 8, Android 17 / SDK 37.
The user confirms all three synthetic test fields work; the original work-items
filter still crashes. Both supplied exports are labeled baseline. The user subsequently reports the original filter also crashes with No optional
text reads selected, and recalls the regression beginning with phrase prediction.

The page trace includes repeated completed backward deletions in plain and
static fields, then seven completed deletions in the dynamic field, each followed
by its delayed update. Those entries are non-composing. This narrows the failure
beyond simple backward deletion, a static combobox, and the synthetic delayed
combobox update. It does not establish that the real editor's accessibility or
DOM operations are safe: the synthetic case is smaller and behaves differently.

In the keyboard trace, getSurroundingText normally completes within milliseconds.
At elapsed_ms 639614086 it begins a call which takes 2002 ms and returns null.
The fallback getTextBeforeCursor then takes 488 ms and also returns null;
subsequent reads continue returning null. The next deletion DOWN is only logged
at 639616583, after those reads. This establishes blocking before delivery of
that deletion, but cannot establish whether the reads caused Chrome's failure or
whether an already-failing renderer caused the reads to block. The exports use
different clocks and do not include a renderer crash timestamp.

PSS samples around the failure are approximately 70–78 thousand KB. The recorded
model load begins at 639657675, about 43.6 seconds after the blocking read began,
and raises PSS to 557361 KB. That later rise is not evidence that model loading
caused the earlier failure; sparse keyboard-only samples do not establish total
system or Chrome memory conditions.

Source comparison: the production editor additionally parses/validates the full
query, changes history state, updates helper controls and autocomplete, and can
fetch/render results. The synthetic dynamic input implements only a small
validation regex and a constant suggestion list. Existing Chromium emulation
checks have not reproduced the physical renderer crash.

Initial discriminating check: choose No optional text reads in the keyboard, close
and reopen it, and repeat the original Open-view edit. A continued crash would
show that the suppressed InputConnection reads are not required for reproduction;
a successful edit would implicate their interaction without proving a specific
Chrome/IME fault. Preserve that mode's trace and report the visible outcome.
Do not select a permanent fix from the baseline trace alone.

## Source findings and 2.1.3 correction

The phrase-completion commit 4c38d0b added unconditional snapshot binding in
PredictionController.changed(). It queries editor text before checking the
field eligibility used by model inference and learning. The exact logged field
flags, 2621601 (0x2800a1), set NO_SUGGESTIONS; those reads should not happen.
A regression test using those flags fails on 2.1.2 because snapshot() returns
text even for this excluded field. 2.1.3 guards both changed() and read() with
the field policy, preserving dictionary completions independently of model
language settings. All 81 unit/Robolectric tests pass. The 2.1.3 source is not
distributed as a crash fix; the latest installed artifact remains 2.1.2.

The diagnostics activity also wrote only credential-protected preferences while
the live keyboard observes device-protected preferences. Its in-process mode
change could be overwritten by a subsequent service configuration callback.
2.1.3 copies only the diagnostic settings to the observed store and tests a
service reconfiguration. Therefore the reported no-reads outcome cannot rule out
text reads without its actual event-mode trace. The baseline export is insufficient.

A page-side explicit-Apply prototype was discarded without deployment after
finding the keyboard regression. Keep the normal filter behavior for the next
2.1.3 baseline test. Native Chrome crash resolution still requires phone evidence.

The subsequent no_reads export confirms the mode remained active through the
crash. It contains deletion DOWN/UP and selection events, no text read calls,
and a model unload before the attempt. This rules out the suppressed reads and
active model inference as necessary triggers for this reproduction. The field
eligibility and settings fixes are valid separate defects, not an established
fix for the Chrome failure.
