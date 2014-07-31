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
package co.mitro.core.servlets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBEmailRecord;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@WebServlet("/api/RecordEmail")
public class RecordEmail extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(AddIssue.class);
  private static final long serialVersionUID = 1L;
  static final String SUCCESS_PATH = "/openaccess/success.html";
  static final String ERROR_PATH = "/openaccess/";

  private final ManagerFactory managerFactory;

  // Signature matches the one used in Main to create servlets via reflection
  public RecordEmail(ManagerFactory managerFactory, KeyFactory keyFactory) {
    this.managerFactory = Preconditions.checkNotNull(managerFactory);
  }

  public String makeErrorUrl(String errorMessage) throws UnsupportedEncodingException {
    return ERROR_PATH + "?error=" + URLEncoder.encode(errorMessage, "UTF-8");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      String email = request.getParameter("email");
      if (Strings.isNullOrEmpty(email) || !Util.isEmailAddress(email)) {
        logger.error("missing or invalid email: {}", email);
        response.sendRedirect(makeErrorUrl("Missing or invalid email"));
        return;
      }
      String fullName = request.getParameter("fullName");

      DBEmailRecord emailRecord = new DBEmailRecord();
      emailRecord.setEmail(email);
      emailRecord.setFullName(fullName);
      String referrer = request.getParameter("referrer");
      if (null == referrer) {
        referrer = request.getHeader("referer");
      }
      emailRecord.setReferrerUrl(referrer);

      try (Manager mgr = managerFactory.newManager()) {
        mgr.emailRecordDao.create(emailRecord);
        mgr.commitTransaction();
      }

      response.sendRedirect(SUCCESS_PATH);
    } catch (Exception e) {
      logger.error("Exception; returning error:", e);
      response.sendRedirect(makeErrorUrl("Unknown error"));
    }
  }
}
