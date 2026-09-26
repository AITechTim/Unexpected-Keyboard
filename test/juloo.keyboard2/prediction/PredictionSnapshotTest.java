package juloo.keyboard2.prediction;

import org.junit.Test;
import static org.junit.Assert.*;

public class PredictionSnapshotTest
{
  private PredictionSnapshot snapshot(String before, String after)
  { return new PredictionSnapshot(7, before, after, before.length()); }

  @Test public void separatesContextAndPartialWord()
  {
    PredictionSnapshot s = snapshot("I would like cof", "");
    assertEquals("I would like ", s.context);
    assertEquals("cof", s.prefix);
    assertTrue(s.validWord("coffee"));
    assertFalse(s.validWord("tea"));
    assertFalse(s.validWord("coffee please"));
    assertFalse(s.validWord("cof"));
    assertFalse(s.validWord("coffee\n"));
    assertTrue(s.eligible());
  }

  @Test public void acceptsNextWordAndPreservesPunctuation()
  {
    assertEquals("coffee ", new CompletionEdit(snapshot("I want ", ""), "coffee", true).insertion);
    assertEquals("coffee", new CompletionEdit(snapshot("I want ", ", please"), "coffee", true).insertion);
    assertEquals("coffee ", new CompletionEdit(snapshot("I want ", " tomorrow"), "coffee", true).insertion);
    assertTrue(snapshot("I want ", "").validWord("coffee"));
    assertFalse(snapshot("I want ", "").validWord("123"));
    assertFalse(snapshot("I want ", "").validWord("'''"));
  }

  @Test public void rejectsWordInteriorAndIdentifiers()
  {
    assertFalse(snapshot("cof", "fee").eligible());
    assertFalse(snapshot("item_", "").eligible());
    assertFalse(snapshot("item_na", "").eligible());
    assertFalse(snapshot("item123na", "").eligible());
    assertFalse(snapshot("123", "").eligible());
  }

  @Test public void handlesUtf16OffsetsAndApostrophes()
  {
    PredictionSnapshot s = snapshot("\ud83d\ude00 Don't forg", "");
    assertEquals("\ud83d\ude00 Don't ", s.context);
    assertEquals(13, s.selection);
    assertEquals("don't", snapshot("don't", "").prefix);
    assertTrue(snapshot("caf", "").validWord("café"));
    assertFalse(snapshot("", "").validWord("bad\ud800"));
  }

  @Test public void staleSelectionOrTextNeverMatches()
  {
    PredictionSnapshot s = snapshot("hello ", "world");
    assertTrue(s.matches("hello ", "world", 6));
    assertFalse(s.matches("hello ", "world", 4));
    assertFalse(s.matches("hello x", "world", 6));
    assertFalse(s.matches("hello ", "there", 6));
  }
}
