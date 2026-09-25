package juloo.keyboard2.prediction;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

public final class PredictionEligibility
{
  public static boolean allows(int type, int options)
  {
    if ((type & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
        || (type & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        || (options & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false;
    switch (type & InputType.TYPE_MASK_VARIATION)
    {
      case InputType.TYPE_TEXT_VARIATION_NORMAL:
      case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
      case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
      case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
      case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
        return true;
      default: return false;
    }
  }
}
