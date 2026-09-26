package juloo.keyboard2.prediction;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** All disk work and cache access is serialized away from the IME thread. */
public final class PhraseStore implements AutoCloseable
{
  private static PhraseStore instance;
  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicLong generation = new AtomicLong(), queryGeneration = new AtomicLong();
  private PhraseMemory memory;
  PhraseStore(Context context) { this.context = context.getApplicationContext(); }
  public static synchronized PhraseStore get(Context context)
  { if (instance == null) instance = new PhraseStore(context); return instance; }

  private SQLiteDatabase open()
  {
    SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File(context.getNoBackupFilesDir(), "phrases.db"), null);
    db.execSQL("CREATE TABLE IF NOT EXISTS phrases (phrase TEXT PRIMARY KEY, count INTEGER NOT NULL, seen INTEGER NOT NULL)");
    return db;
  }
  private void load()
  {
    if (memory != null) return;
    PhraseMemory loaded = new PhraseMemory();
    try (SQLiteDatabase db = open())
    {
      db.delete("phrases", "seen < ?", new String[]{Long.toString(System.currentTimeMillis() - PhraseMemory.RETENTION)});
      try (Cursor c = db.rawQuery("SELECT phrase,count,seen FROM phrases ORDER BY seen DESC LIMIT 10000", null))
      { while (c.moveToNext()) loaded.entries.put(c.getString(0), new PhraseMemory.Entry(c.getInt(1), c.getLong(2))); }
    }
    loaded.prune(System.currentTimeMillis());
    memory = loaded;
  }
  public void learn(List<String> phrases)
  {
    if (phrases.isEmpty()) return;
    long epoch = generation.get();
    executor.execute(() -> {
      if (generation.get() != epoch) return;
      try
      {
        load(); memory.learn(phrases, System.currentTimeMillis());
        try (SQLiteDatabase db = open())
        {
          db.beginTransaction();
          try
          {
            db.delete("phrases", "seen < ?", new String[]{Long.toString(System.currentTimeMillis() - PhraseMemory.RETENTION)});
            for (String phrase : phrases)
            {
              PhraseMemory.Entry entry = memory.entries.get(phrase);
              if (entry == null) continue;
              ContentValues v = new ContentValues();
              v.put("phrase", phrase); v.put("count", entry.count); v.put("seen", entry.time);
              db.insertWithOnConflict("phrases", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.execSQL("DELETE FROM phrases WHERE phrase NOT IN (SELECT phrase FROM phrases ORDER BY seen DESC LIMIT 10000)");
            db.setTransactionSuccessful();
          }
          finally { db.endTransaction(); }
        }
      }
      catch (android.database.SQLException e) { memory = null; }
    });
  }
  public interface Result { void accept(PhraseMemory.Match match); }
  public void query(PredictionSnapshot snapshot, Result result)
  {
    long epoch = generation.get(), query = queryGeneration.incrementAndGet();
    executor.execute(() -> {
      if (generation.get() != epoch || queryGeneration.get() != query) return;
      try
      {
        load(); PhraseMemory.Match match = memory.query(snapshot, System.currentTimeMillis());
        if (generation.get() == epoch) result.accept(match);
      }
      catch (android.database.SQLException e) { memory = null; }
    });
  }
  public interface Cleared { void accept(boolean success); }
  public void clear(Cleared done)
  {
    generation.incrementAndGet();
    executor.execute(() -> {
      memory = null;
      boolean success = false;
      try (SQLiteDatabase db = open())
      {
        try (Cursor setting = db.rawQuery("PRAGMA secure_delete=ON", null)) { setting.moveToFirst(); }
        db.delete("phrases", null, null);
        success = true;
      }
      catch (android.database.SQLException e) { /* Surface failure without logging personal text. */ }
      done.accept(success);
    });
  }
  @Override public void close() { invalidate(); executor.shutdown(); }
  public long revision() { return generation.get(); }
  public void invalidate() { generation.incrementAndGet(); }
}
