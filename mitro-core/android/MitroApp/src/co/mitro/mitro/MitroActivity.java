package co.mitro.mitro;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.flurry.android.FlurryAgent;

public class MitroActivity extends ActionBarActivity {
  public static final String FLURRY_API_KEY = "MMS8THFNQC4SZVQCJD7H";

  private BroadcastReceiver receiver = null;

  public MitroApplication getApp() {
    return (MitroApplication) getApplicationContext();
  }

  @SuppressLint("NewApi")
  @SuppressWarnings("deprecation")
  public void copyText(String label, String text) {
    if (android.os.Build.VERSION.SDK_INT < 11) {
        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(text);
    } else {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
      clipboard.setPrimaryClip(clip);
    }
  }

  public void maybeLogout() {
    // Log out unless "Keep me logged in" has been selected, in which case, privateKey will be set.
    if (getApp().isLoggedIn() &&
        getApp().getSharedPreference("privateKey") == null) {
      Log.d("Mitro", "logging out");
      getApp().getSecretManager().clear();
      getApp().getApiClient().logout();
    }
  }

  public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
        // Log out on screen off unless user has checked "Keep me logged in"
        maybeLogout();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    receiver = new ScreenReceiver();
    registerReceiver(receiver, filter);
  }

  @Override
  protected void onDestroy() {
    if (receiver != null) {
      unregisterReceiver(receiver);
    }
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // Activity is being destroyed.  We will not receive screen off events in this state
    // so we should log out.
    maybeLogout();
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (!getApp().isLoggedIn() && !(this instanceof LoginActivity)) {
      Log.d("Mitro", "starting login activity");
      Intent loginActivity = new Intent(MitroActivity.this, LoginActivity.class);
      startActivity(loginActivity);
      finish();
    }
    FlurryAgent.onStartSession(this, FLURRY_API_KEY);
  }

  @Override
  public void onStop() {
    super.onStop();
    FlurryAgent.onEndSession(this);
  }
}
