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
      assertTrue(edit.undo(editor.connection));
      assertEquals(word, editor.text);
      assertEquals(word.length(), editor.start);
      assertEquals(0, editor.batchDepth);
    }
  }

  @Test public void consumesExistingSpaceAndUndoRestoresOriginalCursor()
  {
    FakeEditor editor = new FakeEditor("to tomorrow", 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "to", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("to tomorrow", editor.text);
    assertEquals(3, editor.start);
    assertEquals(0, editor.commits);
    assertTrue(edit.undo(editor.connection));
    assertEquals("to tomorrow", editor.text);
    assertEquals(2, editor.start);
  }

  @Test public void phraseReplacesPrefixAndUndoRestoresIt()
  {
    FakeEditor editor = new FakeEditor("😀 I would li", 13);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "like a coffee", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("😀 I would like a coffee ", editor.text);
    assertTrue(edit.undo(editor.connection));
    assertEquals("😀 I would li", editor.text);
    assertEquals(13, editor.start);
  }

  @Test public void punctuationAndWordInteriorArePreserved()
  {
    FakeEditor editor = new FakeEditor("cofee, please", 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "coffee", true);
    assertTrue(edit.apply(editor.connection));
    assertEquals("coffee, please", editor.text);
    assertEquals(6, editor.start);
    assertTrue(edit.undo(editor.connection));
    assertEquals("cofee, please", editor.text);
    assertEquals(2, editor.start);
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

  @Test public void confirmsLongFollowingContextWithoutLosingUndo()
  {
    FakeEditor editor = new FakeEditor("to " + new String(new char[80]).replace('\0', 'x'), 2);
    CompletionEdit edit = new CompletionEdit(editor.snapshot(1), "to", true);
    assertTrue(edit.apply(editor.connection));
    assertTrue(edit.confirm(editor.snapshot(2)));
    assertEquals(32, edit.after.after.length());
  }
}
