package juloo.keyboard2.prediction;

/** Immutable editor snapshot. Offsets are UTF-16, matching InputConnection. */
public final class PredictionSnapshot
{
  public enum Kind { WORD, PHRASE }
  public final Kind kind;
  public final long revision;
  public final String before, after, context, prefix, wordBefore, wordAfter;
  public final int selection;

  public PredictionSnapshot(long revision, String before, String after, int selection)
  {
    this(revision, before, after, selection, Kind.WORD);
  }

  public PredictionSnapshot(long revision, String before, String after, int selection, Kind kind)
  {
    this.kind = kind;
    this.revision = revision;
    this.before = before;
    this.after = after;
    this.selection = selection;
    int start = before.length();
    while (start > 0 && wordChar(before.codePointBefore(start)))
      start -= Character.charCount(before.codePointBefore(start));
    prefix = before.substring(start);
    context = before.substring(0, start);
    start = before.length();
    while (start > 0 && replacementChar(before.codePointBefore(start)))
      start -= Character.charCount(before.codePointBefore(start));
    wordBefore = before.substring(start);
    int end = 0;
    while (end < after.length() && replacementChar(after.codePointAt(end)))
      end += Character.charCount(after.codePointAt(end));
    wordAfter = after.substring(0, end);
  }

  public boolean eligible()
  {
    if (!after.isEmpty() && wordChar(after.codePointAt(0))) return false;
    if (prefix.length() > 48) return false;
    if (!prefix.isEmpty() && !context.isEmpty())
    {
      int previous = context.codePointBefore(context.length());
      if (Character.isDigit(previous) || previous == '_') return false;
    }
    // Do not turn numbers or identifiers into words.
    if (!before.isEmpty())
    {
      int c = before.codePointBefore(before.length());
      if (Character.isDigit(c) || c == '_') return false;
    }
    return true;
  }

  public boolean matches(String currentBefore, String currentAfter, int currentSelection)
  {
    return selection == currentSelection && before.equals(currentBefore) && after.equals(currentAfter);
  }

  public boolean validWord(String word)
  {
    if (word == null || word.length() > 64 || word.length() <= prefix.length()
        || !word.startsWith(prefix)) return false;
    boolean letter = false;
    for (int i = 0; i < word.length();)
    {
      int c = word.codePointAt(i);
      if (!wordChar(c)) return false;
      letter |= Character.isLetter(c);
      i += Character.charCount(c);
    }
    return letter;
  }

  public PredictionSnapshot forPhrase()
  { return new PredictionSnapshot(revision, before, after, selection, Kind.PHRASE); }

  public boolean validPhrase(String phrase)
  {
    if (phrase == null || !phrase.startsWith(prefix)) return false;
    String[] words = phrase.split(" ", -1);
    if (words.length < 2 || words.length > 5) return false;
    PredictionSnapshot empty = new PredictionSnapshot(0, "", "", 0);
    for (String word : words) if (!empty.validWord(word)) return false;
    return true;
  }

  private static boolean replacementChar(int c) { return Character.isLetterOrDigit(c) || c == '\''; }

  public static boolean wordChar(int c) { return Character.isLetter(c) || c == '\''; }
}
