package juloo.keyboard2.prediction;

import java.util.*;
import juloo.cdict.Cdict;
import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;

/** Shared by the keyboard and the host quality replay. */
public final class CdictLexicon implements LexicalCandidates.Dictionary
{
  private final Cdict dict;
  public CdictLexicon(Cdict dict) { this.dict = dict; }
  private WordCandidate candidate(int id)
  { return new WordCandidate(dict.word(id), WordCandidate.Match.COMPLETION, dict.freq(id), 0); }
  public WordCandidate exact(String word)
  { Cdict.Result r = dict.find(word); if (!r.found) r = dict.find(aliases(word)); return r.found ? candidate(r.index) : null; }
  public List<WordCandidate> completions(String word, int limit)
  {
    // Prefer the literal spelling before folded aliases (e.g. Grü before gru).
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    for (int id : dict.suffixes(dict.find(word), limit)) ids.add(id);
    String capital = word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    if (!capital.equals(word)) for (int id : dict.suffixes(dict.find(capital), limit)) ids.add(id);
    for (int id : dict.suffixes(dict.find(aliases(word)), limit)) ids.add(id);
    List<WordCandidate> result = new ArrayList<>();
    for (int id : ids) result.add(candidate(id));
    return result;
  }
  public List<WordCandidate> corrections(String word, int limit)
  { return words(dict.distance(aliases(word), 1, limit)); }
  private List<WordCandidate> words(int[] ids)
  { List<WordCandidate> result = new ArrayList<>(); for (int id : ids) result.add(candidate(id)); return result; }
  public static String aliases(String text)
  {
    StringBuilder b = new StringBuilder(text);
    for (int i = 0; i < b.length(); i++)
    {
      char r = ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }
}
