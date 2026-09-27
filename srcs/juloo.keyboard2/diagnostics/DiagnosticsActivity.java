package juloo.keyboard2.diagnostics;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.*;
import android.view.View;
import java.io.OutputStream;

/** On-device diagnostics: no upload, clipboard access, or automatic sharing. */
public final class DiagnosticsActivity extends Activity
{
  private static final int SAVE = 1;
  @Override public void onCreate(Bundle state)
  {
    super.onCreate(state);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    KeyboardDiagnostics.configure(this, prefs);
    LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 24, 24, 24);
    ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
    TextView title = new TextView(this); title.setText("Keyboard diagnostics · 2.1.2"); title.setTextSize(24); layout.addView(title);
    TextView help = new TextView(this);
    help.setText("Test the same backspace in each mode. Close and reopen the keyboard after changing mode. No text or query contents are recorded. Diagnostics stay on this phone until you export them.\n\nNo native predictions unloads the model. No optional reads also disables suggestions and automatic capitalisation while testing. Deferred reads moves refreshes after deletion.\n");
    layout.addView(help);
    Spinner modes = new Spinner(this);
    modes.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
      new String[]{"Original behavior", "No native predictions", "No optional text reads", "Deferred text reads"}));
    modes.setSelection(java.util.Arrays.asList(KeyboardDiagnostics.MODES).indexOf(prefs.getString("keyboard_diagnostic_mode", "baseline")));
    layout.addView(modes);
    modes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(AdapterView<?> parent) {}
      public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        prefs.edit().putString("keyboard_diagnostic_mode", KeyboardDiagnostics.MODES[pos]).apply();
        KeyboardDiagnostics.configure(DiagnosticsActivity.this, prefs);
      }
    });
    Switch record = new Switch(this); record.setText("Record diagnostic metadata");
    record.setChecked(prefs.getBoolean("keyboard_diagnostic_record", false)); layout.addView(record);
    record.setOnCheckedChangeListener((button, enabled) -> {
      prefs.edit().putBoolean("keyboard_diagnostic_record", enabled).apply();
      KeyboardDiagnostics.configure(this, prefs);
    });
    Button export = new Button(this); export.setText("Export diagnostic JSON"); layout.addView(export);
    export.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
      .setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
      .putExtra(Intent.EXTRA_TITLE, "unexpected-keyboard-diagnostics.json"), SAVE));
    Button clear = new Button(this); clear.setText("Clear diagnostic records"); layout.addView(clear);
    clear.setOnClickListener(v -> {KeyboardDiagnostics.clear(); Toast.makeText(this, "Records cleared", Toast.LENGTH_SHORT).show();});
  }
  @Override protected void onActivityResult(int request, int result, Intent data)
  {
    super.onActivityResult(request, result, data);
    if (request != SAVE || result != RESULT_OK || data == null || data.getData() == null) return;
    try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
      if (out == null) throw new java.io.IOException();
      out.write(KeyboardDiagnostics.export().getBytes("UTF-8"));
      Toast.makeText(this, "Diagnostics exported", Toast.LENGTH_SHORT).show();
    } catch (Exception failure) { Toast.makeText(this, "Could not export diagnostics", Toast.LENGTH_LONG).show(); }
  }
}
