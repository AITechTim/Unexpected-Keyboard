package juloo.keyboard2.prediction;

public final class Candidate
{
  public enum Source { DICTIONARY, LEARNED, LLM, EMOJI }
  public final String text;
  public final Source source;
  /** Editor state at publication; required for accepting word and phrase candidates. */
  public final PredictionSnapshot snapshot;

  public Candidate(String text, Source source, PredictionSnapshot snapshot)
  {
    this.text = text;
    this.source = source;
    this.snapshot = snapshot;
  }
}
