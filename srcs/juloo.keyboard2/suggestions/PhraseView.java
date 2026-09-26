package juloo.keyboard2.suggestions;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;
import juloo.keyboard2.Config;
import juloo.keyboard2.prediction.Candidate;

/** Fixed-height optional row. Empty results never move the keyboard. */
public final class PhraseView extends TextView
{
  private Candidate candidate, pressed;
  private boolean touching;
  public PhraseView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setOnTouchListener((v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { touching = true; pressed = candidate; }
      else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) { touching = false; pressed = null; }
      return false;
    });
    setOnClickListener(v -> {
      Candidate chosen = touching ? pressed : candidate;
      touching = false; pressed = null;
      if (chosen != null) Config.globalConfig().handler.candidate_entered(chosen);
    });
  }
  public void setCandidate(Candidate item)
  {
    candidate = item;
    fitText();
    setEnabled(candidate != null);
  }
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh)
  { super.onSizeChanged(w, h, oldw, oldh); fitText(); }

  private void fitText()
  {
    int width = getWidth() - getPaddingLeft() - getPaddingRight();
    int size = 16;
    setTextSize(size);
    if (candidate != null && width > 0)
    {
      while (size > 11 && getPaint().measureText(candidate.text) > width) setTextSize(--size);
      // Never insert a hidden suffix of an ellipsized phrase.
      if (getPaint().measureText(candidate.text) > width) candidate = null;
    }
    setText(candidate == null ? "" : candidate.text);
    setEnabled(candidate != null);
    setContentDescription(candidate == null ? null : getResources().getString(juloo.keyboard2.R.string.prediction_accept_phrase, candidate.text));
  }
  public void clear() { pressed = null; setCandidate(null); }
}
