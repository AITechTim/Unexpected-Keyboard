package juloo.keyboard2.prediction;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.UserManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.concurrent.Executors;
import juloo.keyboard2.Config;
import juloo.keyboard2.suggestions.Suggestions;

/** UI-thread editor coordination with a single, latest-request-only worker. */
public final class PredictionController implements AutoCloseable
{
  public interface Host { InputConnection connection(); }
  private final Context context;
  private final Handler handler;
  private final Host host;
  private final Config config;
  private final Suggestions suggestions;
  private final PredictionWorker worker;
  private boolean active, fieldAllowed, closed, suspended;
  private int selectionStart = -1, selectionEnd = -1;
  private long revision;
  private final Runnable request = this::capture;
  private final Runnable unload = this::unload;

  public PredictionController(Context c, Handler h, Host host, Config config, Suggestions suggestions)
  {
    context = c; handler = h; this.host = host; this.config = config; this.suggestions = suggestions;
    worker = new PredictionWorker(new LlamaPredictionEngine(c),
        Executors.newSingleThreadExecutor(), (snapshot, words) -> handler.post(() -> publish(snapshot, words)));
  }

  private void unload() { worker.unload(); }

  public void start(EditorInfo info)
  {
    active = true;
    suspended = false;
    fieldAllowed = PredictionEligibility.allows(info.inputType, info.imeOptions);
    selectionStart = info.initialSelStart;
    selectionEnd = info.initialSelEnd;
    worker.invalidate(true);
    handler.removeCallbacks(unload);
    changed();
  }

  public void selection(int start, int end)
  {
    boolean moved = selectionStart != start || selectionEnd != end;
    selectionStart = start; selectionEnd = end;
    if (moved) changed();
  }

  public void configurationChanged()
  {
    worker.invalidate(true);
    changed();
  }

  public void replaced(PredictionSnapshot snapshot, String insertion)
  {
    selectionStart = selectionEnd = snapshot.selection - snapshot.prefix.length() + insertion.length();
    changed();
  }

  public void changed()
  {
    if (closed) return;
    revision++;
    handler.removeCallbacks(request);
    worker.invalidate(false);
    suggestions.clear_predictions();
    if (enabled()) handler.postDelayed(request, 50);
    else if (!config.llm_predictions_enabled) worker.unload();
  }

  private boolean enabled()
  {
    if (suspended || !active || !fieldAllowed || !config.suggestions_enabled || config.split_layout
        || !config.llm_predictions_enabled || !ModelStore.supported()) return false;
    UserManager users = (UserManager)context.getSystemService(Context.USER_SERVICE);
    if (Build.VERSION.SDK_INT >= 24 && !users.isUserUnlocked()) return false;
    String language = config.current_dictionary_name;
    if (language == null && config.device_locales.default_ != null)
      language = config.device_locales.default_.lang_tag;
    if (language != null && !language.equals("en") && !language.startsWith("en-")
        && !language.startsWith("en_")) return false;
    return ModelStore.file(context).isFile();
  }

  private PredictionSnapshot read()
  {
    InputConnection ic = host.connection();
    if (ic == null || selectionStart != selectionEnd) return null;
    if (Build.VERSION.SDK_INT >= 31)
    {
      android.view.inputmethod.SurroundingText text = ic.getSurroundingText(1024, 32, 0);
      if (text == null || text.getSelectionStart() != text.getSelectionEnd()) return null;
      int cursor = text.getSelectionStart();
      CharSequence content = text.getText();
      if (cursor < 0 || cursor > content.length()) return null;
      int absolute = text.getOffset() < 0 ? selectionStart : text.getOffset() + cursor;
      if (absolute < 0) return null;
      return new PredictionSnapshot(revision,
          content.subSequence(Math.max(0, cursor - 1024), cursor).toString(),
          content.subSequence(cursor, Math.min(content.length(), cursor + 32)).toString(), absolute);
    }
    if (selectionStart < 0) return null;
    CharSequence before = ic.getTextBeforeCursor(1024, 0);
    CharSequence after = ic.getTextAfterCursor(32, 0);
    CharSequence selected = ic.getSelectedText(0);
    if (before == null || after == null || (selected != null && selected.length() > 0)) return null;
    return new PredictionSnapshot(revision,
        before.subSequence(Math.max(0, before.length() - 1024), before.length()).toString(),
        after.subSequence(0, Math.min(32, after.length())).toString(), selectionStart);
  }

  private void capture()
  {
    if (!enabled()) return;
    PredictionSnapshot snapshot = read();
    if (snapshot == null || !snapshot.eligible()) return;
    worker.submit(snapshot);
  }

  private void publish(PredictionSnapshot snapshot, String[] words)
  {
    if (!closed && enabled() && snapshot.revision == revision)
    {
      PredictionSnapshot current = read();
      if (current != null && snapshot.matches(current.before, current.after, current.selection))
        suggestions.set_predictions(snapshot, words);
    }
  }

  public boolean canAccept(Candidate candidate)
  {
    if (candidate == null || candidate.snapshot == null || !enabled()
        || candidate.snapshot.revision != revision) return false;
    PredictionSnapshot current = read();
    return current != null && candidate.snapshot.matches(current.before, current.after, current.selection);
  }

  public boolean matchesForUndo(PredictionSnapshot snapshot)
  {
    PredictionSnapshot current = read();
    return current != null && snapshot.matches(current.before, current.after, current.selection);
  }

  public void finish()
  {
    if (closed) return;
    active = false;
    changed();
    worker.invalidate(true);
    // Erase field context immediately; retain weights briefly for the next field.
    handler.removeCallbacks(unload);
    handler.postDelayed(unload, 60000);
  }

  public void trim()
  {
    suspended = true;
    changed();
    worker.unload();
  }

  @Override public void close()
  {
    if (closed) return;
    finish(); closed = true;
    handler.removeCallbacks(unload);
    worker.close();
  }
}
