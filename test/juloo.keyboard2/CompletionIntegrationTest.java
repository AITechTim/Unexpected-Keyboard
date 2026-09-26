package juloo.keyboard2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.prediction.*;
import juloo.keyboard2.suggestions.Suggestions;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class CompletionIntegrationTest
{
  private PredictionController controller;
  private KeyEventHandler keys;
  private FakeEditor editor;
  private Suggestions suggestions;
  private void start(String text, int cursor)
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.initGlobalConfig(context.getSharedPreferences("test", 0), context.getResources(), false, Dictionaries.instance(context));
    Config config = Config.globalConfig();
    config.device_locales = DeviceLocales.load(context);
    config.split_layout = false;
    config.llm_predictions_enabled = false;
    config.learn_writing = false;
    editor = new FakeEditor(text, cursor);
    Handler handler = new Handler(Looper.getMainLooper());
    suggestions = new Suggestions(s -> {}, config);
    keys = new KeyEventHandler(new KeyEventHandler.IReceiver() {
      public void handle_event_key(KeyValue.Event ev) {}
      public void set_shift_state(boolean state, boolean lock) {}
      public void set_compose_pending(boolean pending) {}
      public void selection_state_changed(boolean ongoing) {}
      public InputConnection getCurrentInputConnection() { return editor.connection; }
      public Handler getHandler() { return handler; }
      public void set_suggestions(Suggestions s) {}
    }, suggestions);
    controller = new PredictionController(context, handler, () -> editor.connection, config, suggestions);
    keys.predictions = controller;
    EditorInfo info = new EditorInfo();
    info.packageName = "md.obsidian";
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.initialSelStart = info.initialSelEnd = cursor;
    info.setInitialSurroundingText(text);
    config.editor_config.refresh(info, context.getResources());
    keys.started(config);
    controller.start(info);
  }
  @After public void close() { if (controller != null) controller.close(); }

  @Test public void repeatedTapCannotReuseDictionaryReplacementWithoutModel()
  {
    start("to", 2);
    Candidate to = new Candidate("to", Candidate.Source.DICTIONARY, controller.snapshot());
    keys.candidate_entered(to);
    keys.candidate_entered(to);
    keys.candidate_entered(to);
    assertEquals("to ", editor.text);
    assertEquals(3, editor.start);
    keys.handle_backspace();
    assertEquals("to", editor.text);
  }

  @Test public void delayedSelectionCallbacksReconcileActualEditor()
  {
    start("to", 2);
    keys.selection_updated(2, 0, 0); // Editor remains at 2; callback is delayed.
    assertEquals(0, keys._typedword.cursor_relative());
    keys.candidate_entered(new Candidate("to", Candidate.Source.DICTIONARY, controller.snapshot()));
    keys.selection_updated(0, 2, 2);
    assertEquals("to ", editor.text);
    assertEquals("", keys._typedword.get());
    assertEquals(3, keys._typedword._cursor);
  }

  @Test public void actualCursorMoveOrSelectionRejectsPreviouslyShownCandidate()
  {
    start("go to", 5);
    Candidate candidate = new Candidate("to", Candidate.Source.DICTIONARY, controller.snapshot());
    editor.start = editor.end = 2;
    keys.candidate_entered(candidate);
    assertEquals("go to", editor.text);
    editor.start = 0; editor.end = 5;
    assertNull(controller.snapshot());
    keys.candidate_entered(candidate);
    assertEquals("go to", editor.text);
  }

  @Test public void emojiBeforeWordUsesUtf16AndWholePhraseUndo()
  {
    start("😀 see ", 7);
    keys.send_text("y");
    assertEquals(8, keys._typedword._cursor);
    keys.candidate_entered(new Candidate("you tomorrow morning", Candidate.Source.LEARNED, controller.snapshot()));
    assertEquals("😀 see you tomorrow morning ", editor.text);
    keys.handle_backspace();
    assertEquals("😀 see y", editor.text);
  }

  @Test public void failedEditorOperationDoesNotEnableUndo()
  {
    start("cof", 3);
    editor.failCommit = true;
    keys.candidate_entered(new Candidate("coffee", Candidate.Source.DICTIONARY, controller.snapshot()));
    assertEquals("cof", editor.text);
    assertEquals(0, editor.batchDepth);
  }
  private void type(String text)
  { for (int i = 0; i < text.length(); i++) keys.send_text(text.substring(i, i + 1)); }

  private PhraseMemory.Match learned(String context) throws Exception
  {
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<PhraseMemory.Match> result = new java.util.concurrent.atomic.AtomicReference<>();
    PhraseStore.get(RuntimeEnvironment.getApplication()).query(
        new PredictionSnapshot(1, context, "", context.length()), match -> { result.set(match); latch.countDown(); });
    assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
    return result.get();
  }

  private void enableLearning() throws Exception
  {
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean cleared = new java.util.concurrent.atomic.AtomicBoolean();
    PhraseStore.get(RuntimeEnvironment.getApplication()).clear(ok -> { cleared.set(ok); latch.countDown(); });
    assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
    assertTrue("clear succeeded", cleared.get());
    assertEquals("empty immediately after clear", 0, learned("see ").words.length);
    Config.globalConfig().learn_writing = true;
    Config.globalConfig().current_dictionary_name = "en";
    controller.configurationChanged();
  }

  private void settleLearning()
  { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(750)); }

  @Test public void localLearningWorksWithoutModelAndPauseKeepsExistingPhrases() throws Exception
  {
    start("", 0); enableLearning();
    type("see you tomorrow morning \nsee you tomorrow morning "); settleLearning();
    assertEquals("you tomorrow morning", learned("see ").phrase);
    Config.globalConfig().learning_paused = true; controller.configurationChanged();
    type("\nbring fresh coffee \nbring fresh coffee "); settleLearning();
    assertEquals(0, learned("bring ").words.length);
    assertEquals("you tomorrow morning", learned("see ").phrase);
  }

  @Test public void clipboardAndAcceptedPhrasesAreNotLearned() throws Exception
  {
    start("", 0); enableLearning();
    keys.paste_from_clipboard_pane("bring fresh coffee ");
    keys.paste_from_clipboard_pane("bring fresh coffee "); settleLearning();
    assertEquals(0, learned("bring ").words.length);
    for (int i = 0; i < 2; i++)
      keys.candidate_entered(new Candidate("see you tomorrow", Candidate.Source.LLM, controller.snapshot()));
    settleLearning();
    assertEquals(0, learned("see ").words.length);
  }

  @Test public void noPersonalizedLearningAndPasswordFieldsDoNotLearn() throws Exception
  {
    start("", 0); enableLearning();
    EditorInfo info = new EditorInfo();
    info.inputType = InputType.TYPE_CLASS_TEXT;
    info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    info.initialSelStart = info.initialSelEnd = editor.start;
    controller.start(info);
    type("bring fresh coffee \nbring fresh coffee "); settleLearning();
    assertEquals(0, learned("bring ").words.length);
    info.inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
    info.imeOptions = 0; info.initialSelStart = info.initialSelEnd = editor.start;
    controller.start(info);
    type("see you tomorrow \nsee you tomorrow "); settleLearning();
    assertEquals(0, learned("see ").words.length);
  }

  @Test public void immediateUndoAndClearDiscardPendingLearning() throws Exception
  {
    start("", 0); enableLearning();
    type("see you tomorrow \nsee you tomorrow ");
    keys.handle_backspace(); settleLearning();
    assertEquals(0, learned("see ").words.length);
    type("\nbring fresh coffee \nbring fresh coffee ");
    controller.configurationChanged();
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    PhraseStore.get(RuntimeEnvironment.getApplication()).clear(ok -> latch.countDown());
    assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
    settleLearning();
    assertEquals(0, learned("bring ").words.length);
  }

  @Test public void acceptsWordsWhenEditorOnlySupportsLegacyQueries()
  {
    start("to", 2);
    editor.legacyQueries = true;
    keys.candidate_entered(new Candidate("to", Candidate.Source.DICTIONARY, controller.snapshot()));
    assertEquals("to ", editor.text);
    keys.handle_backspace();
    assertEquals("to", editor.text);
  }

}
