package co.mitro.mitro;

import java.util.List;

import com.google.common.base.Strings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TransformationMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import co.mitro.mitro.MitroApi.SecretIdentifier;

public class SecretViewActivity extends MitroActivity implements SecretManager.Listener {
  public enum PasswordAction {
    ACTION_VIEW,
    ACTION_COPY,
    ACTION_COPY_AND_GO
  }

  private Integer secretId;
  private String criticalData;
  private PasswordAction action;

  private TextView titleText;
  private TextView linkText;
  private TextView usernameText;
  private TextView passwordText;
  private TextView noteLabelText;
  private TextView noteText;
  private Button viewPasswordButton;
  private Button twoStepLoginButton;
  private ProgressBar spinner;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle bundle = getIntent().getExtras();
    secretId = Integer.valueOf(bundle.getInt("secret_id"));

    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeButtonEnabled(true);
    actionBar.setLogo(R.drawable.title_logo);
    actionBar.setDisplayUseLogoEnabled(true);
    actionBar.setDisplayShowTitleEnabled(false);

    SecretIdentifier secret = getSecret();
    if (secret == null) {
      Toast.makeText(this, "Could not view secret", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    if (secret.secretData.type.equals("note")) {
      setContentView(R.layout.view_secret_note);
    } else {
      setContentView(R.layout.view_secret);

      linkText = (TextView) findViewById(R.id.url);
      usernameText = (TextView) findViewById(R.id.username);
      twoStepLoginButton = (Button) findViewById(R.id.two_step_copy_button);
      noteLabelText = (TextView) findViewById(R.id.note_label);
      noteText = (TextView) findViewById(R.id.note);
    }

    titleText = (TextView) findViewById(R.id.title);
    passwordText = (TextView) findViewById(R.id.password);
    viewPasswordButton = (Button) findViewById(R.id.view_password_button);
    spinner = (ProgressBar) findViewById(R.id.spinner);

    updateView();
  }

  @Override
  public void onStart() {
    super.onStart();
    getApp().getSecretManager().addListener(this);
  }

  @Override
  public void onStop() {
    super.onStart();
    getApp().getSecretManager().removeListener(this);
  }

  public SecretIdentifier getSecret() {
    return getApp().getSecretManager().getSecret(secretId);
  }

  public void updateView() {
    SecretIdentifier secret = getSecret();
    if (secret == null) {
      return;
    }

    titleText.setText(secret.title);
    if (!secret.secretData.type.equals("note")) {
      if (!secret.secretData.loginUrl.startsWith("http")) {
        twoStepLoginButton.setVisibility(View.GONE);
      }

      linkText.setText(secret.getDomain());
      usernameText.setText(secret.secretData.username);

      // Show password note, if present.
      if (!Strings.isNullOrEmpty(secret.secretData.comment)) {
        noteLabelText.setVisibility(View.VISIBLE);
        noteText.setVisibility(View.VISIBLE);
        noteText.setText(secret.secretData.comment);
      } else {
        noteLabelText.setVisibility(View.GONE);
        noteText.setVisibility(View.GONE);
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    // Respond to the action bar's Up/Home button
      case android.R.id.home:
        this.finish();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void onCopyUsername(View v) {
    copyText("username", usernameText.getText().toString());
    Toast.makeText(this, "Username copied", Toast.LENGTH_SHORT).show();
  }

  public void getSecretCriticalData() {
    getApp().getSecretManager().getSecretCriticalData(secretId);
    spinner.setVisibility(View.VISIBLE);
  }

  public void toggleViewPassword() {
    TransformationMethod transMethod = passwordText.getTransformationMethod();
    if (transMethod instanceof PasswordTransformationMethod) {
      passwordText.setText(criticalData);
      passwordText.setTransformationMethod(null);
      viewPasswordButton.setText(R.string.view);
    } else {
      passwordText.setText(R.string.hidden_password);
      passwordText.setTransformationMethod(new PasswordTransformationMethod());
      viewPasswordButton.setText(R.string.hide);
    }
  }

  public void tryViewPassword() {
    if (criticalData == null) {
      action = PasswordAction.ACTION_VIEW;
      getSecretCriticalData();
    } else {
      toggleViewPassword();
    }
  }

  public void onViewPassword(View v) {
    tryViewPassword();
  }

  public void copyPassword() {
    copyText("password", criticalData);
    Toast.makeText(this, "Password copied", Toast.LENGTH_SHORT).show();
  }

  public void tryCopyPassword() {
    if (criticalData == null) {
      action = PasswordAction.ACTION_COPY;
      getSecretCriticalData();
    } else {
      copyPassword();
    }
  }

  public void onCopyPassword(View v) {
    tryCopyPassword();
  }

  @Override
  public void onListSecrets(List<SecretIdentifier> secrets) {
  }

  @Override
  public void onListSecretsFailed(Exception e) {
  }

  @Override
  public void onGetSecretCriticalData(String criticalData) {
    spinner.setVisibility(View.INVISIBLE);
    this.criticalData = criticalData;

    if (action == PasswordAction.ACTION_VIEW) {
      toggleViewPassword();
    } else if (action == PasswordAction.ACTION_COPY) {
      copyPassword();
    } else {
      copyAndGo();
    }
  }

  @Override
  public void onGetSecretCriticalDataFailed(Exception e) {
    spinner.setVisibility(View.INVISIBLE);
    Toast.makeText(SecretViewActivity.this,
        "Error getting secret data", Toast.LENGTH_SHORT).show();
  }

  public void copyAndGo() {
    SecretIdentifier secret = getSecret();
    if (secret == null || secret.secretData.loginUrl == null) {
      Toast.makeText(this, "Url is not available for this secret", Toast.LENGTH_SHORT).show();
      return;
    }

    copyPassword();
    Uri uri = Uri.parse(secret.secretData.loginUrl);
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);

    try {
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, "Could not open URL", Toast.LENGTH_SHORT).show();
      return;
    }

    /*
    // The following code handles the notification copy
    Intent notificationIntent = new Intent(this, NotificationCopyActivity.class);
    notificationIntent.setAction("android.intent.action.MAIN");
    notificationIntent.addCategory("android.intent.category.LAUNCHER");
    PendingIntent pIntent = PendingIntent.getActivity(this, 0, notificationIntent,
        PendingIntent.FLAG_UPDATE_CURRENT | Notification.FLAG_AUTO_CANCEL);
    String title = titleText.getText().toString();
    Notification noti = new Notification.Builder(this).setContentTitle(title)
        .setContentText("Password Available for Copy!")
        .setSmallIcon(R.drawable.mitro_logo_small).setContentIntent(pIntent)
        .addAction(0, "Copy Password", pIntent).build();

    final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    // Hide the notification after its selected

    noti.flags = Notification.FLAG_AUTO_CANCEL;

    notificationManager.notify(4123, noti);

    BroadcastReceiver call_method = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action_name = intent.getAction();
        if (action_name.equals("copy")) {
          copyPassword();
          // TODO: What is this magic number, 4123?
          notificationManager.cancel(4123);
          PackageManager pm = getPackageManager();
          Intent queryIntent = new Intent(Intent.ACTION_VIEW,
              Uri.parse("http://www.google.com"));
          ActivityInfo af = queryIntent.resolveActivityInfo(pm, 0);
          Intent launchIntent = new Intent(Intent.ACTION_MAIN);
          launchIntent.setClassName(af.packageName, af.name);
          startActivity(launchIntent);
          finish();
        }
      }
    };

    registerReceiver(call_method, new IntentFilter("copy"));*/
  }

  public void tryCopyAndGo() {
    if (criticalData == null) {
      action = PasswordAction.ACTION_COPY_AND_GO;
      getSecretCriticalData();
    } else {
      copyAndGo();
    }
  }

  public void onCopyAndGo(View v) {
    tryCopyAndGo();
  }
}
