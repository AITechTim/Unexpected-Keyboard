package juloo.keyboard2.prediction;

/** Lexical evidence is kept separate from model likelihood and display position. */
public final class WordCandidate
{
  public enum Match { EXACT, COMPLETION, CORRECTION }
  public final String text;
  public final Match match;
  public final int frequency, edits;
  public WordCandidate(String text, Match match, int frequency, int edits)
  { this.text = text; this.match = match; this.frequency = frequency; this.edits = edits; }
  public double prior()
  {
    return frequency * .18 - edits * 6.0 - (match == Match.COMPLETION ? .035 * text.length() : 0);
  }
}
