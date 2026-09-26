package juloo.keyboard2.prediction;

import java.util.*;

/** Bounded phrase counts, independent of Android and model inference. Worker-owned. */
public final class PhraseMemory
{
  static final long RETENTION = 90L * 24 * 60 * 60 * 1000;
  static final int LIMIT = 10000;
  static final class Entry
  {
    int count;
    long time;
    Entry(int count, long time) { this.count = count; this.time = time; }
  }
  final Map<String, Entry> entries = new HashMap<>();

  static String join(List<String> words)
  {
    StringBuilder result = new StringBuilder();
    for (String w : words) { if (result.length() > 0) result.append(' '); result.append(w); }
    return result.toString();
  }

  public void learn(Collection<String> phrases, long now)
  {
    for (String phrase : phrases)
    {
      Entry e = entries.get(phrase);
      entries.put(phrase, new Entry(e == null ? 1 : Math.min(1000000, e.count + 1), now));
    }
    prune(now);
  }

  void prune(long now)
  {
    Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
    while (it.hasNext()) if (now - it.next().getValue().time > RETENTION) it.remove();
    if (entries.size() <= LIMIT) return;
    List<String> keys = new ArrayList<>(entries.keySet());
    Collections.sort(keys, (a, b) -> Long.compare(entries.get(a).time, entries.get(b).time));
    for (int i = 0; i < keys.size() - LIMIT; i++) entries.remove(keys.get(i));
  }

  public static final class Match
  {
    public final String[] words;
    public final String phrase;
    Match(String[] words, String phrase) { this.words = words; this.phrase = phrase; }
  }

  public Match query(PredictionSnapshot snapshot, long now)
  {
    prune(now);
    String context = snapshot.context.toLowerCase(Locale.ROOT);
    List<String> keys = new ArrayList<>(entries.keySet());
    Map<String, Integer> lengths = new HashMap<>();
    for (String key : keys)
    {
      if (entries.get(key).count < 2) continue;
      String[] words = key.split(" ");
      for (int n = Math.min(5, words.length - 1); n >= 1; n--)
      {
        String head = join(Arrays.asList(words).subList(0, n)).toLowerCase(Locale.ROOT) + " ";
        int start = context.length() - head.length();
        if (start >= 0 && context.endsWith(head)
            && (start == 0 || !PredictionSnapshot.wordChar(context.codePointBefore(start))))
        {
          String tail = join(Arrays.asList(words).subList(n, words.length));
          if (tail.toLowerCase(Locale.ROOT).startsWith(snapshot.prefix.toLowerCase(Locale.ROOT))) lengths.put(key, n);
          break;
        }
      }
    }
    Iterator<String> it = keys.iterator();
    while (it.hasNext()) if (!lengths.containsKey(it.next())) it.remove();
    Collections.sort(keys, (a, b) -> {
      int d = Integer.compare(lengths.get(b), lengths.get(a));
      if (d == 0) d = Integer.compare(entries.get(b).count, entries.get(a).count);
      if (d == 0) d = Long.compare(entries.get(b).time, entries.get(a).time);
      if (d == 0) d = Integer.compare(b.length(), a.length());
      return d == 0 ? a.compareTo(b) : d;
    });
    LinkedHashSet<String> result = new LinkedHashSet<>();
    String phrase = null;
    for (String key : keys)
    {
      String[] words = key.split(" ");
      String tail = join(Arrays.asList(words).subList(lengths.get(key), words.length));
      if (!snapshot.prefix.isEmpty()) tail = snapshot.prefix + tail.substring(snapshot.prefix.length());
      String word = tail.split(" ")[0];
      if (snapshot.validWord(word) && result.size() < 3) result.add(word);
      if (phrase == null && snapshot.validPhrase(tail)) phrase = tail;
    }
    return new Match(result.toArray(new String[0]), phrase);
  }
}
