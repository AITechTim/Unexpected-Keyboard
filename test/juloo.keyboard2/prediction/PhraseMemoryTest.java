package juloo.keyboard2.prediction;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhraseMemoryTest
{
  private PredictionSnapshot snapshot(String text) { return new PredictionSnapshot(1, text, "", text.length()); }
  private List<String> type(PhraseLearner learner, String text)
  {
    String before = "";
    for (int i = 0; i < text.length(); i++)
    {
      String after = before + text.charAt(i);
      learner.typed(snapshot(before), snapshot(after), text.substring(i, i + 1)); before = after;
    }
    return learner.take(snapshot(before));
  }

  @Test public void repeatedTypingEnablesWordsAndWholePhrases()
  {
    PhraseMemory memory = new PhraseMemory();
    memory.learn(type(new PhraseLearner(), "see you tomorrow morning "), 1);
    assertEquals(0, memory.query(snapshot("see "), 2).words.length);
    memory.learn(type(new PhraseLearner(), "see you tomorrow morning "), 3);
    assertArrayEquals(new String[]{"you"}, memory.query(snapshot("see "), 4).words);
    assertEquals("you tomorrow morning", memory.query(snapshot("see "), 4).phrase);
    assertEquals("tomorrow morning", memory.query(snapshot("see you to"), 4).phrase);
    assertArrayEquals(new String[]{"tomorrow"}, memory.query(snapshot("see you "), 4).words);
  }

  @Test public void longerContextWinsAndMatchingRespectsWordBoundaries()
  {
    PhraseMemory memory = new PhraseMemory();
    memory.learn(Arrays.asList("see you later", "see you later", "you tomorrow", "you tomorrow", "you tomorrow"), 1);
    assertEquals("later", memory.query(snapshot("see you "), 2).words[0]);
    assertEquals(0, memory.query(snapshot("oversee "), 2).words.length);
  }

  @Test public void pasteImportedPrefixesAndUnconfirmedEditsNeverLearn()
  {
    PhraseLearner learner = new PhraseLearner();
    learner.typed(snapshot(""), snapshot("secret phrase "), "secret phrase ");
    assertTrue(learner.take(snapshot("secret phrase ")).isEmpty());
    learner.typed(snapshot("secret phr"), snapshot("secret phrase "), "ase ");
    assertTrue(learner.take(snapshot("secret phrase ")).isEmpty());
    type(learner, "see you ");
    learner.typed(snapshot("see you "), snapshot("see you now "), "x");
    assertTrue(learner.take(snapshot("see you now ")).isEmpty());
  }

  @Test public void resetDiscardsPendingLearningAndGeneratedContext()
  {
    PhraseLearner learner = new PhraseLearner();
    learner.typed(snapshot(""), snapshot("a"), "a");
    learner.typed(snapshot("a"), snapshot("a "), " ");
    learner.typed(snapshot("a "), snapshot("a b"), "b");
    learner.typed(snapshot("a b"), snapshot("a b "), " ");
    learner.reset();
    assertTrue(learner.take(snapshot("a b ")).isEmpty());
  }

  @Test public void expiresAndBoundsMemory()
  {
    PhraseMemory memory = new PhraseMemory();
    memory.learn(Arrays.asList("see you", "see you"), 1);
    assertEquals(0, memory.query(snapshot("see "), PhraseMemory.RETENTION + 2).words.length);
    List<String> phrases = new ArrayList<>();
    for (int i = 0; i < 10020; i++) phrases.add("test " + i);
    memory.learn(phrases, 1);
    assertEquals(PhraseMemory.LIMIT, memory.entries.size());
  }

  @Test public void phraseValidationRejectsIncompleteShapes()
  {
    PredictionSnapshot s = snapshot("I want co");
    assertTrue(s.validPhrase("coffee please"));
    assertFalse(s.validPhrase("tea please"));
    assertFalse(s.validPhrase("coffee  please"));
    assertFalse(s.validPhrase("coffee please\n"));
    assertFalse(s.validPhrase("coffee"));
    assertFalse(s.validPhrase("coffee and one two three four"));
  }
  @Test public void retainsNamesAndMatchesContextWithoutCaseSensitivity()
  {
    PhraseMemory memory = new PhraseMemory();
    memory.learn(type(new PhraseLearner(), "Ask Alice tomorrow "), 1);
    memory.learn(type(new PhraseLearner(), "Ask Alice tomorrow "), 2);
    assertEquals("Alice tomorrow", memory.query(snapshot("ask "), 3).phrase);
    assertEquals("alice tomorrow", memory.query(snapshot("ask a"), 3).phrase);
  }

  @Test public void sentencePunctuationCompletesLearningWithoutJoiningSentences()
  {
    PhraseMemory memory = new PhraseMemory();
    memory.learn(type(new PhraseLearner(), "see you tomorrow!see you tomorrow!"), 1);
    assertEquals("you tomorrow", memory.query(snapshot("see "), 2).phrase);
    assertEquals(0, memory.query(snapshot("tomorrow "), 2).words.length);
  }

}
