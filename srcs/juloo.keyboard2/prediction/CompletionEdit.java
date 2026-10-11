package juloo.keyboard2.prediction;

import android.view.inputmethod.InputConnection;

/** One editor transaction shared by dictionary, personal and model candidates. */
public final class CompletionEdit
{
  public final PredictionSnapshot before;
  public PredictionSnapshot after;
  public final String original, insertion;
  public final int start;
  public final boolean insertedSpace;
  private final int advanceSpace;

  public CompletionEdit(PredictionSnapshot snapshot, String text, boolean space)
  {
    before = snapshot;
    // Following letters may belong to an existing word we are inserting before.
    // Never include text after the cursor in the replacement range.
    original = snapshot.wordBefore;
    advanceSpace = space && snapshot.after.startsWith(" ") ? 1 : 0;
    insertedSpace = space && (snapshot.after.isEmpty() || !snapshot.wordAfter.isEmpty());
    insertion = text + (insertedSpace ? " " : "");
    start = snapshot.selection - snapshot.wordBefore.length();
    String expected = snapshot.before.substring(0, snapshot.before.length() - snapshot.wordBefore.length())
      + insertion + snapshot.after.substring(0, advanceSpace);
    after = new PredictionSnapshot(0,
        expected.substring(Math.max(0, expected.length() - 1024)),
        snapshot.after.substring(advanceSpace), start + insertion.length() + advanceSpace,
        PredictionSnapshot.Kind.WORD, snapshot.language);
  }

  public boolean apply(InputConnection ic)
  {
    if (ic == null) return false;
    ic.beginBatchEdit();
    try
    {
      if (!ic.finishComposingText()) return false;
      // Identical full words only need a boundary/cursor advance.
      if (original.equals(insertion))
        return ic.setSelection(after.selection, after.selection);
      if (insertion.equals(original + " "))
        return ic.commitText(" ", 1);
      if (!ic.setSelection(start, start + original.length())) return false;
      // Advance over an existing space without replacing or owning it.
      if (ic.commitText(insertion, 1 + advanceSpace)) return true;
      ic.setSelection(before.selection, before.selection);
      return false;
    }
    finally { ic.endBatchEdit(); }
  }

  public boolean confirm(PredictionSnapshot actual)
  {
    if (actual == null || actual.selection != after.selection || !actual.before.equals(after.before)
        || !actual.after.startsWith(after.after)) return false;
    after = actual;
    return true;
  }
}
