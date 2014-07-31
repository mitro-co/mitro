package co.mitro.mitro;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import org.keyczar.Crypter;
import org.keyczar.exceptions.KeyczarException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import co.mitro.api.RPC;
import co.mitro.api.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.api.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyczarKeyFactory;
import co.mitro.keyczar.KeyczarJsonReader;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class MitroApi {
  private Map<String, PrivateKeyInterface> keyCache = Maps.newHashMap();

  // Username for calls;
  private String username;

  // Crypto Stuff
  private static final KeyFactory keyFactory = new KeyczarKeyFactory();
  private PrivateKeyInterface userPrivateKey;
  private PrivateKeyInterface privateGroupKey;

  POSTRequestSender sender;

  // Shared Preference and globalData
  private static MitroApplication globalData;
  SharedPreferences.Editor editor;
  SharedPreferences prefs;

  private static final String API_BASE_PATH = "https://www.mitro.co/mitro-core/api/";
  //private static final String API_BASE_PATH = "https://10.0.2.2:8443/mitro-core/api/";
  private static String clientId = "Android UNKNOWN VERSION";
  private int userGroupId;
  private String listSecretsJson;

  public static String getLoginTokenKey(String username) {
    return "loginToken:" + username;
  }

  public static String getLoginTokenSignatureKey(String username) {
    return "loginTokenSignature:" + username;
  }

  private static final Gson gson = new Gson();

  public MitroApi(POSTRequestSender sender) {
    this.sender = sender;
    globalData = new MitroApplication();
  }

  // Constructor for when you want to have a client identifier with the
  // current version
  public MitroApi(POSTRequestSender sender, Context context) {
    this.sender = sender;
    globalData = (MitroApplication) context.getApplicationContext();

    try {
      PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      String version = pInfo.versionName;
      clientId = "Androidv" + version;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
  }

  public PrivateKeyInterface getDecryptionKeyForSecret(SecretToPath secret, Map<Integer, GroupInfo> groups) throws CryptoError {
    PrivateKeyInterface decryptionKey = userPrivateKey;

    for (Integer groupId : secret.groupIdPath) {
      String unencryptedGroupKey = groups.get(groupId).encryptedPrivateKey;
      PrivateKeyInterface nextDecryptionKey = keyCache.get(unencryptedGroupKey);
      if (nextDecryptionKey == null) {
        nextDecryptionKey = keyFactory.loadPrivateKey(decryptionKey.decrypt(unencryptedGroupKey));
        keyCache.put(unencryptedGroupKey, nextDecryptionKey);
      }
      decryptionKey = nextDecryptionKey;
    }
    return decryptionKey;
  }

  /**
   * Gets an ArrayList of the identifier data for the secret
   * 
   * @param username
   *          The username of who's secret identifier data is being retrieved.
   * @param password
   *          The password of the user who's secret identifier data is being
   *          retrieved.
   * @return An ArrayList<SecretIdentifer> of all the secrets and some
   *         identifier data.
   * @throws IOException
   * @throws CryptoError
   */
  public ArrayList<SecretIdentifier> getSecretIdentiferData() throws IOException, CryptoError {
    ArrayList<SecretIdentifier> secretIdentifierData = new ArrayList<SecretIdentifier>();

    listSecretsJson = getListOfSecretsJsonString(username);
    RPC.ListMySecretsAndGroupKeysResponse secrets_json = gson.fromJson(listSecretsJson,
        RPC.ListMySecretsAndGroupKeysResponse.class);

    // Gets the groupid for the username so that secrets can be added
    for (GroupInfo groupInfo : secrets_json.groups.values()) {
      if (groupInfo.name.equals("")) {
        userGroupId = groupInfo.groupId;
        privateGroupKey = keyFactory.loadPrivateKey(userPrivateKey.decrypt(groupInfo.encryptedPrivateKey));
      }
    }

    for (SecretToPath secretToPath : secrets_json.secretToPath.values()) {
      // Sets the decryption key used to decrypt the current secret
      PrivateKeyInterface decryptionKey = getDecryptionKeyForSecret(secretToPath, secrets_json.groups);

      SecretIdentifier secret = new SecretIdentifier();
      secretIdentifierData.add(secret);

      String clientDataString = decryptionKey.decrypt(secretToPath.encryptedClientData);
      secret.secretData = gson.fromJson(clientDataString, ClientDataStruct.class);
      secret.id = secretToPath.secretId;
      secret.groupid = secretToPath.groupIdPath.get(secretToPath.groupIdPath.size() - 1);

      if (!Strings.isNullOrEmpty(secret.secretData.title)) {
        secret.title = secret.secretData.title;
      } else if (!Strings.isNullOrEmpty(secretToPath.title)) {
        secret.title = secretToPath.title;
      } else {
        secret.title = secret.getDomain();
      }

      if (secret.secretData.type == null) {
        secret.secretData.type = "auto";
      }
    }
    return secretIdentifierData;
  }

  protected String makeRequestString(RPC.MitroRPC request) throws CryptoError {
    request.deviceId = globalData.getSharedPreference("deviceId");
    RPC.SignedRequest rval = new RPC.SignedRequest();
    rval.clientIdentifier = clientId;
    rval.identity = this.username;
    rval.request = gson.toJson(request);
    rval.signature = userPrivateKey.sign(rval.request);
    return gson.toJson(rval);
  }

  /**
   * Gets a string representation of the critical data whether it be a password
   * or a note
   * 
   * @param secretId
   *          An integer representation of the secret id.
   * @param groupId
   *          An integer representation of the group id of the secret.
   * @param type
   *          A string representation of the secret type;
   * @return A string representation of the critical data whether it be a
   *         password or a note
   * @throws IOException
   * @throws CryptoError
   */
  public String getCriticalDataContents(int secretId, int groupId, String type) throws IOException,
      CryptoError {
    // Gets a RPC.ListMySecretsAndGroupKeysResponse of all the secrets
    RPC.ListMySecretsAndGroupKeysResponse secrets_json = gson.fromJson(listSecretsJson,
        RPC.ListMySecretsAndGroupKeysResponse.class);

    // Gets a RPC.GetSecretResponse object contining the encrypted critical
    // data.
    RPC.GetSecretResponse encrypted_secret = gson.fromJson(
        getCriticalData(secretId, groupId, true, userPrivateKey), RPC.GetSecretResponse.class);
    // Create a new PrivateKeyInterface of the decrypted private key given a
    // specific group
    String encrypted_private_key = secrets_json.groups.get(groupId).encryptedPrivateKey;
    PrivateKeyInterface decrypter = keyFactory.loadPrivateKey(userPrivateKey.decrypt(encrypted_private_key));
    // Uses the decrypter to decrypt the encrypted Critical Data from the
    // RPC.GetSecretResponse object.
    CriticalDataStruct criticaldata = gson.fromJson(
        decrypter.decrypt(encrypted_secret.secret.encryptedCriticalData), CriticalDataStruct.class);

    if (criticaldata.password == null)
      return criticaldata.note;
    else if (criticaldata.note == null)
      return criticaldata.password;
    else {
      Log.w("Critical Data Warning", "No critical data available");
      return "N/A";
    }
  }

  /**
   * Gets the crypto key for a specific user(username field should remain for
   * other pre-login methods)
   * 
   * @param username
   *          The username of which cryptokey you are getting
   * @param twoFactorAuthCode
   * @return A string representation of the crypto key
   * @throws IOException
   */
  public String getCryptoKey(String username, String twoFactorAuthCode) throws IOException {

    RPC.SignedRequest user = new RPC.SignedRequest();
    user.clientIdentifier = clientId;
    user.identity = username;

    RPC.GetMyPrivateKeyRequest req = new RPC.GetMyPrivateKeyRequest();
    req.userId = user.identity;
    req.deviceId = globalData.getSharedPreference("deviceId");
    req.twoFactorCode = twoFactorAuthCode;

    // For compatibility with legacy code: the login token key previously did not include the username.
    // Load the old preferences and then wipe them.
    // Since we don't know which user the old login token belongs to, we don't write out the new keys here.
    if (globalData.getSharedPreference("loginToken") != null) {
      req.loginToken = globalData.getSharedPreference("loginToken");
      req.loginTokenSignature = globalData.getSharedPreference("loginTokenSignature");
      globalData.putSharedPreferences("loginToken", null);
      globalData.putSharedPreferences("loginTokenSignature", null);
    } else {
      req.loginToken = globalData.getSharedPreference(getLoginTokenKey(username));
      req.loginTokenSignature = globalData.getSharedPreference(getLoginTokenSignatureKey(username));
    }

    user.request = gson.toJson(req);

    return sender.getResponse(API_BASE_PATH + "GetMyPrivateKey", gson.toJson(user));
  }

  /**
   * Decrypts the cryptokey
   * 
   * @param key
   *          A string representation of the cryptokey
   * @param password
   *          A string representation of the password
   * @return A PrivateKeyInterface that
   * @throws CryptoError
   */
  public PrivateKeyInterface decryptCryptoKey(String key, String password) throws CryptoError {
    return keyFactory.loadEncryptedPrivateKey(key, password);
  }

  /**
   * Gets a Json String representation of all the secrets
   * 
   * @param username
   *          A string that is the username of the user
   * @param signature
   *          A string signature for the user(can be generated using
   *          getSignature)
   * @return A String representation of all the secrets in JSON format
   * @throws CryptoError
   */
  public String getListOfSecretsJsonString(String username) throws IOException, CryptoError {
    RPC.ListMySecretsAndGroupKeysRequest req = new RPC.ListMySecretsAndGroupKeysRequest();

    return sender.getResponse(API_BASE_PATH + "ListMySecretsAndGroupKeys",
        this.makeRequestString(req));
  }

  /**
   * Returns the json response for a call to GeyMyDeviceKey
   * 
   * @param username
   * @return
   * @throws IOException
   * @throws CryptoError
   */
  public String getDeviceKeyString(String username) throws IOException, CryptoError {
    RPC.GetMyDeviceKeyRequest req = new RPC.GetMyDeviceKeyRequest();
    req.deviceId = globalData.getSharedPreference("deviceId");
    return sender.getResponse(API_BASE_PATH + "GetMyDeviceKey", this.makeRequestString(req));
  }

  /**
   * Gets the critical data JSON representation
   * 
   * @param secretid
   *          An integer representation of the secretid
   * @param groupid
   *          An integer representation of the groupid
   * @param includeCriticalData
   *          A boolean that indicates whether or not critical data should be
   *          sent back
   * @param decryptionKey
   *          A PrivateKeyInterface that is the decrypted cryptokey
   * @return A String of the response from the server in JSON format
   */
  public String getCriticalData(int secretid, int groupid, boolean includeCriticalData,
      PrivateKeyInterface decryptionKey) throws IOException, CryptoError {
    RPC.GetSecretRequest secret_request = new RPC.GetSecretRequest();
    secret_request.secretId = secretid;
    secret_request.groupId = groupid;
    secret_request.includeCriticalData = includeCriticalData;
    return sender.getResponse(API_BASE_PATH + "GetSecret", this.makeRequestString(secret_request));
  }

  /**
   * Gets the private key for the user, decrypts it and then gets a signature
   * for the user.
   * 
   * @param username
   *          The username of the user logging in
   * @param password
   *          The password of the user logging in
   * @throws IOException
   * @throws CryptoError
   */
  public boolean login(String username, String password, boolean twoFactorAuthNeeded,
      String twoFactorAuthCode) throws IOException, CryptoError {
    try {
      this.username = username;

      if (!username.equals(globalData.getSharedPreference("savedUsername"))) {
        globalData.putSharedPreferences("savedUsername", username);
        // privateKey is invalid for the new user.
        globalData.putSharedPreferences("privateKey", null);
      }

      if (globalData.getSharedPreference("deviceId") == null) {
        globalData.putSharedPreferences("deviceId",
            org.keyczar.util.Base64Coder.encodeWebSafe(org.keyczar.util.Util.rand(20)).toString());
      }

      String privateKeyResponseString;
      if (twoFactorAuthNeeded) {
        privateKeyResponseString = getCryptoKey(username, twoFactorAuthCode);
      } else {
        privateKeyResponseString = getCryptoKey(username, null);
      }

      RPC.GetMyPrivateKeyResponse privateKeyResponse = gson.fromJson(privateKeyResponseString,
          RPC.GetMyPrivateKeyResponse.class);

      RPC.MitroException privateKeyException = gson.fromJson(privateKeyResponseString,
          RPC.MitroException.class);

      if (privateKeyException.exceptionType == null) {

        if ((globalData.getSharedPreference("privateKey") != null)
            && (privateKeyResponse.deviceKeyString != null)) {
          String storedPrivateKey = globalData.getSharedPreference("privateKey");
          KeyczarJsonReader reader = new KeyczarJsonReader(privateKeyResponse.deviceKeyString);
          Crypter deviceKey = new Crypter(reader);
          userPrivateKey = keyFactory.loadPrivateKey(deviceKey.decrypt(storedPrivateKey));
        } else {
          userPrivateKey = keyFactory
              .loadEncryptedPrivateKey(privateKeyResponse.encryptedPrivateKey, password);

          RPC.GetMyDeviceKeyResponse deviceKeyResponse = gson.fromJson(
              getDeviceKeyString(username), RPC.GetMyDeviceKeyResponse.class);
          String deviceKeyString = deviceKeyResponse.deviceKeyString;

          userPrivateKey = decryptCryptoKey(privateKeyResponse.encryptedPrivateKey, password);

          if (globalData.shouldSavePrivateKey()) {
            KeyczarJsonReader reader = new KeyczarJsonReader(deviceKeyString);
            Crypter deviceKey = new Crypter(reader);
            globalData.putSharedPreferences("privateKey", deviceKey.encrypt(userPrivateKey.toString()));
          }
        }

        String loginToken = privateKeyResponse.unsignedLoginToken;
        String loginTokenSignature = userPrivateKey.sign(privateKeyResponse.unsignedLoginToken);
        globalData.putSharedPreferences(getLoginTokenKey(username), loginToken);
        globalData.putSharedPreferences(getLoginTokenSignatureKey(username), loginTokenSignature);

        return true;
      } else {
        String errorMessage;
        if (privateKeyException.exceptionType.equals("DoEmailVerificationException")) {
          errorMessage = "Go check your email to verify your Mitro account";
        } else if (privateKeyException.exceptionType.equals("DoTwoFactorAuthException")) {
          errorMessage = "You must enter your two-factor authentication code.";
        } else if (privateKeyException.exceptionType.equals("RateLimitedException")) {
          errorMessage = "Rate limit exceeded.  Wait 10 seconds and try again";
        } else {
          errorMessage = privateKeyException.exceptionType;
        }

        globalData.setLoginErrorMessage(errorMessage);

        // Wipe old login token on error.  It may be invalid.
        globalData.putSharedPreferences(getLoginTokenKey(username), null);
        globalData.putSharedPreferences(getLoginTokenSignatureKey(username), null);

        return false;
      }
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Adds a secret password.
   * 
   * @param title
   * @param loginUrl
   * @param username
   * @param password
   * @return
   * @throws CryptoError
   * @throws MalformedURLException
   * @throws IOException
   */
  public String addSecretPassword(String title, String loginUrl, String username, String password)
      throws CryptoError, MalformedURLException, IOException {
    ClientDataStruct newSecret = new ClientDataStruct();
    newSecret.title = title;
    newSecret.loginUrl = loginUrl;
    newSecret.username = username;
    newSecret.type = "manual";
    CriticalDataStruct newCriticalData = new CriticalDataStruct();
    newCriticalData.password = password;

    RPC.AddSecretRequest addSecretRequest = new RPC.AddSecretRequest();
    addSecretRequest.hostname = "none";
    addSecretRequest.ownerGroupId = userGroupId;
    addSecretRequest.encryptedClientData = privateGroupKey.encrypt(gson.toJson(newSecret));
    addSecretRequest.encryptedCriticalData = privateGroupKey.encrypt(gson.toJson(newCriticalData));

    return sender
        .getResponse(API_BASE_PATH + "AddSecret", this.makeRequestString(addSecretRequest));
  }

  /**
   * Adds a secret note.
   * 
   * @param title
   * @param noteData
   * @return
   * @throws CryptoError
   * @throws MalformedURLException
   * @throws IOException
   */
  public String addSecretNote(String title, String noteData) throws CryptoError,
      MalformedURLException, IOException {
    ClientDataStruct newSecret = new ClientDataStruct();
    newSecret.title = title;
    newSecret.type = "note";
    CriticalDataStruct newCriticalData = new CriticalDataStruct();
    newCriticalData.note = noteData;

    RPC.AddSecretRequest addSecretRequest = new RPC.AddSecretRequest();
    addSecretRequest.hostname = "none";
    addSecretRequest.ownerGroupId = userGroupId;
    addSecretRequest.encryptedClientData = privateGroupKey.encrypt(gson.toJson(newSecret));
    addSecretRequest.encryptedCriticalData = privateGroupKey.encrypt(gson.toJson(newCriticalData));

    return sender
        .getResponse(API_BASE_PATH + "AddSecret", this.makeRequestString(addSecretRequest));
  }


  public void loadPrivateKey(String privateKey, String username) throws CryptoError,
      JsonSyntaxException, IOException, KeyczarException {

    RPC.GetMyDeviceKeyResponse deviceKeyString = gson.fromJson(getDeviceKeyString(username),
        RPC.GetMyDeviceKeyResponse.class);
    KeyczarJsonReader reader = new KeyczarJsonReader(deviceKeyString.deviceKeyString);
    Crypter deviceKey = new Crypter(reader);
    privateKey = deviceKey.decrypt(privateKey);
    userPrivateKey = keyFactory.loadPrivateKey(gson.toJson(privateKey));
  }

  public void setUsername(String newUsername) {
    this.username = newUsername;
  }

  /**
   * Secret data that comes from clientData
   * Contains:title,type,loginUrl,username,usernameField,passwordField,note
   */
  public static final class ClientDataStruct {
    /** The type of data gotten */
    public String type;
    /** The loginurl the data would be used in */
    public String loginUrl;
    /** The username to be used at the loginurl */
    public String username;
    /** The field the username would be used in */
    public String usernameField;
    /** The password the username would be used in */
    public String passwordField;
    /** The title of the data */
    public String title;
    /** Password note field */
    public String comment;
  }

  /**
   * Secret Identifier used when getting the list of secrets.
   */
  public static class SecretIdentifier {
    /** The title of the secret. */
    public String title;
    /** The groupid of the secret */
    public int groupid;
    /** The id of the secret */
    public int id;
    /** The ClientDataStruct of the secret */
    public String icon;
    public ClientDataStruct secretData;

    public String displayTitle;

    public String getType() {
      return secretData.type == null ? "auto" : secretData.type;
    }

    public String getDomain() {
      URL loginUrl;
      try {
        loginUrl = new URL(secretData.loginUrl);
      } catch (MalformedURLException e) {
        return "";
      }
      String domain = loginUrl.getHost();
      if (domain.startsWith("www.")) {
        return domain.substring(4);
      } else {
        return domain;
      }
    }
  }

  // JSON data format as defined by mitro_fe.js:addSite()
  // Eclipse doesn't realize Gson.toJson() reads the fields

  /**
   * Contains the critical data. Use note for notes and password for passwords
   */
  public static final class CriticalDataStruct {
    /** The decrypted password' */
    public String password;
    /** The decrypted note */
    public String note;
  }

  public void logout() {
    if (this.username != null) {
      globalData.putSharedPreferences(getLoginTokenKey(this.username), null);
      globalData.putSharedPreferences(getLoginTokenSignatureKey(this.username), null);
    }
    globalData.putSharedPreferences("privateKey", null);
    globalData.setIsLoggedIn(false);

    this.listSecretsJson = null;
    this.userPrivateKey = null;
    this.privateGroupKey = null;
    this.username = null;
    this.userGroupId = -1;
  }
}
