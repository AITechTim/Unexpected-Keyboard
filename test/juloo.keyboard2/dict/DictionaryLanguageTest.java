package juloo.keyboard2.dict;

import android.content.Context;
import android.content.SharedPreferences;
import juloo.keyboard2.Config;
import juloo.keyboard2.DeviceLocales;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class DictionaryLanguageTest
{
  @Test public void migratesEnglishChoiceAndRemembersVariantsAcrossRestart()
  {
    Context context = RuntimeEnvironment.getApplication();
    SharedPreferences prefs = context.getSharedPreferences("language-dictionaries", 0);
    Dictionaries dictionaries = new Dictionaries(context, prefs);
    Config.initGlobalConfig(context.getSharedPreferences("language-config", 0), context.getResources(), false, dictionaries);
    Config config = Config.globalConfig();
    config.device_locales = DeviceLocales.load(context);
    String tag = config.device_locales.default_ == null ? "" : config.device_locales.default_.lang_tag;
    prefs.edit().putString("selection:" + tag + "-" + config.get_current_layout(), "en_GB").commit();
    assertEquals("en_GB", dictionaries.get_selected(config));
    config.prediction_language = "de";
    assertNull(dictionaries.get_selected(config));
    dictionaries.set_selected(config, "de_AT");
    dictionaries = new Dictionaries(context, prefs);
    assertEquals("de_AT", dictionaries.get_selected(config));
    dictionaries.set_current_dictionary(config, "de_AT");
    assertNull(config.current_dictionary); // Missing dictionary never changes language.
    assertEquals("de", config.prediction_language);
    config.prediction_language = "en";
    assertEquals("en_GB", dictionaries.get_selected(config));
  }
}
