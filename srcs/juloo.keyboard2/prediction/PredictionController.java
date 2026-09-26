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
  private final PhraseStore store;
  private final PhraseLearner learner = new PhraseLearner();
  private final Runnable learn = this::flushLearning;
  private void flushLearning()
  { if (localEnabled() && !config.learning_paused) store.learn(learner.take(read())); }
  private final Runnable phraseRequest = this::capturePhrase;
  private boolean active, fieldAllowed, closed, suspended;
  private int selectionStart = -1, selectionEnd = -1;
  private long revision, changedAt;
  private final Runnable request = this::capture;
  private final Runnable unload = this::unload;

  public PredictionController(Context c, Handler h, Host host, Config config, Suggestions suggestions)
  {
    context = c; handler = h; this.host = host; this.config = config; this.suggestions = suggestions;
    store = PhraseStore.get(c);
    worker = new PredictionWorker(new LlamaPredictionEngine(c),
        Executors.newSingleThreadExecutor(), (snapshot, words) -> handler.post(() -> publish(snapshot, words)));
  }

  private void unload() { worker.unload(); }

  public void start(EditorInfo info)
  {
    stopLearning();
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
    stopLearning();
    store.invalidate();
    worker.invalidate(true);
    changed();
  }

  public void replaced(PredictionSnapshot snapshot)
  { selectionStart = selectionEnd = snapshot.selection; }

  public PredictionSnapshot snapshot() { return active ? read() : null; }

  public void consume()
  {
    stopLearning();
    revision++;
    handler.removeCallbacks(request); handler.removeCallbacks(phraseRequest);
    worker.invalidate(false); suggestions.invalidate();
  }

  public void stopLearning()
  { handler.removeCallbacks(learn); learner.reset(); }

  public void typed(PredictionSnapshot before, String text, boolean manual)
  {
    if (before != null) selectionStart = selectionEnd = before.selection + text.length();
    if (!manual || !localEnabled() || config.learning_paused) { stopLearning(); return; }
    learner.typed(before, read(), text);
    handler.removeCallbacks(learn); handler.postDelayed(learn, 700);
  }

  public void changed()
  {
    if (closed) return;
    revision++;
    changedAt = android.os.SystemClock.uptimeMillis();
    handler.removeCallbacks(request); handler.removeCallbacks(phraseRequest);
    worker.invalidate(false);
    suggestions.clear_predictions();
    suggestions.bind(active ? read() : null);
    if (localEnabled())
    {
      PredictionSnapshot snapshot = read();
      long epoch = store.revision();
      if (snapshot != null && snapshot.eligible()) store.query(snapshot, match -> handler.post(() -> {
        if (!localEnabled() || store.revision() != epoch || !current(snapshot)) return;
        suggestions.set_learned(snapshot, match.words);
        if (config.phrase_predictions_enabled && match.phrase != null)
          suggestions.set_phrase(snapshot, match.phrase, Candidate.Source.LEARNED);
      }));
    }
    if (enabled()) handler.postDelayed(request, 50);
    else if (!config.llm_predictions_enabled) worker.unload();
  }

  private boolean eligibleField()
  {
    if (suspended || !active || !fieldAllowed || !config.suggestions_enabled || config.split_layout) return false;
    UserManager users = (UserManager)context.getSystemService(Context.USER_SERVICE);
    if (Build.VERSION.SDK_INT >= 24 && !users.isUserUnlocked()) return false;
    String language = config.current_dictionary_name;
    if (language == null && config.device_locales != null && config.device_locales.default_ != null)
      language = config.device_locales.default_.lang_tag;
    return language == null || language.equals("en") || language.startsWith("en-") || language.startsWith("en_");
  }

  private boolean localEnabled() { return eligibleField() && config.learn_writing; }

  private boolean enabled()
  {
    return eligibleField() && config.llm_predictions_enabled && ModelStore.supported()
      && ModelStore.file(context).isFile();
  }

  private PredictionSnapshot read()
  {
    InputConnection ic = host.connection();
    if (ic == null) return null;
    if (Build.VERSION.SDK_INT >= 31)
    {
      android.view.inputmethod.SurroundingText text = ic.getSurroundingText(1024, 32, 0);
      if (text != null)
      {
        if (text.getSelectionStart() != text.getSelectionEnd()) return null;
        int cursor = text.getSelectionStart();
        CharSequence content = text.getText();
        if (cursor < 0 || cursor > content.length()) return null;
        int absolute = text.getOffset() < 0 ? selectionStart : text.getOffset() + cursor;
        if (absolute < 0) return null;
        return new PredictionSnapshot(revision,
            content.subSequence(Math.max(0, cursor - 1024), cursor).toString(),
            content.subSequence(cursor, Math.min(content.length(), cursor + 32)).toString(), absolute);
      }
      // Some editors only implement the older before/after cursor queries.
    }
    if (selectionStart < 0 || selectionStart != selectionEnd) return null;
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

  private void capturePhrase()
  {
    if (!enabled() || !config.phrase_predictions_enabled || suggestions.phrase != null) return;
    PredictionSnapshot snapshot = read();
    if (snapshot != null && snapshot.eligible()) worker.submit(snapshot.forPhrase());
  }

  private boolean current(PredictionSnapshot snapshot)
  {
    if (closed || snapshot.revision != revision) return false;
    PredictionSnapshot current = read();
    return current != null && snapshot.matches(current.before, current.after, current.selection);
  }

  private void publish(PredictionSnapshot snapshot, String[] words)
  {
    if (!enabled() || !current(snapshot)) return;
    if (snapshot.kind == PredictionSnapshot.Kind.PHRASE)
    {
      if (config.phrase_predictions_enabled && words.length > 0)
        suggestions.set_phrase(snapshot, words[0], Candidate.Source.LLM);
    }
    else
    {
      suggestions.set_predictions(snapshot, words);
      // Word inference always finishes first. The quiet period is at least 250ms.
      if (config.phrase_predictions_enabled) handler.postDelayed(phraseRequest,
          Math.max(0, changedAt + 250 - android.os.SystemClock.uptimeMillis()));
    }
  }

  public boolean canAccept(Candidate candidate)
  {
    return active && candidate != null && candidate.snapshot != null
      && candidate.snapshot.wordBefore.length() < 1024
      && candidate.snapshot.wordAfter.length() < 32 && current(candidate.snapshot);
  }

  public boolean matchesForUndo(PredictionSnapshot snapshot)
  {
    PredictionSnapshot current = read();
    return current != null && snapshot.matches(current.before, current.after, current.selection);
  }

  public void finish()
  {
    if (closed) return;
    stopLearning();
    active = false;
    changed();
    worker.invalidate(true);
    // Erase field context immediately; retain weights briefly for the next field.
    handler.removeCallbacks(unload);
    handler.postDelayed(unload, 60000);
  }

  public void trim()
  {
    stopLearning();
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
