package co.mitro.mitro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class NotificationCopyActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = new Intent();
    intent.setAction("copy");
    intent.putExtra("copy", "copy");
    sendBroadcast(intent);
    finish();
  }
}