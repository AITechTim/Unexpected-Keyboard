package juloo.keyboard2.prediction;

/** Controller runs inference/lifecycle on its worker; cancellation is thread-safe. */
public interface PredictionEngine extends AutoCloseable
{
  String[] predict(PredictionSnapshot snapshot);
  void cancel();
  void reset();
  void close();
}
