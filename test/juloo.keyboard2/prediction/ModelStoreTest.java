package juloo.keyboard2.prediction;

import android.content.Context;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ModelStoreTest
{
  @Test public void legacyModelIsEnglishOnlyAndUpgradeTakesPriority() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    ModelStore.remove(context);
    assertFalse(ModelStore.forLanguage(context, "de").exists());
    assertTrue(ModelStore.legacyFile(context).createNewFile());
    assertEquals(ModelStore.legacyFile(context), ModelStore.forLanguage(context, "en"));
    assertFalse(ModelStore.forLanguage(context, "de").exists());
    File partial = new File(ModelStore.file(context).getPath() + ".part");
    assertTrue(partial.createNewFile());
    assertFalse(ModelStore.forLanguage(context, "de").exists());
    assertTrue(ModelStore.file(context).createNewFile());
    assertEquals(ModelStore.file(context), ModelStore.forLanguage(context, "en"));
    assertEquals(ModelStore.file(context), ModelStore.forLanguage(context, "de"));
    assertTrue(ModelStore.remove(context));
    assertFalse(ModelStore.legacyFile(context).exists());
    assertFalse(ModelStore.file(context).exists());
    partial.delete();
  }
}
