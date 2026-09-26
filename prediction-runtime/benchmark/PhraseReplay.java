package juloo.keyboard2.prediction;

import java.util.*;

/** Deterministic recurring-phrase replay. No private corpus or Android runtime. */
public final class PhraseReplay
{
  static String language = "en";
  static PredictionSnapshot snapshot(String s) { return new PredictionSnapshot(1, s, "", s.length(), PredictionSnapshot.Kind.WORD, language); }
  public static void main(String[] args)
  {
    language = args.length > 0 ? args[0] : "en";
    String[] corpus = { "see you tomorrow morning", "thanks for your help",
      "let me know what you think", "have a great weekend", "I will get back to you" };
    if (language.equals("de")) corpus = new String[]{"vielen Dank für deine Hilfe", "wir sehen uns morgen wieder",
      "hoffentlich sehen wir uns bald", "schöne Grüße aus München", "ich melde mich morgen früh"};
    PhraseMemory memory = new PhraseMemory();
    for (int repeat = 0; repeat < 2; repeat++) for (String line : corpus)
    {
      PhraseLearner learner = new PhraseLearner();
      String before = "";
      for (char c : (line + " ").toCharArray())
      {
        String after = before + c;
        learner.typed(snapshot(before), snapshot(after), Character.toString(c));
        before = after;
      }
      memory.learn(language, learner.take(snapshot(before)), repeat + 1);
    }
    int hits = 0, manual = 0, idealWordTaps = 0, phraseTaps = 0;
    List<Long> timings = new ArrayList<>();
    for (String line : corpus)
    {
      int first = line.indexOf(' ');
      String context = line.substring(0, first + 1), tail = line.substring(first + 1);
      long start = System.nanoTime();
      PhraseMemory.Match match = memory.query(snapshot(context), 3);
      timings.add(System.nanoTime() - start);
      boolean hit = tail.equals(match.phrase);
      if (hit) hits++;
      manual += tail.length() + 1;
      idealWordTaps += tail.split(" ").length;
      phraseTaps += hit ? 1 : tail.length() + 1;
    }
    Collections.sort(timings);
    System.out.println("{\"phrases\":" + corpus.length + ",\"exact_learned_hits\":" + hits
      + ",\"manual_keys_after_first_word\":" + manual + ",\"ideal_single_word_taps\":" + idealWordTaps
      + ",\"learned_phrase_taps\":" + phraseTaps + ",\"max_lookup_ms\":" + timings.get(timings.size() - 1) / 1e6 + "}");
    if (hits != corpus.length) throw new AssertionError("Recurring phrase replay regressed");
  }
}
