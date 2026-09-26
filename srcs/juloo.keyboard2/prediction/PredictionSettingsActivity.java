package juloo.keyboard2.prediction;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import juloo.keyboard2.R;

public final class PredictionSettingsActivity extends Activity
{
  private final Handler handler = new Handler();
  private TextView status;
  private ProgressBar progress;
  private Button action, remove;
  private final Runnable refresh = new Runnable() {
    public void run() { update(); handler.postDelayed(this, 300); }
  };

  @Override public void onCreate(Bundle state)
  {
    super.onCreate(state);
    setTitle(R.string.prediction_title);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    int pad = (int)(20 * getResources().getDisplayMetrics().density);
    layout.setPadding(pad, pad, pad, pad);
    TextView description = new TextView(this);
    description.setText(R.string.prediction_description);
    layout.addView(description);
    status = new TextView(this); layout.addView(status);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100); layout.addView(progress);
    action = new Button(this); layout.addView(action);
    remove = new Button(this); remove.setText(R.string.prediction_remove); layout.addView(remove);
    Button clear = new Button(this); clear.setText(R.string.prediction_clear); layout.addView(clear);
    clear.setOnClickListener(v -> {
      // Invalidate controller caches and its pending learning before clearing disk.
      android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      prefs.edit().putLong("learning_clear_epoch", System.currentTimeMillis()).apply();
      clear.setEnabled(false);
      PhraseStore.get(this).clear(success -> handler.post(() -> {
        clear.setEnabled(true);
        android.widget.Toast.makeText(this, success ? R.string.prediction_cleared : R.string.prediction_clear_failed, android.widget.Toast.LENGTH_SHORT).show();
      }));
    });
    TextView attribution = new TextView(this);
    attribution.setText(R.string.prediction_attribution); layout.addView(attribution);
    Button licenses = new Button(this);
    licenses.setText(R.string.prediction_licenses); layout.addView(licenses);
    licenses.setOnClickListener(v -> {
      try (java.io.InputStream in = getAssets().open("prediction-licenses.txt"))
      {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        new android.app.AlertDialog.Builder(this).setTitle(R.string.prediction_licenses)
          .setMessage(out.toString("UTF-8")).setPositiveButton(android.R.string.ok, null).show();
      }
      catch (java.io.IOException e) { /* Bundled asset; no user text or network involved. */ }
    });
    action.setOnClickListener(v -> {
      if (ModelStore.downloading) ModelStore.cancel(); else ModelStore.download(this);
      update();
    });
    remove.setOnClickListener(v -> {
      PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("llm_predictions", false).apply();
      if (!ModelStore.remove(this)) ModelStore.failed = true;
      update();
    });
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    scroll.addView(layout);
    setContentView(scroll);
  }

  @Override protected void onStart() { super.onStart(); handler.post(refresh); }
  @Override protected void onStop() { handler.removeCallbacks(refresh); super.onStop(); }

  private void update()
  {
    boolean installed = ModelStore.file(this).isFile();
    boolean downloading = ModelStore.downloading;
    int message = !ModelStore.supported() ? R.string.prediction_unsupported :
      downloading ? R.string.prediction_downloading :
      ModelStore.runtimeFailed ? R.string.prediction_runtime_failed :
      ModelStore.failed ? R.string.prediction_download_failed :
      installed ? R.string.prediction_ready :
      ModelStore.legacyFile(this).isFile() ? R.string.prediction_legacy : R.string.prediction_not_installed;
    status.setText(message);
    progress.setProgress((int)(100 * ModelStore.downloaded / ModelStore.SIZE));
    progress.setVisibility(downloading ? android.view.View.VISIBLE : android.view.View.GONE);
    action.setText(downloading ? android.R.string.cancel : R.string.prediction_download);
    action.setEnabled(ModelStore.supported() && (downloading || !installed));
    remove.setEnabled((installed || ModelStore.legacyFile(this).isFile()) && !downloading);
  }
}
