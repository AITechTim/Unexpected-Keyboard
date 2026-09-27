package juloo.keyboard2.prediction;

/** Slots are center, right, left, matching existing completion shortcuts. */
public final class StableSlots
{
  private Candidate[] previous = new Candidate[3];
  public void reset() { previous = new Candidate[3]; }
  public Candidate[] assign(Candidate[] ranked)
  {
    Candidate[] slots = new Candidate[3];
    boolean[] used = new boolean[ranked.length];
    for (int i = 0; i < 3; i++) if (previous[i] != null)
      for (int j = 0; j < ranked.length; j++) if (!used[j] && previous[i].text.equals(ranked[j].text))
      { slots[i] = ranked[j]; used[j] = true; break; }
    int next = 0;
    for (int i = 0; i < 3; i++) if (slots[i] == null)
    {
      while (next < ranked.length && used[next]) next++;
      if (next < ranked.length) { slots[i] = ranked[next]; used[next++] = true; }
    }
    previous = slots;
    return slots;
  }
}
