package juloo.keyboard2.prediction.runtime;

import java.nio.charset.StandardCharsets;

/** Load, predict, reset and close on one worker. cancel() may run on the UI thread. */
public final class NativePredictor implements AutoCloseable
{
  private long handle;

  public NativePredictor()
  {
    System.loadLibrary("keyboard_prediction");
    handle = create();
    if (handle == 0) throw new IllegalStateException("Unable to allocate predictor");
  }

  public boolean load(String path) { return load(handle, path); }

  public String[] predict(String context, String prefix, boolean phrase)
  {
    byte[][] words = predict(handle, context.getBytes(StandardCharsets.UTF_8),
        prefix.getBytes(StandardCharsets.UTF_8), phrase);
    String[] result = new String[words.length];
    for (int i = 0; i < words.length; i++)
      result[i] = new String(words[i], StandardCharsets.UTF_8);
    return result;
  }

  public synchronized void cancel() { if (handle != 0) cancel(handle); }
  public void reset() { reset(handle); }

  @Override public void close()
  {
    long old;
    synchronized (this) { old = handle; handle = 0; }
    if (old != 0) destroy(old);
  }

  private static native long create();
  private static native boolean load(long handle, String path);
  private static native byte[][] predict(long handle, byte[] context, byte[] prefix, boolean phrase);
  private static native void cancel(long handle);
  private static native void reset(long handle);
  private static native void destroy(long handle);
}
