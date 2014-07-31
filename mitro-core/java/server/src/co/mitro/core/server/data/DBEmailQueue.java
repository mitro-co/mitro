/*******************************************************************************
 * Copyright (c) 2013, 2014 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You can contact the authors at inbound@mitro.co.
 *******************************************************************************/
package co.mitro.core.server.data;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import co.mitro.core.servlets.Util;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName="email_queue")
public class DBEmailQueue {
  // Gson is thread-safe.
  private static final Gson gson = new Gson();

  private static final Set<String> EXPERIMENTAL_EMAIL_SUFFIXES = ImmutableSet.of("@mitro.co", "@lectorius.com", "vijayp@gmail.com");

  private static final String VERIFY_DEVICE_PATH = "/mitro-core/user/VerifyDevice";
  private static final String VERIFY_ACCOUNT_PATH = "/mitro-core/user/VerifyAccount";
  private static final String INVITE_USER_PATH = "/install.html";

  @DatabaseField(generatedId=true, canBeNull=false)
  private int id;

  @DatabaseField(columnName="type_string", canBeNull=false)
  private String typeString;

  @DatabaseField(columnName="arg_string", canBeNull=true, dataType=DataType.LONG_STRING)
  private String argString;
  
  @DatabaseField(columnName="mandrill_template_name", canBeNull=true, dataType=DataType.LONG_STRING)
  public String mandrillTemplateName;
  
  @DatabaseField(columnName="mandrill_template_param_map_json", canBeNull=true, dataType=DataType.LONG_STRING)
  public String mandrillTemplateParamMapJson;

  // This uses SQL TIMESTAMP which has no time zone information
  // TODO: change this to dataType=DataType.DATE_LONG to avoid timezone troubles
  @DatabaseField(columnName="attempted_time")
  private Date attemptedTime;

  public int getId() {
    return id;
  }

  public Type getType() {
    Type t = Type.fromString(typeString);
    assert t != null : "Unsupported typeString: " + typeString;
    return t;
  }

  public String[] getArguments() {
    return gson.fromJson(argString, String[].class);
  }

  public  void setArguments(String... args) {
    // TODO: Use a builder to enforce this? this should be immutable?
    assert argString == null;
    argString = gson.toJson(args);
  }

  public Date getAttemptedTime() {
    if (attemptedTime != null) {
      // ORMLite uses the default java.util.Date parsing, which assumes local time zone
      // Our database uses UTC, so we need to convert
      // TODO: Use a custom persister to do this on save/load instead of this gross hack?
      Calendar calendar = Calendar.getInstance();
      // set the calendar to the local time parsed out of the database
      calendar.setTime(attemptedTime);

      // adjust the local time back to the UTC equivalent
      int localUtcOffset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
      calendar.add(Calendar.MILLISECOND, localUtcOffset);
      return calendar.getTime();
    } else {
      return null;
    }
  }

