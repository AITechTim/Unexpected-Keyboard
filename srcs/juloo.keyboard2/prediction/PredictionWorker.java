package juloo.keyboard2.prediction;

import java.util.concurrent.ExecutorService;

/** Bounded queue: one running prediction and one replaceable pending snapshot. */
public final class PredictionWorker implements AutoCloseable
{
  public interface Result { void accept(PredictionSnapshot snapshot, String[] words); }
  private final PredictionEngine engine;
  private final ExecutorService executor;
  private final Result result;
  private PredictionSnapshot pending;
  private boolean running, resetNeeded, closed;
  private long generation;

  public PredictionWorker(PredictionEngine engine, ExecutorService executor, Result result)
  { this.engine = engine; this.executor = executor; this.result = result; }

  public synchronized void submit(PredictionSnapshot snapshot)
  {
    if (closed) return;
    pending = snapshot;
    if (!running)
    {
      running = true;
      executor.execute(this::drain);
    }
  }

  public synchronized void invalidate(boolean reset)
  {
    if (closed) return;
    ++generation;
    pending = null;
    resetNeeded |= reset;
    engine.cancel();
    if (reset) executor.execute(engine::reset);
  }

  public synchronized void unload()
  {
    if (!closed) executor.execute(engine::close);
  }

  private void drain()
  {
    for (;;)
    {
      PredictionSnapshot snapshot;
      long version;
      boolean reset;
      synchronized (this)
      {
        snapshot = pending; pending = null;
        if (snapshot == null || closed) { running = false; return; }
        version = generation;
        reset = resetNeeded; resetNeeded = false;
      }
      if (reset) engine.reset();
      String[] words;
      try { words = engine.predict(snapshot); }
      catch (RuntimeException | LinkageError e) { words = new String[0]; engine.close(); }
      synchronized (this)
      {
        if (!closed && generation == version) result.accept(snapshot, words);
      }
    }
  }

  @Override public synchronized void close()
  {
    if (closed) return;
    invalidate(true);
    closed = true;
    executor.execute(engine::close);
    executor.shutdown();
  }
}
