package juloo.keyboard2.prediction;

import java.io.File;
import android.content.Context;
import juloo.keyboard2.prediction.runtime.NativePredictor;

public final class LlamaPredictionEngine implements PredictionEngine
{
  private final Context context;
  private volatile NativePredictor nativePredictor;
  private boolean failed;
  private String loadedPath;
  private final java.util.concurrent.atomic.AtomicLong cancellation = new java.util.concurrent.atomic.AtomicLong();

  public LlamaPredictionEngine(Context context) { this.context = context; }

  public String[] predict(PredictionSnapshot snapshot)
  {
    File model = ModelStore.forLanguage(context, snapshot.language);
    long generation = cancellation.get();
    if (loadedPath != null && !loadedPath.equals(model.getAbsolutePath())) close();
    if (failed || !model.isFile()) return new String[0];
    try
    {
      if (nativePredictor == null)
      {
        loadedPath = model.getAbsolutePath();
        nativePredictor = new NativePredictor();
        if (!nativePredictor.load(model.getAbsolutePath()))
          throw new IllegalStateException("Model load failed");
      }
      if (cancellation.get() != generation) return new String[0];
      boolean bilingual = model.equals(ModelStore.file(context));
      boolean phrase = snapshot.kind == PredictionSnapshot.Kind.PHRASE;
      return nativePredictor.predict(snapshot.context, snapshot.prefix, phrase,
          bilingual ? (phrase ? 1000 : 500) : (phrase ? 500 : 250),
          bilingual ? snapshot.language : "");
    }
    catch (LinkageError | RuntimeException e)
    {
      close();
      failed = true;
      ModelStore.runtimeFailed = true;
      return new String[0];
    }
  }

  public void cancel()
  {
    cancellation.incrementAndGet();
    NativePredictor p = nativePredictor;
    if (p != null) p.cancel();
  }
  public void reset() { if (nativePredictor != null) nativePredictor.reset(); }
  public void close()
  {
    NativePredictor p = nativePredictor;
    nativePredictor = null;
    if (p != null) p.close();
    failed = false;
    loadedPath = null;
  }
}
