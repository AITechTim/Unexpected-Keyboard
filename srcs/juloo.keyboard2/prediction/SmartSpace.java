package juloo.keyboard2.prediction;

import android.view.inputmethod.InputConnection;

/** Own only the space this keyboard inserted, never an existing editor space. */
public final class SmartSpace
{
  private PredictionSnapshot expected;
  public void clear() { expected = null; }
  public void completed(CompletionEdit edit)
  { expected = edit.insertedSpace ? edit.after : null; }
  public void validate(PredictionSnapshot current)
  {
    if (expected != null && (current == null || !expected.language.equals(current.language)
        || !expected.matches(current.before, current.after, current.selection))) clear();
  }
  /** Before a hardware-style Enter event, remove only our trailing space. */
  public boolean remove(InputConnection connection, PredictionSnapshot current)
  { return replace(connection, current, ""); }
  public boolean applies(PredictionSnapshot current, String text)
  {
    if (expected == null || current == null || !expected.language.equals(current.language)
        || !expected.matches(current.before, current.after, current.selection)) { clear(); return false; }
    return text.length() == 1 && (".,!?;:…)]}\n".contains(text));
  }
  public boolean apply(InputConnection connection, PredictionSnapshot current, String text)
  {
    if (!applies(current, text)) return false;
    return replace(connection, current, text.equals("\n") ? text : text + " ");
  }
  private boolean replace(InputConnection connection, PredictionSnapshot current, String replacement)
  {
    if (!applies(current, "\n")) return false;
    clear();
    connection.beginBatchEdit();
    try
    {
      if (!connection.finishComposingText() || !connection.setSelection(current.selection - 1, current.selection)) return false;
      if (!connection.commitText(replacement, 1))
      { connection.setSelection(current.selection, current.selection); return false; }
      String before = current.before.substring(0, current.before.length() - 1) + replacement;
      if (replacement.endsWith(" ")) expected = new PredictionSnapshot(current.revision,
          before.substring(Math.max(0, before.length() - 1024)), current.after,
          current.selection - 1 + replacement.length(), PredictionSnapshot.Kind.WORD, current.language);
      return true;
    }
    finally { connection.endBatchEdit(); }
  }
}
