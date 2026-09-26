package juloo.keyboard2.prediction;

import java.util.*;

/** Observes only successful individual keystrokes; never harvests editor contents. */
public final class PhraseLearner
{
  private final List<String> run = new ArrayList<>(), pending = new ArrayList<>();
  private final StringBuilder word = new StringBuilder();
  private PredictionSnapshot expected;

  public void reset() { run.clear(); pending.clear(); word.setLength(0); expected = null; }

  public void typed(PredictionSnapshot before, PredictionSnapshot after, String text)
  {
    if (before == null || after == null || text.codePointCount(0, text.length()) != 1)
    { reset(); return; }
    if (expected != null && !expected.matches(before.before, before.after, before.selection)) reset();
    // Never start learning a word whose beginning came from another source.
    if (expected == null && !before.prefix.isEmpty()) { reset(); return; }
    String combined = before.before + text;
    combined = combined.substring(Math.max(0, combined.length() - 1024));
    if (!after.matches(combined, before.after, before.selection + text.length())) { reset(); return; }
    expected = after;
    int c = text.codePointAt(0);
    if (PredictionSnapshot.wordChar(c))
    {
      word.append(text);
      if (word.length() > 48) reset();
      return;
    }
    if (" \n\t.,!?;:".contains(text) && word.length() > 0)
    {
      String w = word.toString();
      if (!new PredictionSnapshot(0, "", "", 0).validWord(w)) { reset(); return; }
      run.add(w);
      if (run.size() > 6) run.remove(0);
      for (int n = 2; n <= run.size(); n++)
        pending.add(PhraseMemory.join(run.subList(run.size() - n, run.size())));
      if (pending.size() > 256) pending.subList(0, pending.size() - 256).clear();
      word.setLength(0);
      if (!text.equals(" ")) run.clear();
    }
    else { run.clear(); word.setLength(0); }
  }

  public List<String> take(PredictionSnapshot current)
  {
    if (expected == null || current == null
        || !expected.matches(current.before, current.after, current.selection)) { reset(); return Collections.emptyList(); }
    List<String> result = new ArrayList<>(pending);
    pending.clear();
    return result;
  }
}
