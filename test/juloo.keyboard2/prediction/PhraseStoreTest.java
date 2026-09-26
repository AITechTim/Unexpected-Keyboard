package juloo.keyboard2.prediction;

import android.content.Context;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class PhraseStoreTest
{
  private PhraseStore store;
  private void clear() throws Exception
  {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Boolean> success = new AtomicReference<>();
    store.clear(ok -> { success.set(ok); latch.countDown(); });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertTrue(success.get());
  }
  private PhraseMemory.Match query() throws Exception
  {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PhraseMemory.Match> result = new AtomicReference<>();
    store.query(new PredictionSnapshot(1, "see ", "", 4), match -> { result.set(match); latch.countDown(); });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    return result.get();
  }
  @Before public void setup() throws Exception { store = new PhraseStore(RuntimeEnvironment.getApplication()); clear(); }
  @After public void cleanup() throws Exception { clear(); store.close(); }

  @Test public void persistsOutsideBackupsReloadsAndClears() throws Exception
  {
    store.learn(Arrays.asList("see you tomorrow", "see you tomorrow"));
    assertEquals("you tomorrow", query().phrase);
    Context context = RuntimeEnvironment.getApplication();
    assertTrue(new File(context.getNoBackupFilesDir(), "phrases.db").isFile());
    assertFalse(context.getDatabasePath("phrases.db").exists());
    java.lang.reflect.Field memory = PhraseStore.class.getDeclaredField("memory");
    memory.setAccessible(true); memory.set(store, null); // Simulate a process cache reload.
    assertEquals("you tomorrow", query().phrase);
    clear();
    assertEquals(0, query().words.length);
    memory.set(store, null);
    assertEquals(0, query().words.length);
  }
}
