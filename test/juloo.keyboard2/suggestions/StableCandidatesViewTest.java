package juloo.keyboard2.suggestions;

import android.content.Context;
import android.view.*;
import android.os.Looper;
import juloo.keyboard2.Config;
import juloo.keyboard2.DeviceLocales;
import juloo.keyboard2.R;
import juloo.keyboard2.dict.Dictionaries;
import juloo.keyboard2.prediction.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class StableCandidatesViewTest
{
  private CandidatesView view;
  private Suggestions suggestions;
  @Before public void setup()
  {
    Context context = RuntimeEnvironment.getApplication();
    context.setTheme(R.style.appTheme); context.getTheme().applyStyle(R.style.Dark, true);
    Config.initGlobalConfig(context.getSharedPreferences("stable-slots", 0), context.getResources(), false, Dictionaries.instance(context));
    Config config = Config.globalConfig(); config.device_locales = DeviceLocales.load(context);
    config.keyboard_rows_height_pixels = 60;
    LayoutInflater inflater = LayoutInflater.from(context).cloneInContext(context);
    inflater.setFactory((name, ctx, attrs) -> name.equals("juloo.keyboard2.Keyboard2View") ? new View(ctx) : null);
    View keyboard = inflater.inflate(R.layout.keyboard, null);
    view = keyboard.findViewById(R.id.candidates_view); view.refresh_config(config);
    suggestions = new Suggestions(s -> view.set_candidates(s), config);
  }
  private void layout()
  { view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY)); view.layout(0,0,1080,60); }
  @Test public void emptySlotsEmojiAndLanguageKeepIdenticalBounds()
  {
    suggestions.set_predictions(new PredictionSnapshot(1,"","",0),new String[]{"one","two","three"}); layout();
    int[] left=new int[4],width=new int[4];
    for(int i=0;i<4;i++){left[i]=view._item_views[i].getLeft();width[i]=view._item_views[i].getWidth();assertTrue(width[i]>0);}
    for(int count=0;count<=3;count++)
    {
      String[] words=java.util.Arrays.copyOf(new String[]{"one","two","three"},count);
      suggestions.set_predictions(new PredictionSnapshot(count+2,"","",0),words);
      suggestions.emoji_suggestion=count%2==0?"😀":null;view.set_candidates(suggestions);layout();
      for(int i=0;i<4;i++) {assertEquals(left[i],view._item_views[i].getLeft());assertEquals(width[i],view._item_views[i].getWidth());assertEquals(View.VISIBLE,view._item_views[i].getVisibility());}
      assertEquals(View.VISIBLE,view._lang_name_view.getVisibility());
    }
    suggestions.invalidate();
    for(int i=0;i<4;i++){assertFalse(view._item_views[i].isEnabled());assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,view._item_views[i].getImportantForAccessibility());}
  }
  @Test public void updatesFreezeDuringPressAndCancelAppliesLatest()
  {
    suggestions.set_predictions(new PredictionSnapshot(1,"help","",4),new String[]{"help","helper","helping"});layout();
    Candidate old=view._items[1];
    view._item_views[1].dispatchTouchEvent(MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,10,10,0));
    suggestions.set_predictions(new PredictionSnapshot(2,"helpe","",5),new String[]{"helped","helper"});
    assertSame(old,view._items[1]);assertSame(old,view._pressed_items[1]);
    assertEquals("help",view._items[0].text);
    view._item_views[1].dispatchTouchEvent(MotionEvent.obtain(1,2,MotionEvent.ACTION_CANCEL,10,10,0));
    assertEquals("helped",view._items[0].text);assertEquals("helper",view._items[1].text);
    assertEquals(2,view._items[1].snapshot.revision);assertNull(view.pending);
  }
  @Test public void clickAcceptsCandidateCapturedBeforeResultArrived()
  {
    final Candidate[] accepted = new Candidate[1];
    Config.globalConfig().handler = new Config.IKeyEventHandler() {
      public void key_down(juloo.keyboard2.KeyValue v, boolean swipe) {}
      public void key_up(juloo.keyboard2.KeyValue v, juloo.keyboard2.Pointers.Modifiers m) {}
      public void mods_changed(juloo.keyboard2.Pointers.Modifiers m) {}
      public void suggestion_entered(String text) {}
      public void candidate_entered(Candidate candidate) { accepted[0] = candidate; }
    };
    suggestions.set_predictions(new PredictionSnapshot(1,"help","",4),new String[]{"help","helper","helping"});layout();
    Candidate old = view._items[1];
    view._item_views[1].dispatchTouchEvent(MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,10,10,0));
    suggestions.set_predictions(new PredictionSnapshot(2,"helpe","",5),new String[]{"helped","helper"});
    view._item_views[1].performClick();
    assertSame(old,accepted[0]); // The controller's separate snapshot check rejects it if text changed.
    view._item_views[1].dispatchTouchEvent(MotionEvent.obtain(1,2,MotionEvent.ACTION_CANCEL,10,10,0));
    assertEquals(2,view._items[1].snapshot.revision);
  }
  @Test public void phraseMigrationRunsOnceAndPreservesLaterOptIn()
  {
    Context context=RuntimeEnvironment.getApplication();
    android.content.SharedPreferences prefs=context.getSharedPreferences("phrase-migration",0);
    prefs.edit().clear().putBoolean("phrase_predictions",true).commit();
    Config.initGlobalConfig(prefs,context.getResources(),false,Dictionaries.instance(context));
    assertFalse(Config.globalConfig().phrase_predictions_enabled);assertTrue(prefs.getBoolean("phrase_row_v2",false));
    prefs.edit().putBoolean("phrase_predictions",true).commit();
    Config.initGlobalConfig(prefs,context.getResources(),false,Dictionaries.instance(context));
    assertTrue(Config.globalConfig().phrase_predictions_enabled);
  }
}
