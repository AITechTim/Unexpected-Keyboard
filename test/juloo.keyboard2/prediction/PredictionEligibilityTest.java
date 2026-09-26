package juloo.keyboard2.prediction;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class PredictionEligibilityTest
{
  @Test public void allowsOrdinaryText()
  {
    assertTrue(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT, 0));
    assertTrue(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, 0));
  }
  @Test public void rejectsSensitiveAndNonProseFields()
  {
    int[] variations = {InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_URI,
      InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS};
    for (int v : variations) assertFalse(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT | v, 0));
    assertFalse(PredictionEligibility.allows(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0));
    assertFalse(PredictionEligibility.allows(InputType.TYPE_NULL, 0));
    assertFalse(PredictionEligibility.allows(InputType.TYPE_CLASS_PHONE, 0));
  }
  @Test public void honorsEditorPrivacyAndSuggestionFlags()
  {
    assertFalse(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
    assertFalse(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0));
    assertTrue(PredictionEligibility.allows(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, 0));
  }
}
