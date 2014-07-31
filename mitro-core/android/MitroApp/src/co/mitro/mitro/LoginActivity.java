package co.mitro.mitro;

import java.io.IOException;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;

public class LoginActivity extends MitroActivity {
  // View elements
  private EditText usernameText;
  private EditText passwordText;
  private EditText twoFactorAuthCodeText;
  private TextView error;
  private ProgressBar logInSpinner;
  private CheckBox keepLoggedIn;
  private Button signInButton;

  private Handler handler = new Handler();
  private LoginTask loginTask = null;

  // Log in when enter pressed in password or 2FA field.
  private OnEditorActionListener enterListener = new OnEditorActionListener() {
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
          || (actionId == EditorInfo.IME_ACTION_DONE)) {
        login();
      }
      return false;
    }
  };

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getSupportActionBar().hide();
    setContentView(R.layout.login_activity);

    usernameText = (EditText) findViewById(R.id.username);
    passwordText = (EditText) findViewById(R.id.password);
    twoFactorAuthCodeText = (EditText) findViewById(R.id.two_factor_auth_code);

    logInSpinner = (ProgressBar) findViewById(R.id.login_progress_bar);
    keepLoggedIn = (CheckBox) findViewById(R.id.checkBox1);
    signInButton = (Button) findViewById(R.id.signin);
    error = (TextView) findViewById(R.id.Error);

    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    String savedUsername = prefs.getString("savedUsername", "");

    if (!TextUtils.isEmpty(savedUsername)) {
      usernameText.setText(savedUsername);
      passwordText.requestFocus();
    }
    // TODO: investigate why private key encryption is broken on older android versions
    if (android.os.Build.VERSION.SDK_INT < 11) {
      keepLoggedIn.setVisibility(View.INVISIBLE);
    }

    passwordText.setOnEditorActionListener(enterListener);
    twoFactorAuthCodeText.setOnEditorActionListener(enterListener);

    if (getApp().getSharedPreference("privateKey") != null
        && !TextUtils.isEmpty(savedUsername)) {
      usernameText.setVisibility(View.INVISIBLE);
      passwordText.setVisibility(View.INVISIBLE);
      keepLoggedIn.setVisibility(View.INVISIBLE);
      signInButton.setVisibility(View.INVISIBLE);

      login();
    }
  }

  public void onClickSignIn(View unused) {
    login();
  }

  public void onClickSignUp(View unused) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage("We are still working on signup support in the app.  " +
        "For now, sign up at mitro.co using the desktop version of Chrome.");
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        dialog.cancel();
      }
    });
    builder.show();
  }

  // Starts the login and checks if user exists on sign in button click
  public void login() {
    if (loginTask != null) {
      return;
    }

    error.setText("");
    getApp().setSavePrivateKey(keepLoggedIn.isChecked());

    // Hides the keyboard after sign is pressed so that the error message is
    // visible
    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (getCurrentFocus() != null) {
      inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
          InputMethodManager.HIDE_NOT_ALWAYS);
    }
    loginTask = new LoginTask();
    loginTask.execute();
    logInSpinner.setVisibility(View.VISIBLE);
  }

  public void onLogin() {
    logInSpinner.setVisibility(View.INVISIBLE);

    Intent intent = new Intent(this, SecretListActivity.class);
    intent.putExtra("username", usernameText.getText().toString());

    SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    editor.putString("savedUsername", usernameText.getText().toString());
    editor.commit();

    getApp().setIsLoggedIn(true);
    startActivity(intent);
    finish();
  }

  public void onLoginFailed() {
    logInSpinner.setVisibility(View.INVISIBLE);

    error.setText(getApp().getLoginErrorMessage());
    if (getApp().getLoginErrorMessage().equals(
        "You must enter your two-factor authentication code.")) {
      usernameText.setVisibility(View.INVISIBLE);
      keepLoggedIn.setVisibility(View.INVISIBLE);
      passwordText.setVisibility(View.GONE);
      twoFactorAuthCodeText.setVisibility(View.VISIBLE);
    } else {
      usernameText.setVisibility(View.VISIBLE);
      passwordText.setVisibility(View.VISIBLE);
      keepLoggedIn.setVisibility(View.VISIBLE);
      signInButton.setVisibility(View.VISIBLE);
      twoFactorAuthCodeText.setVisibility(View.GONE);
      twoFactorAuthCodeText.setText("");
    }

    getApp().setLoginErrorMessage("Incorrect login info or bad network connection.");
  }

  class LoginTask extends AsyncTask<Void, Void, Boolean> {
    private String username;
    private String password;
    private String twoFactorAuthCode ;

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      username = usernameText.getText().toString();
      password = passwordText.getText().toString();
      twoFactorAuthCode = twoFactorAuthCodeText.getText().toString();
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
      try {
        MitroApi apiClient = getApp().getApiClient();
        return apiClient.login(username, password, !TextUtils.isEmpty(twoFactorAuthCode), twoFactorAuthCode);
      } catch (IOException e) {
        return false;
      } catch (CryptoError e) {
        return false;
      }
    }

    @Override
    protected void onPostExecute(final Boolean result) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          if (result) {
            onLogin();
          } else {
            onLoginFailed();
          }
          loginTask = null;
        }
      });
    }
  }
}
