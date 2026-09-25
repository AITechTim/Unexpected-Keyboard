package juloo.keyboard2.suggestions;

import juloo.keyboard2.prediction.Candidate;
import juloo.keyboard2.prediction.PredictionSnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class PredictionMergeTest
{
  @Test public void retainsSeparateDictionaryCandidateForSpaceAndFallback()
  {
    Suggestions s = new Suggestions(ignored -> {}, null);
    s.count = 3;
    s.suggestions[0] = "coat"; s.suggestions[1] = "coffee"; s.suggestions[2] = "cold";
    s.publish_dictionary();
    s.emoji_suggestion = "☕";
    s.set_predictions(new PredictionSnapshot(1, "I want co", "", 9), new String[]{"coffee", "coffee", "unrelated"});
    assertArrayEquals(new String[]{"coffee", "coat", "cold"}, s.suggestions);
    assertEquals(Candidate.Source.LLM, s.candidates[0].source);
    assertEquals("coat", s.dictionary_first());
    assertEquals("☕", s.emoji_suggestion);
    s.clear_predictions();
    assertArrayEquals(new String[]{"coat", "coffee", "cold"}, s.suggestions);
    assertEquals(Candidate.Source.DICTIONARY, s.candidates[0].source);
  }

  @Test public void worksWithoutDictionaryAndClearsOnInvalidation()
  {
    Suggestions s = new Suggestions(ignored -> {}, null);
    s.set_predictions(new PredictionSnapshot(1, "I want ", "", 7), new String[]{"tea", "coffee", "water"});
    assertEquals(3, s.count);
    assertNull(s.dictionary_first());
    s.clear_predictions();
    assertEquals(0, s.count);
    assertNull(s.candidates[0]);
  }
}
