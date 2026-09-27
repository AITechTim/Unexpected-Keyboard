package juloo.keyboard2.prediction;

import java.util.*;

/** Bounded dictionary queries; all surface forms retain their spelling and case. */
public final class LexicalCandidates
{
  public interface Dictionary
  {
    WordCandidate exact(String text);
    List<WordCandidate> completions(String text, int limit);
    List<WordCandidate> corrections(String text, int limit);
  }
  public static WordCandidate[] find(String typed, Dictionary dictionary, Map<Character, String> neighbors)
  {
    if (dictionary == null || typed.length() < 2 || typed.length() > 48) return new WordCandidate[0];
    Map<String, WordCandidate> found = new LinkedHashMap<>();
    add(found, typed, dictionary.exact(typed));
    for (WordCandidate c : dictionary.completions(typed, 256)) add(found, typed, c);
    // Dictionary-validated inflections keep everyday forms in a news-frequency lexicon.
    Set<String> inflections = new HashSet<>();
    for (String ending : new String[]{"e", "s", "t", "n", "en", "er", "es", "st", "te", "ten", "tes", "ern", "end", "ed", "ing", "ies"})
    {
      WordCandidate c = dictionary.exact(typed + ending);
      if (c != null) { add(found, typed, c); inflections.add(c.text); }
    }
    for (WordCandidate c : dictionary.corrections(typed, 32)) add(found, typed, c);
    // Explicit transpositions: the dictionary's edit search is ordinary Levenshtein.
    char[] chars = WordForms.folded(typed).toCharArray();
    for (int i = 0; i + 1 < chars.length; i++)
    {
      char t = chars[i]; chars[i] = chars[i+1]; chars[i+1] = t;
      add(found, typed, dictionary.exact(new String(chars)));
      t = chars[i]; chars[i] = chars[i+1]; chars[i+1] = t;
    }
    // Two nearby-key substitutions use exact lookups instead of an unbounded edit-2 trie walk.
    if (chars.length >= 5 && chars.length <= 24)
    {
      int queries = 0;
      outer: for (int i = 0; i < chars.length; i++) for (int j = i + 1; j < chars.length; j++)
      {
        char a = chars[i], b = chars[j];
        String na = neighbors.get(a), nb = neighbors.get(b);
        if (na == null || nb == null) continue;
        for (char ca : na.toCharArray()) for (char cb : nb.toCharArray())
        {
          if (++queries > 1024) break outer;
          chars[i] = ca; chars[j] = cb;
          add(found, typed, dictionary.exact(new String(chars)));
          chars[i] = a; chars[j] = b;
        }
      }
    }
    List<WordCandidate> ranked = new ArrayList<>(found.values());
    Collections.sort(ranked, (a,b) -> { int cmp = Double.compare(b.prior(), a.prior()); return cmp == 0 ? a.text.compareTo(b.text) : cmp; });
    // A shared token trie scores this wider pool with at most six active branches.
    LinkedHashMap<String, WordCandidate> shortlist = new LinkedHashMap<>();
    int corrections = 0;
    for (WordCandidate c : ranked) if (c.match == WordCandidate.Match.CORRECTION && corrections++ < 8) shortlist.put(c.text, c);
    for (WordCandidate c : ranked) if (inflections.contains(c.text) && shortlist.size() < 20) shortlist.put(c.text, c);
    List<WordCandidate> shortForms = new ArrayList<>(ranked);
    Collections.sort(shortForms, (a,b) -> Integer.compare(a.text.length(), b.text.length()));
    int shortCount = 0;
    for (WordCandidate c : shortForms) if (c.match != WordCandidate.Match.CORRECTION
        && c.text.length() <= typed.length() + 3 && shortCount++ < 12 && shortlist.size() < 32) shortlist.put(c.text, c);
    for (WordCandidate.Match kind : WordCandidate.Match.values())
    {
      int n = 0, limit = kind == WordCandidate.Match.CORRECTION ? 8 : 36;
      for (WordCandidate c : ranked) if (c.match == kind && n++ < limit && shortlist.size() < 48) shortlist.put(c.text, c);
    }
    for (WordCandidate c : ranked) if (shortlist.size() < 48) shortlist.put(c.text, c);
    return shortlist.values().toArray(new WordCandidate[0]);
  }

  private static void add(Map<String, WordCandidate> found, String typed, WordCandidate c)
  {
    if (c == null || !WordForms.letters(c.text)) return;
    String a = WordForms.folded(typed), b = WordForms.folded(c.text);
    boolean prefix = b.startsWith(a);
    int edits = prefix ? 0 : WordForms.distance(a, b);
    if (!prefix && edits > (a.length() >= 5 ? 2 : 1)) return;
    WordCandidate.Match kind = b.equals(a) ? WordCandidate.Match.EXACT : prefix ? WordCandidate.Match.COMPLETION : WordCandidate.Match.CORRECTION;
    found.put(c.text, new WordCandidate(c.text, kind, c.frequency, edits));
    if (Character.isUpperCase(typed.charAt(0)) && Character.isLowerCase(c.text.charAt(0)))
    {
      String capital = c.text.substring(0, 1).toUpperCase(Locale.ROOT) + c.text.substring(1);
      found.put(capital, new WordCandidate(capital, kind, c.frequency, edits));
    }
  }
}
