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
package co.mitro.access.servlets;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBMitroAccessEmail;
import co.mitro.core.servlets.MitroWebServlet;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.SelectArg;

/**
 * Base class for servlets sending emails for Mitro Access
 */
public abstract class MitroAccessEmailer extends MitroWebServlet {
  
  private static final Logger logger = LoggerFactory.getLogger(MitroAccessEmailer.class);
  private static final long serialVersionUID = 1L;
  private final ManagerFactory managerFactory;
  
  // These are the ids used for the form fields
  protected static final String FORM_RECIPIENT_EMAIL_ID = "recipient-email";
  protected static final String FORM_USER_EMAIL_ID = "user-email";
  protected static final String FORM_NOTE_ID = "note";
  protected static final String FORM_URL_ID = "url";
  protected static final String FORM_ACCOUNT_ID = "account";
    
  public MitroAccessEmailer(ManagerFactory managerFactory, KeyFactory keyFactory) {
    this.managerFactory = Preconditions.checkNotNull(managerFactory);
  }
  
  abstract protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception;

  protected void processMitroAccessEmail(String recipientEmail,  Set<String> emailsToSave, Map<String, String> emailParams, String mandrillTemplate) throws SQLException {
   try (Manager mgr = managerFactory.newManager()) { 
     // Send email to recipient
     sendMitroAccessEmail(mgr, recipientEmail, emailParams, mandrillTemplate);
     
     // For each email to save -> save the email to the DB
     for (String email : emailsToSave) {
       saveMitroAccessEmail(mgr, email);
     }
     
     mgr.commitTransaction();
   }
 }
 
 protected void sendMitroAccessEmail(Manager mgr, String recipientEmail, Map<String, String> emailParams, String mandrillTemplate) throws SQLException {
   Preconditions.checkArgument(!Strings.isNullOrEmpty(mandrillTemplate));
   Preconditions.checkArgument(!Strings.isNullOrEmpty(recipientEmail));
   
   DBEmailQueue emailQueue = new DBEmailQueue();
   emailQueue.setType(this.getClass().getSimpleName());
   // from adam:
   // args: [subject, sender_name, sender_email, recipient_name, recipient_email]. 
   // Subject, sender_name, and sender_email, should be set to the empty string or null for now, 
   // since they can be set in web mandrill editor
   emailQueue.setArguments(null, null, null, null, recipientEmail);
   
   emailQueue.mandrillTemplateName = mandrillTemplate;
   emailQueue.mandrillTemplateParamMapJson = gson.toJson(emailParams);
   
   mgr.emailDao.create(emailQueue);
 }
 
 protected void saveMitroAccessEmail(Manager mgr, String email) throws SQLException {
   Preconditions.checkArgument(!Strings.isNullOrEmpty(email));
   PreparedQuery<DBMitroAccessEmail> query = mgr.mitroAccessEmailDao.queryBuilder().setCountOf(true)
       .where().eq(DBMitroAccessEmail.EMAIL_FIELD_NAME, new SelectArg(email)).prepare();
   if (0 == mgr.mitroAccessEmailDao.countOf(query)) {
     DBMitroAccessEmail mitroAccessEmail = new DBMitroAccessEmail();
     mitroAccessEmail.setEmail(email);
     mgr.mitroAccessEmailDao.create(mitroAccessEmail);
   }
 }
 
}