  public static boolean allowsExperimentalEmails(String email) {
    for (String suffix : EXPERIMENTAL_EMAIL_SUFFIXES) {
      if (email.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  public static DBEmailQueue makeMandrillEmail(String recipientEmail, Map<String, String> emailParams, Type type) {
    DBEmailQueue eq = new DBEmailQueue();
    eq.setType(type.getValue());
    // args: [subject, sender_name, sender_email, recipient_name, recipient_email]. 
    // Subject, sender_name, and sender_email, should be set to the empty string or null for now, 
    // since they can be set in web mandrill editor
    eq.setArguments(null, null, null, null, recipientEmail);

    Map<String, String> outputParams = new HashMap<String, String>(emailParams);
    // TODO: make this a name instead of an email.
    outputParams.put("firstname", recipientEmail);
    outputParams.put("TO", recipientEmail);
    eq.mandrillTemplateName = type.getValue();
    eq.mandrillTemplateParamMapJson = gson.toJson(outputParams);

    return eq;
  }

  public static DBEmailQueue makeInvitation(
      String senderAddress, String recipientAddress, String generatedPassword) {
    if (allowsExperimentalEmails(recipientAddress)) {
      Map<String, String> hashParams = new HashMap<String, String>();
      hashParams.put("u", recipientAddress);
      hashParams.put("p",  generatedPassword);

      String invitationUrl = Util.buildUrl("https://www.mitro.co",
                INVITE_USER_PATH + "#" + Util.urlEncode(hashParams), null);

      Map<String, String> emailParams = new HashMap<String, String>();
      emailParams.put("invitation_url",  invitationUrl);
      emailParams.put("sender",  senderAddress);
      emailParams.put("password",  generatedPassword);
      return makeMandrillEmail(recipientAddress, emailParams, Type.MANDRILL_INVITE);
    } else {
      DBEmailQueue email = new DBEmailQueue();
      email.typeString = Type.INVITE.getValue();
      email.setArguments(senderAddress, recipientAddress, generatedPassword);
      return email;
    }
  }
  
  public static DBEmailQueue makeNewDeviceVerification(String recipientAddress, String token, String tokenSignature, String platform, String serverUrl) {
    if (allowsExperimentalEmails(recipientAddress)) {
      Map<String, String> queryParams = new HashMap<String, String>();
      queryParams.put("user", recipientAddress);
      queryParams.put("token",  token);
      queryParams.put("token_signature",  tokenSignature);
      String verificationUrl = Util.buildUrl(serverUrl, VERIFY_DEVICE_PATH, queryParams);

      Map<String, String> emailParams = new HashMap<String, String>();
      emailParams.put("verification_url",  verificationUrl);
      emailParams.put("platform", platform);
      return makeMandrillEmail(recipientAddress, emailParams, Type.MANDRILL_LOGIN_ON_NEW_DEVICE);
    } else {
      DBEmailQueue email = new DBEmailQueue();
      email.typeString = Type.LOGIN_ON_NEW_DEVICE.getValue();
      email.setArguments(recipientAddress, token, tokenSignature);
      return email;
    }
  }

  public static DBEmailQueue makeNewIssue(String userEmailAddress, 
      String url, String type, String description, int issueId) {
    if (null == userEmailAddress) {
      userEmailAddress = "unknown@example.com";
    }
    DBEmailQueue email = new DBEmailQueue();
    email.typeString = Type.ISSUE_REPORTED.getValue();
    email.setArguments(userEmailAddress, url, type, description, Integer.toString(issueId));
    return email;
  }

  /** Creates a validation message to recipientAddress, with code. */
  public static DBEmailQueue makeAddressVerification(String recipientAddress, String code, String serverUrl) {
    if (allowsExperimentalEmails(recipientAddress)) {
      Map<String, String> queryParams = new HashMap<String, String>();
      queryParams.put("user", recipientAddress);
      queryParams.put("code", code);
      String verificationUrl = Util.buildUrl(serverUrl, VERIFY_ACCOUNT_PATH, queryParams);

      Map<String, String> emailParams = new HashMap<String, String>();
      emailParams.put("verification_url",  verificationUrl);
      return makeMandrillEmail(recipientAddress, emailParams, Type.MANDRILL_ADDRESS_VERIFICATION);
    } else {
      DBEmailQueue email = new DBEmailQueue();
      email.typeString = Type.ADDRESS_VERIFICATION.getValue();
      email.setArguments(recipientAddress, code);
      return email;
    }
  }

  /**
   * Returns a DateFormat that always outputs UTC in ISO 8601 format.
   * DateFormat objects are not thread safe.
   */
  public static DateFormat getUtcIsoFormat() {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    f.setTimeZone(TimeZone.getTimeZone("UTC"));
    return f;
  }

  public enum Type {
    INVITE("new_user_invitation"),
    ADDRESS_VERIFICATION("address_verification"),
    LOGIN_ON_NEW_DEVICE("new_device_login"), 
    ISSUE_REPORTED("issue_reported"),
    // Values are the mandrill template names
    MANDRILL_LOGIN_ON_NEW_DEVICE("product-verify"),
    MANDRILL_ADDRESS_VERIFICATION("onboard-verify"),
    MANDRILL_INVITE("share-to-recipient-web-new-user"),
    MANDRILL_SAVE_FIRST_SECRET("onboard-first-secret"),
    MANDRILL_SHARE_SECRET("share-to-recipient-web");

    
    private final String value;
    private Type(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    /** Returns the Type with value == typeString, or null if it doesn't exist. */
    public static Type fromString(String typeString) {
      for (Type t : Type.values()) {
        if (typeString.equals(t.value)) {
          return t;
        }
      }

      return null;
    }
  }

  public void setType(String typeString) {
    this.typeString = typeString;
  }
}
