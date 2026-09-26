package juloo.keyboard2.prediction;

import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import java.lang.reflect.*;

/** Editor with independently delivered selection callbacks, like remote IMEs. */
public final class FakeEditor implements InvocationHandler
{
  public String text;
  public int start, end, batchDepth, commits;
  public boolean composing, failSelection, failCommit, legacyQueries;
  public final InputConnection connection = (InputConnection)Proxy.newProxyInstance(
      InputConnection.class.getClassLoader(), new Class<?>[]{InputConnection.class}, this);
  public FakeEditor(String text, int cursor) { this.text = text; start = end = cursor; }
  public PredictionSnapshot snapshot(long revision)
  { return new PredictionSnapshot(revision, text.substring(Math.max(0, start - 1024), start), text.substring(end, Math.min(text.length(), end + 32)), start); }
  public Object invoke(Object proxy, Method method, Object[] args)
  {
    switch (method.getName())
    {
      case "beginBatchEdit": batchDepth++; return true;
      case "endBatchEdit": batchDepth--; return true;
      case "finishComposingText": composing = false; return true;
      case "setSelection":
        if (failSelection) return false;
        start = (int)args[0]; end = (int)args[1]; return true;
      case "commitText":
        if (failCommit) return false;
        String insertion = args[0].toString();
        if (composing) start = 0;
        text = text.substring(0, start) + insertion + text.substring(end);
        start += insertion.length(); end = start; commits++; return true;
      case "getTextBeforeCursor": return text.substring(Math.max(0, start - (int)args[0]), start);
      case "getTextAfterCursor": return text.substring(end, Math.min(text.length(), end + (int)args[0]));
      case "getSelectedText": return text.substring(start, end);
      case "getSurroundingText":
        if (legacyQueries) return null;
        int offset = Math.max(0, start - (int)args[0]);
        return new SurroundingText(text.substring(offset, Math.min(text.length(), end + (int)args[1])), start - offset, end - offset, offset);
      case "toString": return "FakeEditor";
    }
    if (method.getReturnType() == boolean.class) return true;
    if (method.getReturnType() == int.class) return 0;
    return null;
  }
}
