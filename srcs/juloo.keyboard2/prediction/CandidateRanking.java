package juloo.keyboard2.prediction;

import java.util.*;

public final class CandidateRanking
{
  public static String[] rank(WordCandidate[] candidates, double[] likelihoods)
  {
    // One coefficient set, frozen on the tuning corpus; timeout means lexical fallback.
    boolean complete = likelihoods.length == candidates.length;
    for (double score : likelihoods) complete &= !Double.isNaN(score) && score != Double.POSITIVE_INFINITY;
    boolean any = false; for (double score : likelihoods) any |= !Double.isInfinite(score) && !Double.isNaN(score);
    final boolean contextual = complete && any;
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < candidates.length; i++) order.add(i);
    Collections.sort(order, (a,b) -> {
      double sa = candidates[a].prior() + (contextual ? likelihoods[a] : 0);
      double sb = candidates[b].prior() + (contextual ? likelihoods[b] : 0);
      int cmp = Double.compare(sb, sa);
      return cmp == 0 ? candidates[a].text.compareTo(candidates[b].text) : cmp;
    });
    List<String> result = new ArrayList<>();
    Set<String> forms = new HashSet<>();
    for (int i : order)
    {
      if (forms.add(WordForms.folded(candidates[i].text))) result.add(candidates[i].text);
      if (result.size() == 3) break;
    }
    return result.toArray(new String[0]);
  }
}
