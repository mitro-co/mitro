package co.mitro.mitro;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

// TODO: this should be a manager for all secrets instead of being a kludge to 
// communicate between activities.
// TODO: inheritance implies an "is a" relationship which is not true here.
public class MitroApplication extends Application {
  private MitroApi apiClient = null;
  private SecretManager secretManager = null;

  boolean savePrivateKey;
  boolean isUserLoggedIn;
  String loginErrorMessage = "Incorrect login info or bad network connection.";
  SharedPreferences.Editor editor;
  public static SharedPreferences settings;

  @Override
  public void onCreate() {
    super.onCreate();
    PRNGFixes.apply();
    settings = PreferenceManager.getDefaultSharedPreferences(this);
    apiClient = new MitroApi(new POSTRequestToURL(), this);
    secretManager = new SecretManager(apiClient);
  }

  public MitroApi getApiClient() {
    return apiClient;
  }

  public SecretManager getSecretManager() {
    return secretManager;
  }

  public void setSavePrivateKey(boolean state) {
    savePrivateKey = state;
  }

  public boolean shouldSavePrivateKey() {
    return savePrivateKey;
  }

  public boolean isLoggedIn() {
    return isUserLoggedIn;
  }

  public void setIsLoggedIn(boolean loginStatus) {
    isUserLoggedIn = loginStatus;
  }

  public void setLoginErrorMessage(String error) {
    loginErrorMessage = error;
  }

  public String getLoginErrorMessage() {
    return loginErrorMessage;
  }

  public void putSharedPreferences(String key, String value) {
    editor = settings.edit();
    editor.putString(key, value);
    editor.commit();
  }

  public void putSharedPreferences(String key, int value) {
    editor = settings.edit();
    editor.putInt(key, value);
    editor.commit();
  }

  public String getSharedPreference(String key) {
    String returnString = settings.getString(key, null);
    return returnString;
  }
}
