package juloo.keyboard2.suggestions;

import android.content.Context;
import juloo.keyboard2.prediction.Candidate;
import android.os.Build.VERSION;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.R;

public class CandidatesView extends LinearLayout
{
  static final int NUM_CANDIDATES = 4;

  /** Candidates currently visible. Entries can be [null] when there are less
      than [NUM_CANDIDATES] suggestions.
      - Entries at indexes [0] to [2] are word suggestions.
      - Entry at index [3] is the emoji suggestion. */
  Candidate[] pending;
  Candidate[] _items = new Candidate[NUM_CANDIDATES];
  boolean[] _touching = new boolean[NUM_CANDIDATES];
  Candidate[] _pressed_items = new Candidate[NUM_CANDIDATES];

  /** Empty slots keep their geometry and are disabled for touch and accessibility. */
  TextView[] _item_views = new TextView[NUM_CANDIDATES];

  /** Message when no dictionary is installed. Visible when no candidates are
      shown. Might be [null]. */
  View _status_no_dict = null;

  View _dictionary_switch_button;
  boolean should_show_dictionary_switch = false;

  TextView _lang_name_view;

  public CandidatesView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    setup_item_view(0, R.id.candidates_middle);
    setup_item_view(1, R.id.candidates_right);
    setup_item_view(2, R.id.candidates_left);
    setup_item_view(3, R.id.candidates_emoji);
    setup_dictionary_switch_button();
    _lang_name_view = (TextView)findViewById(R.id.candidates_lang_name);
  }

  public void set_candidates(Suggestions s)
  {
    Candidate[] next = new Candidate[NUM_CANDIDATES];
    for (int i = 0; i < Suggestions.MAX_COUNT; i++) next[i] = s.candidates[i];
    next[3] = s.emoji_suggestion == null ? null : new Candidate(s.emoji_suggestion, Candidate.Source.EMOJI, s.snapshot());
    for (boolean touching : _touching) if (touching) { pending = next; return; }
    render(next);
    int dict_vis = View.VISIBLE;
    _dictionary_switch_button.setVisibility(View.GONE);
    _lang_name_view.setVisibility(dict_vis);
  }

  private void render(Candidate[] next)
  {
    _items = next;
    boolean any = false;
    for (int i = 0; i < NUM_CANDIDATES; i++)
    {
      Candidate item = next[i];
      any |= item != null;
      TextView view = _item_views[i];
      view.setText(item == null ? "" : item.text);
      view.setEnabled(item != null);
      view.setImportantForAccessibility(item == null ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      view.setVisibility(View.VISIBLE);
    }
    if (any && _status_no_dict != null) _status_no_dict.setVisibility(View.GONE);
  }

  private void flushPending()
  {
    for (boolean touching : _touching) if (touching) return;
    if (pending != null) { Candidate[] next = pending; pending = null; render(next); }
  }

  public void clear_candidates()
  {
    pending = null;
    for (int i = 0; i < NUM_CANDIDATES; i++) _pressed_items[i] = null;
    render(new Candidate[NUM_CANDIDATES]);
  }

  public void refresh_config(Config config)
  {
    clear_candidates();
    // The status message indicates whether the dictionaries should be
    // installed.
    if (config.current_dictionary == null && !config.llm_predictions_enabled && !config.learn_writing)
      inflate_status_no_dict(config);
    else if (_status_no_dict != null)
      _status_no_dict.setVisibility(View.GONE);
    should_show_dictionary_switch = config.should_show_dictionary_switch;
    set_sizes(config);
    _lang_name_view.setText(config.prediction_language.toUpperCase(Locale.ROOT));
    _lang_name_view.setContentDescription(getResources().getString(R.string.prediction_language_toggle, config.prediction_language.toUpperCase(Locale.ROOT)));
    _lang_name_view.setOnClickListener(v -> Config.globalPrefs().edit()
        .putString("prediction_language", config.prediction_language.equals("en") ? "de" : "en").apply());
    _lang_name_view.setOnLongClickListener(v -> {
      Config.globalConfig().handler.key_up(KeyValue.getKeyByName("change_dictionary"), Pointers.Modifiers.EMPTY);
      return true;
    });
    _dictionary_switch_button.setVisibility(View.GONE);
    _lang_name_view.setVisibility(View.VISIBLE);
  }

  /** Set the height of the suggestion row and the text size. */
  void set_sizes(Config config)
  {
    // Make the candidates view about as high as a keyboard row.
    float row_height = config.keyboard_rows_height_pixels * (1 - config.key_vertical_margin);
    ViewGroup.MarginLayoutParams p =
      (ViewGroup.MarginLayoutParams)getLayoutParams();
    p.height = (int)row_height;
    setLayoutParams(p);
    // Match the size of labels on the keyboard.
    float text_size = row_height * config.characterSize * config.labelTextSize;
    for (int i = 0; i < NUM_CANDIDATES; i++)
    {
      TextView v = _item_views[i];
      // Set text size and enable auto size if supported.
      if (VERSION.SDK_INT < 26)
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size);
      else
        v.setAutoSizeTextTypeUniformWithConfiguration(
            (int)(text_size / 2.), (int)text_size, 1, TypedValue.COMPLEX_UNIT_PX);
    }
  }

  void inflate_status_no_dict(Config config)
  {
    if (_status_no_dict == null)
    {
      _status_no_dict = View.inflate(getContext(),
          R.layout.candidates_status_no_dict, null);
      ((android.widget.FrameLayout)findViewById(R.id.candidates_word_area)).addView(_status_no_dict);
    }
    Locale current_locale = Locale.forLanguageTag(config.prediction_language);
    TextView tv = _status_no_dict.findViewById(android.R.id.text1);
    if (tv != null && current_locale != null)
      tv.setText(getResources().getString(
            R.string.candidates_status_click_to_install,
            current_locale.getDisplayName()));
    _status_no_dict.setVisibility(View.VISIBLE);
  }

  private void setup_item_view(final int item_index, int item_id)
  {
    TextView v = (TextView)findViewById(item_id);
    v.setOnTouchListener((view, event) -> {
      if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
      { _touching[item_index] = true; _pressed_items[item_index] = _items[item_index]; }
      else if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
      { _touching[item_index] = false; _pressed_items[item_index] = null; flushPending(); }
      else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP)
        post(() -> { _touching[item_index] = false; _pressed_items[item_index] = null; flushPending(); });
      return false;
    });
    v.setOnClickListener(new View.OnClickListener()
        {
          @Override
          public void onClick(View _v)
          {
            Candidate it = _touching[item_index] ? _pressed_items[item_index] : _items[item_index];
            _touching[item_index] = false;
            _pressed_items[item_index] = null;
            if (it != null)
              Config.globalConfig().handler.candidate_entered(it);
          }
        });
    v.setVisibility(View.VISIBLE);
    v.setEnabled(false);
    _item_views[item_index] = v;
  }

  void setup_dictionary_switch_button()
  {
    _dictionary_switch_button = findViewById(R.id.dictionary_switch);
    _dictionary_switch_button.setOnClickListener(new View.OnClickListener()
        {
          @Override
          public void onClick(View _v)
          {
            Config.globalConfig().handler.key_up(
                KeyValue.getKeyByName("change_dictionary"),
                Pointers.Modifiers.EMPTY);
          }
        });
  }

  /** Whether the candidates view should be shown for a given editor. */
  public static boolean should_show(EditorInfo info)
  {
    int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
    switch (info.inputType & InputType.TYPE_MASK_CLASS)
    {
      case InputType.TYPE_CLASS_TEXT:
        switch (variation)
        {
          case InputType.TYPE_TEXT_VARIATION_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
          case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
            return false;
          default:
            /* Editor requested that we don't show suggestions. Enable
               suggestions anyway when the flags [NO_SUGGESTIONS] and
               [AUTO_CORRECT] are present at the same time. This happens with
               Google Keep. */
            if (juloo.keyboard2.prediction.PredictionEligibility.suppressesSuggestions(info.inputType))
              return false;
            return true;
        }
      case InputType.TYPE_CLASS_NUMBER:
        // Beware of TYPE_NUMBER_VARIATION_PASSWORD
        return false;
      default: return false;
    }
  }
}
