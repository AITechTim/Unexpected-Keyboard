package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import juloo.keyboard2.diagnostics.KeyboardDiagnostics;
import juloo.keyboard2.prediction.FakeEditor;
import juloo.keyboard2.prediction.PredictionController;
import juloo.keyboard2.suggestions.Suggestions;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
public class KeyboardDiagnosticTest
{
  private Context context;
  private SharedPreferences prefs;
  private final List<String> calls = new ArrayList<>();
  private final FakeEditor editor = new FakeEditor("secret 😀 status", 16);
  private InputConnection connection;
  private Config config;
  private void mode(String mode, boolean record)
  {
    prefs.edit().putString("keyboard_diagnostic_mode", mode).putBoolean("keyboard_diagnostic_record", record).commit();
    KeyboardDiagnostics.configure(context, prefs);
    connection = KeyboardDiagnostics.wrap((InputConnection)Proxy.newProxyInstance(InputConnection.class.getClassLoader(), new Class<?>[]{InputConnection.class}, (proxy, method, args) -> {
      if (method.getName().equals("sendKeyEvent")) calls.add("key:"+((KeyEvent)args[0]).getAction());
      else calls.add(method.getName());
      return method.invoke(editor.connection, args);
    }));
  }
  @Before public void setup()
  {
    context = RuntimeEnvironment.getApplication();
    prefs = context.getSharedPreferences("diagnostic-test", 0);prefs.edit().clear().commit();
    mode("baseline", false);KeyboardDiagnostics.clear();
    Config.initGlobalConfig(prefs, context.getResources(), false, null);
    config = Config.globalConfig();config.split_layout=false;config.suggestions_enabled=true;
    config.editor_config.initial_sel_start=16;config.editor_config.initial_sel_end=16;
    config.editor_config.initial_text_before_cursor="status";
    config.editor_config.initial_text_after_cursor="";
  }
  @After public void finish() {KeyboardDiagnostics.clear();}

  @Test public void metadataIsOptInBoundedAndDoesNotContainEditorTextOrPrintableKeys() throws Exception
  {
    connection.getTextBeforeCursor(100, 0);
    assertEquals(0,new JSONObject(KeyboardDiagnostics.export()).getJSONArray("events").length());
    mode("baseline",true);
    assertEquals("secret 😀 status",connection.getTextBeforeCursor(100,0));
    connection.commitText("unlogged confidential insertion",1);
    connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_Z));
    for(int i=0;i<400;i++) KeyboardDiagnostics.event("selection",i,i);
    String export=KeyboardDiagnostics.export();
    assertEquals(256,new JSONObject(export).getJSONArray("events").length());
    assertFalse(export.contains("secret"));assertFalse(export.contains("confidential"));assertFalse(export.contains("KEYCODE_Z"));
  }
  @Test public void noReadsBlocksOptionalQueriesButStillDeliversDeletion()
  {
    mode("no_reads",true);calls.clear();
    assertNull(connection.getSurroundingText(1024,32,0));
    assertNull(connection.getTextBeforeCursor(20,0));assertNull(connection.getTextAfterCursor(20,0));
    assertEquals(0,connection.getCursorCapsMode(1));
    connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));
    connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL));
    assertEquals(Arrays.asList("key:0","key:1"),calls);
    assertTrue(KeyboardDiagnostics.noNative());
  }
  @Test public void deferredPredictionReadsCoalesceAndStopOnFinish()
  {
    mode("deferred",false);
    Suggestions suggestions=new Suggestions(s->{},config);
    PredictionController controller=new PredictionController(context,new Handler(Looper.getMainLooper()),()->connection,config,suggestions);
    EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.initialSelStart=info.initialSelEnd=16;
    try {
      controller.start(info);calls.clear();
      for(int i=0;i<10;i++)controller.changed();
      assertTrue(calls.isEmpty());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
      assertEquals(Collections.singletonList("getSurroundingText"),calls);
      calls.clear();controller.changed();controller.finish();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
      assertTrue(calls.isEmpty());
    } finally {controller.close();}
  }
  @Test public void deletePairHasNoInterleavedPredictionRead()
  {
    mode("deferred",false);
    KeyEventHandler.IReceiver receiver=new KeyEventHandler.IReceiver(){
      public void handle_event_key(KeyValue.Event event){} public void set_shift_state(boolean a,boolean b){}
      public void set_compose_pending(boolean a){} public void selection_state_changed(boolean a){}
      public InputConnection getCurrentInputConnection(){return connection;}
      public Handler getHandler(){return new Handler(Looper.getMainLooper());}
      public void set_suggestions(Suggestions suggestions){}
    };
    Suggestions suggestions=new Suggestions(receiver,config);
    KeyEventHandler keys=new KeyEventHandler(receiver,suggestions);
    PredictionController controller=new PredictionController(context,new Handler(Looper.getMainLooper()),()->connection,config,suggestions);
    keys.predictions=controller;
    EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_TEXT;info.initialSelStart=info.initialSelEnd=16;
    try {
      controller.start(info);keys.started(config);calls.clear();
      keys.send_key_down_up(KeyEvent.KEYCODE_DEL);
      assertEquals(Arrays.asList("key:0","key:1"),calls);
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
      assertEquals(Arrays.asList("key:0","key:1","getSurroundingText"),calls);
    } finally {keys.finished();controller.close();}
  }
  @Test public void typedWordCancelsStaleDeferredReadsAcrossInputFinish()
  {
    mode("deferred",false);
    CurrentlyTypedWord word=new CurrentlyTypedWord(new Handler(Looper.getMainLooper()),s->{});
    word.started(config,connection);calls.clear();
    word.selection_updated(16,15,15);word.selection_updated(15,14,14);
    assertTrue(calls.isEmpty());word.finished();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    assertTrue(calls.isEmpty());
  }
}
