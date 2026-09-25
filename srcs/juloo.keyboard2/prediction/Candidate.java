package juloo.keyboard2.prediction;

public final class Candidate
{
  public enum Source { DICTIONARY, LLM, EMOJI }
  public final String text;
  public final Source source;
  /** Non-null only for LLM candidates; supplies replacement range and revision. */
  public final PredictionSnapshot snapshot;

  public Candidate(String text, Source source, PredictionSnapshot snapshot)
  {
    this.text = text;
    this.source = source;
    this.snapshot = snapshot;
  }
}
