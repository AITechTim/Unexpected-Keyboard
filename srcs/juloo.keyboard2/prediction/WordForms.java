package juloo.keyboard2.prediction;

import java.util.Locale;
import java.text.Normalizer;

public final class WordForms
{
  public static String folded(String s) { return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
  public static boolean letters(String s)
  {
    if (s == null || s.isEmpty() || s.length() > 64) return false;
    boolean letter = false;
    for (int i = 0; i < s.length();)
    {
      int c = s.codePointAt(i);
      if (!PredictionSnapshot.wordChar(c)) return false;
      letter |= Character.isLetter(c);
      i += Character.charCount(c);
    }
    return letter;
  }
  /** Restricted Damerau-Levenshtein on code points, not UTF-8 bytes. */
  public static int distance(String a, String b)
  {
    int[] x = points(folded(a)), y = points(folded(b));
    int[][] d = new int[x.length + 1][y.length + 1];
    for (int i = 0; i <= x.length; i++) d[i][0] = i;
    for (int j = 0; j <= y.length; j++) d[0][j] = j;
    for (int i = 1; i <= x.length; i++) for (int j = 1; j <= y.length; j++)
    {
      d[i][j] = Math.min(d[i-1][j] + 1, Math.min(d[i][j-1] + 1, d[i-1][j-1] + (x[i-1] == y[j-1] ? 0 : 1)));
      if (i > 1 && j > 1 && x[i-1] == y[j-2] && x[i-2] == y[j-1]) d[i][j] = Math.min(d[i][j], d[i-2][j-2] + 1);
    }
    return d[x.length][y.length];
  }
  private static int[] points(String text)
  {
    int[] result = new int[text.codePointCount(0, text.length())];
    for (int i = 0, j = 0; i < text.length(); j++)
    { result[j] = text.codePointAt(i); i += Character.charCount(result[j]); }
    return result;
  }
  public static boolean fits(PredictionSnapshot s, String word)
  {
    if (!letters(word)) return false;
    String prefix = folded(s.prefix), candidate = folded(word);
    if (candidate.startsWith(prefix)) return true;
    return !prefix.isEmpty() && distance(prefix, candidate) <= (prefix.length() >= 5 ? 2 : 1);
  }
}
