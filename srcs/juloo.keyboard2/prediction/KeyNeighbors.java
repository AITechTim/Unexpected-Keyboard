package juloo.keyboard2.prediction;

import java.util.*;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;

public final class KeyNeighbors
{
  public static Map<Character, String> from(KeyboardData layout)
  {
    Map<Character, float[]> positions = new LinkedHashMap<>();
    float y = 0;
    for (KeyboardData.Row row : layout.rows)
    {
      y += row.shift;
      float x = 0;
      for (KeyboardData.Key key : row.keys)
      {
        x += key.shift;
        KeyValue value = key.keys[0];
        if (value != null && value.getKind() == KeyValue.Kind.Char && Character.isLetter(value.getChar()))
          positions.put(Character.toLowerCase(value.getChar()), new float[]{x + key.width / 2, y + row.height / 2});
        x += key.width;
      }
      y += row.height;
    }
    return fromPositions(positions);
  }
  public static Map<Character, String> fromPositions(Map<Character, float[]> positions)
  {
    Map<Character, String> result = new HashMap<>();
    for (char c : positions.keySet())
    {
      List<Character> near = new ArrayList<>();
      float[] p = positions.get(c);
      for (char other : positions.keySet()) if (c != other)
      {
        float[] q = positions.get(other);
        if (Math.abs(p[0] - q[0]) <= 1.3f && Math.abs(p[1] - q[1]) <= 1.1f) near.add(other);
      }
      Collections.sort(near, (a,b) -> Float.compare(distance(p, positions.get(a)), distance(p, positions.get(b))));
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < Math.min(6, near.size()); i++) s.append(near.get(i));
      result.put(c, s.toString());
    }
    return result;
  }
  private static float distance(float[] p, float[] q) { return (p[0]-q[0])*(p[0]-q[0]) + (p[1]-q[1])*(p[1]-q[1]); }
}
