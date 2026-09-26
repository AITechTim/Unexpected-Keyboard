package juloo.keyboard2.suggestions;

import java.util.Arrays;
import juloo.keyboard2.prediction.Candidate;
import juloo.keyboard2.prediction.PredictionSnapshot;
import java.util.List;
import juloo.cdict.Cdict;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.Config;
import juloo.keyboard2.ComposeKey;
import juloo.keyboard2.ComposeKeyData;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  Callback _callback;
  Config _config;
  boolean _enabled;

  /** Current suggestions. The best suggestion is at index [0]. */
  public String[] suggestions = new String[MAX_COUNT];
  /** Number of suggestions at the beginning of the [suggestions] array that
      are not [null]. */
  public int count = 0;
  public String emoji_suggestion = null;
  public final Candidate[] candidates = new Candidate[MAX_COUNT];
  private final String[] dictionary = new String[MAX_COUNT];
  private int dictionary_count;

  private PredictionSnapshot editor;
  private String[] learned = new String[0], model = new String[0];
  public Candidate phrase;

  public PredictionSnapshot snapshot() { return editor; }

  public String dictionary_first() { return dictionary_count > 0 ? dictionary[0] : null; }

  public void bind(PredictionSnapshot snapshot)
  { editor = snapshot; merge(); }

  public void invalidate()
  {
    editor = null; phrase = null;
    learned = model = new String[0];
    merge();
  }

  public void clear_predictions()
  { learned = model = new String[0]; phrase = null; merge(); }

  public void set_predictions(PredictionSnapshot snapshot, String[] words)
  { editor = snapshot; model = words; merge(); }

  public void set_learned(PredictionSnapshot snapshot, String[] words)
  { editor = snapshot; learned = words; merge(); }

  public void set_phrase(PredictionSnapshot snapshot, String text, Candidate.Source source)
  {
    if (!snapshot.validPhrase(text)) return;
    if (phrase != null && phrase.source == Candidate.Source.LEARNED && source == Candidate.Source.LLM) return;
    phrase = new Candidate(text, source, snapshot);
    _callback.set_suggestions(this);
  }

  private void merge()
  {
    count = 0;
    Arrays.fill(suggestions, null); Arrays.fill(candidates, null);
    append(learned, Candidate.Source.LEARNED);
    append(model, Candidate.Source.LLM);
    append(Arrays.copyOf(dictionary, dictionary_count), Candidate.Source.DICTIONARY);
    _callback.set_suggestions(this);
  }

  private void append(String[] words, Candidate.Source source)
  {
    for (String word : words)
    {
      if (count == MAX_COUNT) return;
      if (word == null || contains(word, count)) continue;
      if (source != Candidate.Source.DICTIONARY && (editor == null || !editor.validWord(word))) continue;
      suggestions[count] = word;
      candidates[count++] = new Candidate(word, source, editor);
    }
  }

  private boolean contains(String word, int n)
  {
    for (int i = 0; i < n; i++) if (word.equalsIgnoreCase(suggestions[i])) return true;
    return false;
  }
  /** Number of suggestions in [suggestions]. */
  public static final int MAX_COUNT = 3;

  public Suggestions(Callback c, Config conf)
  {
    _callback = c;
    _config = conf;
  }

  public void started()
  {
    _enabled = _config.editor_config.should_show_candidates_view;
    clear();
  }

  public void currently_typed_word(String word)
  {
    if (!_enabled)
      return;
    if (word.length() < 2 || _config.current_dictionary == null)
      clear();
    else
      query_suggestions(word);
    publish_dictionary();
  }

  void publish_dictionary()
  {
    dictionary_count = count;
    for (int i = 0; i < MAX_COUNT; i++) dictionary[i] = i < count ? suggestions[i] : null;
    editor = null; learned = model = new String[0]; phrase = null;
    merge();
  }

  void clear()
  {
    count = dictionary_count = 0;
    editor = null; learned = model = new String[0]; phrase = null;
    for (int i = 0; i < MAX_COUNT; i++)
    { suggestions[i] = dictionary[i] = null; candidates[i] = null; }
    emoji_suggestion = null;
  }

  int query_suggestions(String word)
  {
    Cdict dict = _config.current_dictionary;
    boolean first_char_upper = Character.isUpperCase(word.charAt(0));
    word = apply_substitutions(word);
    Cdict.Result r = dict.find(word);
    int i = 0;
    if (r.found)
      suggestions[i++] = dict.word(r.index);
    int[] suffixes = dict.suffixes(r, MAX_COUNT);
    // Disable distance search for small words
    int[] dist = (word.length() < 3 || i + 1 >= MAX_COUNT) ? NO_RESULTS :
      dict.distance(word, 1, MAX_COUNT);
    for (int j = 0; j < MAX_COUNT && i < MAX_COUNT; j++)
    {
      if (suffixes.length > j)
        suggestions[i++] = dict.word(suffixes[j]);
      if (dist.length > j && i < MAX_COUNT)
        suggestions[i++] = dict.word(dist[j]);
    }
    if (first_char_upper)
      capitalize_results(suggestions, i);
    emoji_suggestion = query_emoji(word); // word with substitutions applied
    count = i;
    return i;
  }

  static void capitalize_results(String[] s, int count)
  {
    for (int i = 0; i < count; i++)
      s[i] = s[i].substring(0, 1).toUpperCase() + s[i].substring(1);
  }

  String query_emoji(String word)
  {
    Cdict dict = _config.emoji_dictionary;
    // Disable emoji suggestion for short words
    if (dict == null || word.length() < 3)
      return null;
    Cdict.Result r = dict.find(word);
    if (r.found)
      return dict.word(r.index);
    int[] s = dict.suffixes(r, 1);
    if (s.length > 0)
      return dict.word(s[0]);
    return null;
  }

  /** Apply the same substitutions that were used when building the
      dictionaries to find word aliases. This catches missing diacritics for
      example. */
  String apply_substitutions(String w)
  {
    StringBuilder b = new StringBuilder(w);
    int len = w.length();
    for (int i = 0; i < len; i++)
    {
      char r =
        ComposeKey.transform_char(ComposeKeyData.substitutions, b.charAt(i));
      if (r != 0) b.setCharAt(i, r);
    }
    return b.toString();
  }

  static final int[] NO_RESULTS = new int[0];

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
