package juloo.keyboard2.suggestions;

import juloo.keyboard2.prediction.*;
import java.util.*;
import juloo.cdict.Cdict;
import juloo.keyboard2.Config;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  Callback _callback;
  Config _config;

  /** Physical slots: center, right, left. Quality order is held separately in ranked. */
  public String[] suggestions = new String[MAX_COUNT];
  /** Number of non-empty slots (holes are retained). */
  public int count = 0;
  public String emoji_suggestion = null;
  public final Candidate[] candidates = new Candidate[MAX_COUNT];
  private final String[] dictionary = new String[MAX_COUNT];
  private int dictionary_count;

  private PredictionSnapshot editor;
  private WordCandidate[] lexical = new WordCandidate[0];
  private final StableSlots slots = new StableSlots();
  public Candidate[] ranked = new Candidate[0];
  private String[] learned = new String[0], model = new String[0];
  public Candidate phrase;

  public PredictionSnapshot snapshot() { return editor; }

  public String dictionary_first() { return dictionary_count > 0 ? dictionary[0] : null; }

  public WordCandidate[] lexicalCandidates()
  {
    // Learned continuations join the same bounded scoring request while typing.
    if (lexical.length == 0) return lexical.clone();
    LinkedHashMap<String, WordCandidate> pool = new LinkedHashMap<>();
    for (String word : learned) if (editor != null && WordForms.fits(editor, word) && pool.size() < 2)
      pool.put(word, new WordCandidate(word, WordCandidate.Match.COMPLETION, 12, 0));
    for (WordCandidate word : lexical) if (pool.size() < 48) pool.put(word.text, word);
    return pool.values().toArray(new WordCandidate[0]);
  }

  public void bind(PredictionSnapshot snapshot) { editor = snapshot; merge(); }

  /** One editor revision is published atomically, with freshly queried dictionary evidence. */
  public void refresh(PredictionSnapshot snapshot)
  {
    boolean continuing = editor != null && snapshot != null && editor.language.equals(snapshot.language)
      && editor.context.equals(snapshot.context) && editor.after.equals(snapshot.after)
      && editor.selection - editor.prefix.length() == snapshot.selection - snapshot.prefix.length()
      && snapshot.wordAfter.isEmpty();
    if (!continuing) { slots.reset(); learned = model = new String[0]; }
    editor = snapshot; phrase = null;
    if (snapshot == null) { clear(); merge(); return; }
    lexical = _config != null && !_config.editor_config.should_show_candidates_view ? new WordCandidate[0] : lookup(snapshot.wordBefore);
    dictionary_count = 0;
    List<WordCandidate> fallback = new ArrayList<>(Arrays.asList(lexical));
    Collections.sort(fallback, (a,b) -> a.match == WordCandidate.Match.EXACT && b.match != a.match ? -1 :
        b.match == WordCandidate.Match.EXACT && a.match != b.match ? 1 : Double.compare(b.prior(), a.prior()));
    for (WordCandidate c : fallback) if (dictionary_count < MAX_COUNT) dictionary[dictionary_count++] = c.text;
    emoji_suggestion = query_emoji(apply_substitutions(snapshot.wordBefore));
    merge();
  }

  public void invalidate()
  {
    clear(); slots.reset(); merge();
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
    LinkedHashMap<String, Candidate> choices = new LinkedHashMap<>();
    // Contextually ranked results already include dictionary corrections.
    if (editor != null && editor.prefix.isEmpty()) append(choices, learned, Candidate.Source.LEARNED);
    append(choices, model, Candidate.Source.LLM);
    append(choices, learned, Candidate.Source.LEARNED);
    List<WordCandidate> local = new ArrayList<>(Arrays.asList(lexical));
    Collections.sort(local, (a,b) -> Double.compare(b.prior(), a.prior()));
    for (WordCandidate c : local) append(choices, new String[]{c.text}, Candidate.Source.DICTIONARY);
    append(choices, Arrays.copyOf(dictionary, dictionary_count), Candidate.Source.DICTIONARY);
    ranked = choices.values().toArray(new Candidate[0]);
    count = ranked.length;
    Candidate[] displayed = slots.assign(ranked);
    for (int i = 0; i < MAX_COUNT; i++)
    { candidates[i] = displayed[i]; suggestions[i] = displayed[i] == null ? null : displayed[i].text; }
    _callback.set_suggestions(this);
  }

  private void append(Map<String, Candidate> choices, String[] words, Candidate.Source source)
  {
    for (String word : words)
    {
      if (choices.size() == MAX_COUNT) return;
      if (word == null || choices.containsKey(word)) continue;
      boolean duplicate = false;
      for (String existing : choices.keySet()) duplicate |= WordForms.folded(existing).equals(WordForms.folded(word));
      if (duplicate) continue;
      if (editor != null && !WordForms.fits(editor, word)) continue;
      if (editor == null && source != Candidate.Source.DICTIONARY) continue;
      choices.put(word, new Candidate(word, source, editor));
    }
  }

  private WordCandidate[] lookup(String typed)
  {
    if (_config == null || _config.current_dictionary == null) return new WordCandidate[0];
    return LexicalCandidates.find(typed, new CdictLexicon(_config.current_dictionary), _config.prediction_neighbors);
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
    clear();
  }

  public void currently_typed_word(String word)
  {
    // The controller refreshes from the actual editor immediately after this callback.
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
    lexical = new WordCandidate[0]; slots.reset();
    editor = null; learned = model = new String[0]; phrase = null;
    for (int i = 0; i < MAX_COUNT; i++)
    { suggestions[i] = dictionary[i] = null; candidates[i] = null; }
    emoji_suggestion = null;
  }

  String query_emoji(String word)
  {
    Cdict dict = _config == null ? null : _config.emoji_dictionary;
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
    return CdictLexicon.aliases(w);
  }

  public static interface Callback
  {
    public void set_suggestions(Suggestions suggestions);
  }
}
