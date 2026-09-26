package juloo.keyboard2.prediction;

import android.view.inputmethod.InputConnection;

/** One editor transaction shared by dictionary, personal and model candidates. */
public final class CompletionEdit
{
  public final PredictionSnapshot before;
  public PredictionSnapshot after;
  public final String original, insertion;
  public final int start;

  public CompletionEdit(PredictionSnapshot snapshot, String text, boolean space)
  {
    before = snapshot;
    int suffix = snapshot.wordAfter.length();
    String remaining = snapshot.after.substring(suffix);
    boolean consumeSpace = space && remaining.startsWith(" ");
    original = snapshot.wordBefore + snapshot.after.substring(0, suffix + (consumeSpace ? 1 : 0));
    insertion = text + (space && (remaining.isEmpty() || consumeSpace) ? " " : "");
    start = snapshot.selection - snapshot.wordBefore.length();
    String expected = snapshot.before.substring(0, snapshot.before.length() - snapshot.wordBefore.length()) + insertion;
    after = new PredictionSnapshot(0,
        expected.substring(Math.max(0, expected.length() - 1024)),
        remaining.substring(consumeSpace ? 1 : 0), start + insertion.length());
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
      if (original.equals(before.wordBefore) && insertion.equals(original + " "))
        return ic.commitText(" ", 1);
      if (!ic.setSelection(start, start + original.length())) return false;
      return ic.commitText(insertion, 1);
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

  public boolean undo(InputConnection ic)
  {
    if (ic == null) return false;
    ic.beginBatchEdit();
    try
    {
      return ic.finishComposingText()
        && ic.setSelection(start, start + insertion.length())
        && ic.commitText(original, 1)
        && ic.setSelection(before.selection, before.selection);
    }
    finally { ic.endBatchEdit(); }
  }
}
