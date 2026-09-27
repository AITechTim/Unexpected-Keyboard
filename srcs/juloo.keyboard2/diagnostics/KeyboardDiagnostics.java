package juloo.keyboard2.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.util.AtomicFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in, bounded metadata only. Never serialize InputConnection arguments/results. */
public final class KeyboardDiagnostics
{
  public static final String[] MODES = {"baseline", "no_native", "no_reads", "deferred"};
  private static volatile String mode = "baseline";
  private static volatile boolean recording;
  private static boolean loaded;
  private static final int LIMIT = 256;
  private static JSONArray events = new JSONArray();
  private static File file;
  private static boolean persistPending;
  private static final Handler handler = new Handler(Looper.getMainLooper());
  private static final Runnable persist = KeyboardDiagnostics::persist;

  public static void configure(Context context, SharedPreferences prefs)
  {
    file = new File(context.getCacheDir(), "keyboard-diagnostics.json");
    String requested = prefs.getString("keyboard_diagnostic_mode", "baseline");
    mode = java.util.Arrays.asList(MODES).contains(requested) ? requested : "baseline";
    boolean enabled = prefs.getBoolean("keyboard_diagnostic_record", false);
    if (!loaded) {
      loaded = true;
      try {
        byte[] bytes = new AtomicFile(file).readFully();
        events = new JSONObject(new String(bytes, "UTF-8")).getJSONArray("events");
        while (events.length() > LIMIT) events.remove(0);
      } catch (Exception ignored) { events = new JSONArray(); }
    }
    recording = enabled;
    event("configuration", 0, 0);
  }

  public static boolean noReads() { return mode.equals("no_reads"); }
  public static boolean noNative() { return noReads() || mode.equals("no_native"); }
  public static boolean deferred() { return mode.equals("deferred"); }

  /** Event names are developer constants. Numeric fields hold flags/counts/offsets only. */
  public static synchronized void event(String name, long first, long second)
  {
    if (!recording) return;
    try {
      JSONObject e = new JSONObject();
      e.put("event", name); e.put("elapsed_ms", SystemClock.elapsedRealtime());
      e.put("mode", mode); e.put("a", first); e.put("b", second);
      events.put(e);
      while (events.length() > LIMIT) events.remove(0);
      // At most one pending disk write. A Chrome renderer crash leaves the IME alive.
      if (!persistPending) { persistPending = true; handler.postDelayed(persist, 100); }
    } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
  }

  public static synchronized String export()
  {
    try {
      JSONObject result = new JSONObject();
      result.put("schema", 1); result.put("keyboard_version", "2.1.1");
      result.put("model", android.os.Build.MODEL); result.put("android", android.os.Build.VERSION.RELEASE);
      result.put("sdk", android.os.Build.VERSION.SDK_INT); result.put("mode", mode);
      result.put("recording", recording); result.put("events", events);
      return result.toString(2);
    } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
  }

  private static void persist()
  {
    persistPending = false;
    if (file == null) return;
    AtomicFile target = new AtomicFile(file);
    FileOutputStream stream = null;
    try { stream = target.startWrite(); stream.write(export().getBytes("UTF-8")); target.finishWrite(stream); }
    catch (Exception ignored) { if (stream != null) target.failWrite(stream); }
  }

  public static synchronized void clear()
  { handler.removeCallbacks(persist); persistPending = false; events = new JSONArray(); if (file != null) new AtomicFile(file).delete(); }

  public static void memory(String phase)
  {
    if (!recording) return;
    event("memory_pss_kb", android.os.Debug.getPss(), 0);
    event(phase, android.os.Debug.getNativeHeapAllocatedSize() / 1024,
        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);
  }

  public static InputConnection wrap(InputConnection connection)
  {
    if (connection == null || (!recording && !noReads())) return connection;
    return (InputConnection)Proxy.newProxyInstance(InputConnection.class.getClassLoader(),
      new Class<?>[]{InputConnection.class}, (proxy, method, args) -> {
        String name = method.getName();
        boolean read = name.equals("getSurroundingText") || name.equals("getTextBeforeCursor")
          || name.equals("getTextAfterCursor") || name.equals("getSelectedText")
          || name.equals("getExtractedText") || name.equals("getCursorCapsMode");
        if (read) {
          event(noReads() ? "read_blocked" : "read_start_" + name, 0, 0);
          if (noReads()) return method.getReturnType() == int.class ? 0 : null;
        }
        if (name.equals("sendKeyEvent")) {
          KeyEvent key = (KeyEvent)args[0];
          // Never log printable keycodes or characters.
          event(key.getKeyCode() == KeyEvent.KEYCODE_DEL ? "delete_event" : "key_event", key.getAction(), 0);
        }
        if (name.equals("setSelection")) event("set_selection", (int)args[0], (int)args[1]);
        long start = SystemClock.uptimeMillis();
        try {
          Object result = method.invoke(connection, args);
          if (read) event("read_end_" + name, SystemClock.uptimeMillis() - start, result == null ? 0 : 1);
          return result;
        } catch (InvocationTargetException failure) {
          if (read) event("read_error", 0, 0);
          throw failure.getCause();
        }
      });
  }
}
