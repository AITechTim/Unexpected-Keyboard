package juloo.keyboard2.prediction;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompletionEditTest
{
  @Test public void fullShortWordsOnlyInsertSpace()
  {
    for (String word : new String[]{"to", "at", "by"})
    {
      FakeEditor editor = new FakeEditor(word, word.length());
      CompletionEdit edit = new CompletionEdit(editor.snapshot(1), word, true);
      assertTrue(edit.apply(editor.connection));
      assertEquals(word + " ", editor.text);
      assertEquals(word.length() + 1, editor.start);
      assertTrue(edit.confirm(editor.snapshot(2)));
      assertTrue(edit.insertedSpace);
      assertEquals(0, editor.batchDepth);
    }
  }

  @Test public void advancesOverExistingSpaceWithoutReplacingIt()
  {
    FakeEditor editor = new FakeEditor("to tomorrow", 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "to", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("to tomorrow", editor.text);
    assertEquals(3, editor.start);
    assertEquals(0, editor.commits);
    assertFalse(edit.insertedSpace);
    assertTrue(edit.confirm(editor.snapshot(2)));
  }

  @Test public void phraseReplacesOnlyPrefix()
  {
    FakeEditor editor = new FakeEditor("😀 I would li", 13);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "like a coffee", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("😀 I would like a coffee ", editor.text);
    assertEquals(editor.text.length(), editor.start);
    assertTrue(edit.confirm(editor.snapshot(2)));
  }

  @Test public void punctuationAndWordInteriorArePreserved()
  {
    FakeEditor editor = new FakeEditor("cofee, please", 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "coffee", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("coffee fee, please", editor.text);
    assertEquals(7, editor.start);
    assertTrue(edit.confirm(editor.snapshot(2)));
  }

  @Test public void composingIsFinishedBeforeRangeReplacement()
  {
    FakeEditor editor = new FakeEditor("hello cof", 9);
    editor.composing = true;
    assertTrue(new CompletionEdit(editor.snapshot(1), "coffee", true).apply(editor.connection));
    assertEquals("hello coffee ", editor.text);
  }

  @Test public void failedSelectionDoesNotCommitAndAlwaysEndsBatch()
  {
    FakeEditor editor = new FakeEditor("cof", 3);
    editor.failSelection = true;
    assertFalse(new CompletionEdit(editor.snapshot(1), "coffee", true).apply(editor.connection));
    assertEquals("cof", editor.text);
    assertEquals(0, editor.commits);
    assertEquals(0, editor.batchDepth);
  }

  @Test public void dictionaryCorrectionReplacesDigitsWithTheWholeWord()
  {
    FakeEditor editor = new FakeEditor("word1", 5);
    assertTrue(new CompletionEdit(editor.snapshot(1), "word", true).apply(editor.connection));
    assertEquals("word ", editor.text);
  }

  @Test public void confirmsLongFollowingContext()
  {
    FakeEditor editor = new FakeEditor("to " + new String(new char[80]).replace('\0', 'x'), 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "to", true);
    assertTrue(edit.apply(editor.connection));
    assertTrue(edit.confirm(editor.snapshot(2)));
    assertEquals(32, edit.after.after.length());
  }

  @Test public void insertionPreservesAdjacentFollowingWords()
  {
    assertCompletion("I coftomorrow", 5, "coffee", true, "I coffee tomorrow", 9);
    assertCompletion("I tomorrow", 2, "coffee", true, "I coffee tomorrow", 9);
    assertCompletion("tomorrow", 0, "coffee", true, "coffee tomorrow", 7);
    assertCompletion("to1 tomorrow", 2, "to", true, "to 1 tomorrow", 3);
    assertCompletion("I coftomorrow", 5, "coffee and tea", true, "I coffee and tea tomorrow", 17);
  }

  @Test public void existingWhitespaceAndPunctuationArePreserved()
  {
    assertCompletion("cof  tomorrow", 3, "coffee", true, "coffee  tomorrow", 7);
    assertCompletion("cof\ttomorrow", 3, "coffee", true, "coffee\ttomorrow", 6);
    assertCompletion("cof\ntomorrow", 3, "coffee", true, "coffee\ntomorrow", 6);
    assertCompletion("cof, please", 3, "coffee", true, "coffee, please", 6);
    assertCompletion("cof😀 tomorrow", 3, "coffee", true, "coffee😀 tomorrow", 6);
  }

  @Test public void unicodeAndEmojiPreserveFollowingText()
  {
    assertCompletion("😀 grömorgen", 6, "größere", true, "😀 größere morgen", 11);
    assertCompletion("coffee tomorrow", 6, "☕", false, "☕ tomorrow", 1);
    assertCompletion("coftomorrow", 3, "☕", false, "☕tomorrow", 1);
  }

  @Test public void failedCommitRestoresCursorAndPreservesFollowingSpace()
  {
    FakeEditor editor = new FakeEditor("cof tomorrow", 3);
    editor.failCommit = true;
    assertFalse(new CompletionEdit(editor.snapshot(1), "coffee", true).apply(editor.connection));
    assertEquals("cof tomorrow", editor.text);
    assertEquals(3, editor.start);
    assertEquals(editor.start, editor.end);
    assertEquals(0, editor.batchDepth);
  }

  private void assertCompletion(String original, int cursor, String candidate, boolean space,
      String expected, int expectedCursor)
  {
    FakeEditor editor = new FakeEditor(original, cursor);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), candidate, space);
    assertTrue(edit.apply(editor.connection));
    assertEquals(expected, editor.text);
    assertEquals(expectedCursor, editor.start);
    assertEquals(editor.start, editor.end);
    assertTrue(edit.confirm(editor.snapshot(2)));
    assertEquals(0, editor.batchDepth);
  }
}
