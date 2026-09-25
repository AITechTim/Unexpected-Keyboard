package juloo.keyboard2.prediction;

/** Immutable editor snapshot. Offsets are UTF-16, matching InputConnection. */
public final class PredictionSnapshot
{
  public final long revision;
  public final String before, after, context, prefix;
  public final int selection;

  public PredictionSnapshot(long revision, String before, String after, int selection)
  {
    this.revision = revision;
    this.before = before;
    this.after = after;
    this.selection = selection;
    int start = before.length();
    while (start > 0 && wordChar(before.codePointBefore(start)))
      start -= Character.charCount(before.codePointBefore(start));
    prefix = before.substring(start);
    context = before.substring(0, start);
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

  public String insertion(String word) { return after.isEmpty() ? word + " " : word; }

  private static boolean wordChar(int c) { return Character.isLetter(c) || c == '\''; }
}
