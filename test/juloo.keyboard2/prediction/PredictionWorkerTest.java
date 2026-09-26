package juloo.keyboard2.prediction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class PredictionWorkerTest
{
  static class ManualExecutor extends AbstractExecutorService
  {
    ArrayDeque<Runnable> queue = new ArrayDeque<>();
    boolean stopped;
    public void execute(Runnable r) { queue.add(r); }
    public void shutdown() { stopped = true; }
    public List<Runnable> shutdownNow() { stopped = true; return new ArrayList<>(); }
    public boolean isShutdown() { return stopped; }
    public boolean isTerminated() { return stopped && queue.isEmpty(); }
    public boolean awaitTermination(long time, TimeUnit unit) { return isTerminated(); }
    void runAll() { while (!queue.isEmpty()) queue.remove().run(); }
  }
  static class Engine implements PredictionEngine
  {
    List<Long> requested = new ArrayList<>();
    Runnable during;
    int resets, cancels, closes;
    public String[] predict(PredictionSnapshot s)
    {
      requested.add(s.revision);
      if (during != null) { Runnable r = during; during = null; r.run(); }
      return new String[]{"word"};
    }
    public void cancel() { cancels++; }
    public void reset() { resets++; }
    public void close() { closes++; }
  }
  private PredictionSnapshot snapshot(long revision)
  { return new PredictionSnapshot(revision, "test ", "", 5); }

  @Test public void coalescesPendingWorkAndDropsInFlightStaleResult()
  {
    ManualExecutor executor = new ManualExecutor();
    Engine engine = new Engine();
    List<Long> delivered = new ArrayList<>();
    PredictionWorker worker = new PredictionWorker(engine, executor, (s, w) -> delivered.add(s.revision));
    worker.submit(snapshot(1)); worker.submit(snapshot(2));
    engine.during = () -> {
      worker.invalidate(false);
      worker.submit(snapshot(3)); worker.submit(snapshot(4));
    };
    executor.runAll();
    assertEquals(java.util.Arrays.asList(2L, 4L), engine.requested);
    assertEquals(java.util.Arrays.asList(4L), delivered);
    assertEquals(1, engine.cancels);
  }

  @Test public void fieldSwitchClearsContextAndCloseDropsPendingWork()
  {
    ManualExecutor executor = new ManualExecutor();
    Engine engine = new Engine();
    List<Long> delivered = new ArrayList<>();
    PredictionWorker worker = new PredictionWorker(engine, executor, (s, w) -> delivered.add(s.revision));
    worker.submit(snapshot(1));
    worker.invalidate(true);
    worker.submit(snapshot(2));
    executor.runAll();
    assertEquals(java.util.Arrays.asList(2L), delivered);
    assertTrue(engine.resets > 0);
    worker.submit(snapshot(3)); worker.close(); worker.submit(snapshot(4));
    executor.runAll();
    assertEquals(java.util.Arrays.asList(2L), delivered);
    assertTrue(engine.closes > 0);
    assertTrue(executor.isShutdown());
  }

  @Test public void unavailableRuntimeDoesNotPoisonQueue()
  {
    ManualExecutor executor = new ManualExecutor();
    Engine engine = new Engine();
    List<Integer> delivered = new ArrayList<>();
    PredictionWorker worker = new PredictionWorker(engine, executor, (s, w) -> delivered.add(w.length));
    engine.during = () -> { throw new UnsatisfiedLinkError("unavailable"); };
    worker.submit(snapshot(1)); executor.runAll();
    worker.submit(snapshot(2)); executor.runAll();
    assertEquals(java.util.Arrays.asList(0, 1), delivered);
  }
  @Test public void languageSwitchDuringInferenceClearsContextAndDropsOldResult()
  {
    ManualExecutor executor = new ManualExecutor();
    Engine engine = new Engine();
    List<String> delivered = new ArrayList<>();
    PredictionWorker worker = new PredictionWorker(engine, executor, (s, w) -> delivered.add(s.language));
    worker.submit(snapshot(1));
    engine.during = () -> {
      worker.invalidate(true);
      worker.submit(new PredictionSnapshot(2, "test ", "", 5, PredictionSnapshot.Kind.WORD, "de"));
    };
    executor.runAll();
    assertEquals(java.util.Arrays.asList("de"), delivered);
    assertTrue(engine.resets > 0);
    assertEquals(0, engine.closes);
  }
}
