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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.servlets.Util;

/**
 * Class to process Share Access Link form on Mitro Access
 */
@WebServlet("/ShareAccessLink")
public class ShareAccessEmailer extends MitroAccessEmailer {
  private static final Logger logger = LoggerFactory.getLogger(ShareAccessEmailer.class);
  private static final long serialVersionUID = 1L; 
  private static final String MANDRILL_TEMPLATE = "mitro-share-access-link";

  public ShareAccessEmailer(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }
  
  @Override
  protected void handleRequest(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    Util.allowCrossOriginRequests(response);
    response.setContentType(Util.JSON_CONTENT_TYPE);
    String recipientEmail = request.getParameter(FORM_RECIPIENT_EMAIL_ID);
    if (Strings.isNullOrEmpty(recipientEmail) || !Util.isEmailAddress(recipientEmail)) {
      logger.error("missing or invalid email: {}", recipientEmail);    
      response.getWriter().write("{\"errorMessage\":\"Please enter valid email addresses\"}");
      return;
    }
    
    String userEmail = request.getParameter(FORM_USER_EMAIL_ID);
    
    Map<String, String> emailParams = new HashMap<String, String>();
    emailParams.put("user",  userEmail);
    emailParams.put("url", request.getParameter(FORM_URL_ID));
    emailParams.put("note", request.getParameter(FORM_NOTE_ID));
    emailParams.put("account", request.getParameter(FORM_ACCOUNT_ID));
    
    Set<String> emailsToSave = new HashSet<String>();
    emailsToSave.add(recipientEmail);
    // TODO: should user email be a requirement to share access?  If so, should move check earlier.
    if (!Strings.isNullOrEmpty(userEmail) && Util.isEmailAddress(userEmail)) {
      emailsToSave.add(userEmail);
    }
    
    processMitroAccessEmail(recipientEmail, emailsToSave, emailParams, MANDRILL_TEMPLATE);
    
    response.getWriter().write("{\"successMessage\":\"Message successfully sent\"}");
  }
}
