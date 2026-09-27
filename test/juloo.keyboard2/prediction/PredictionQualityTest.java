package juloo.keyboard2.prediction;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PredictionQualityTest
{
  private Candidate c(String text, long revision)
  { return new Candidate(text, Candidate.Source.DICTIONARY, new PredictionSnapshot(revision, "helpe", "", 5)); }
  @Test public void survivingChoicesStayPutButNewWinnersCanReplaceThem()
  {
    StableSlots slots = new StableSlots();
    slots.assign(new Candidate[]{c("help", 1), c("helper", 1), c("helping", 1)});
    Candidate[] next = slots.assign(new Candidate[]{c("helper", 2), c("helped", 2)});
    assertEquals("helper", next[1].text);
    assertEquals(2, next[1].snapshot.revision);
    assertEquals("helped", next[0].text);
    assertNull(next[2]);
    next = slots.assign(new Candidate[]{c("helper", 3)});
    assertNull(next[0]); assertEquals("helper", next[1].text);
    slots.reset();
    assertEquals("helper", slots.assign(new Candidate[]{c("helper", 4)})[0].text);
  }
  @Test public void trueUnicodeDistanceRejectsUnrelatedShortWords()
  {
    assertEquals(1, WordForms.distance("teh", "the"));
    assertEquals(2, WordForms.distance("hiben", "given"));
    assertEquals(0, WordForms.distance("GRÜẞE", "Grüße"));
    assertEquals(0, WordForms.distance("gro\u0308ßer", "größer"));
    assertFalse(WordForms.fits(new PredictionSnapshot(1, "How do I tur", "", 12), "but"));
    assertTrue(WordForms.fits(new PredictionSnapshot(1, "hiben", "", 5), "given"));
  }
  @Test public void typoPoolIncludesTranspositionsAndTwoNeighborErrors()
  {
    LexicalCandidates.Dictionary dict = new LexicalCandidates.Dictionary() {
      public WordCandidate exact(String text)
      { return text.equals("given") || text.equals("their") ? new WordCandidate(text, WordCandidate.Match.EXACT, 12, 0) : null; }
      public List<WordCandidate> completions(String text, int n) { return Collections.emptyList(); }
      public List<WordCandidate> corrections(String text, int n) { return Collections.emptyList(); }
    };
    Map<Character, String> neighbors = new HashMap<>(); neighbors.put('h', "g"); neighbors.put('b', "v");
    WordCandidate[] words = LexicalCandidates.find("hiben", dict, neighbors);
    assertEquals("given", words[0].text); assertEquals(2, words[0].edits);
    assertEquals("their", LexicalCandidates.find("thier", dict, neighbors)[0].text);
    assertEquals(0, LexicalCandidates.find("hiben", dict, Collections.emptyMap()).length);
  }
  @Test public void uncommonInflectionsSurviveFrequentLongCompletions()
  {
    LexicalCandidates.Dictionary dict = new LexicalCandidates.Dictionary() {
      public WordCandidate exact(String word) { return word.equals("kommst") ? new WordCandidate(word, WordCandidate.Match.COMPLETION, 1, 0) : null; }
      public List<WordCandidate> corrections(String word,int n) { return Collections.emptyList(); }
      public List<WordCandidate> completions(String word,int n) {
        List<WordCandidate> result = new ArrayList<>();
        for(char c='a';c<='z';c++) for(char d='a';d<='c';d++) result.add(new WordCandidate("kommando"+c+d,WordCandidate.Match.COMPLETION,15,0));
        return result;
      }
    };
    WordCandidate[] pool=LexicalCandidates.find("komm",dict,Collections.emptyMap());
    assertTrue(pool.length<=48);
    assertTrue(Arrays.asList(Arrays.stream(pool).map(c->c.text).toArray(String[]::new)).contains("kommst"));
  }
  @Test public void partialModelScoresNeverFavorOnlyFinishedShortCandidates()
  {
    WordCandidate[] words = {new WordCandidate("turn", WordCandidate.Match.COMPLETION, 12, 0),
        new WordCandidate("turbine", WordCandidate.Match.COMPLETION, 3, 0)};
    assertEquals("turn", CandidateRanking.rank(words, new double[]{Double.NaN, 100})[0]);
    assertEquals("turbine", CandidateRanking.rank(words, new double[]{-20, -2})[0]);
  }
}
