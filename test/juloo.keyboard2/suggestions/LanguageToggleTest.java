package juloo.keyboard2.suggestions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import juloo.keyboard2.Config;
import juloo.keyboard2.DeviceLocales;
import juloo.keyboard2.R;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.prediction.PredictionSnapshot;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class LanguageToggleTest
{
  @Test public void toggleStaysVisibleWithCandidatesPersistsAndKeepsLayout()
  {
    Context context = RuntimeEnvironment.getApplication();
    context.setTheme(R.style.appTheme);
    context.getTheme().applyStyle(R.style.Dark, true);
    Dictionaries dictionaries = Dictionaries.instance(context);
    android.content.SharedPreferences prefs = context.getSharedPreferences("toggle", 0);
    Config.initGlobalConfig(prefs, context.getResources(), false, dictionaries);
    Config config = Config.globalConfig();
    config.device_locales = DeviceLocales.load(context);
    config.should_show_dictionary_switch = true;
    config.learn_writing = true;
    int layout = config.get_current_layout();
    LayoutInflater inflater = LayoutInflater.from(context).cloneInContext(context);
    // The keybed needs an IME Window; this test exercises the real suggestion rows.
    inflater.setFactory((name, ctx, attrs) -> name.equals("juloo.keyboard2.Keyboard2View") ? new View(ctx) : null);
    View keyboard = inflater.inflate(R.layout.keyboard, null);
    CandidatesView candidates = keyboard.findViewById(R.id.candidates_view);
    candidates.refresh_config(config);
    Suggestions suggestions = new Suggestions(s -> {}, config);
    suggestions.set_predictions(new PredictionSnapshot(1, "", "", 0), new String[]{"one", "two", "three"});
    candidates.set_candidates(suggestions);
    TextView language = keyboard.findViewById(R.id.candidates_lang_name);
    assertEquals(View.VISIBLE, language.getVisibility());
    assertEquals("EN", language.getText().toString());
    language.performClick();
    assertEquals("de", prefs.getString("prediction_language", ""));
    Config.initGlobalConfig(prefs, context.getResources(), false, dictionaries);
    assertEquals("de", Config.globalConfig().prediction_language);
    assertEquals(layout, Config.globalConfig().get_current_layout());
  }
}
