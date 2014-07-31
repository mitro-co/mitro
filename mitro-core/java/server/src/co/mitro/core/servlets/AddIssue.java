/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBIssue;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPCLogger;

import com.google.gson.Gson;

@WebServlet("/api/AddIssue")
public class AddIssue extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(AddIssue.class);
  private static final long serialVersionUID = -8241025232603896888L;

  // Gson is thread-safe.
  private static final Gson gson = new Gson();

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    RPC.LogMetadata logMetadata = new RPC.LogMetadata(request);

    // Parse the JSON message
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        request.getInputStream(), "UTF-8"));
    RPC.SignedRequest rpc = gson.fromJson(reader, RPC.SignedRequest.class);
    reader.close();

    RPC.AddIssueRequest in = gson.fromJson(rpc.request, RPC.AddIssueRequest.class);
      
    try {
      DBIssue issue = new DBIssue();
      issue.setUrl(in.url);
      issue.setType(in.type);
      issue.setDescription(in.description);
      issue.setEmail(in.email);
      issue.setLogs(in.logs);

      
      try (Manager mgr = ManagerFactory.getInstance().newManager()) {
        mgr.issueDao.create(issue);
        DBEmailQueue email = DBEmailQueue.makeNewIssue(in.email, in.url, in.type, in.description, issue.getId());
        mgr.emailDao.create(email);
        mgr.commitTransaction();
      }

      RPC.AddIssueResponse out = new RPC.AddIssueResponse();
      Util.writeJsonResponse(out, response);
      logMetadata.setResponse(response.getStatus(), out);
      RPCLogger.log(rpc, logMetadata);
    } catch (Exception e) {
      RPC.MitroException out = RPC.MitroException.createFromException(e, RPC.MitroException.MitroExceptionReason.FOR_USER);
      
      // TODO: throw different exceptions based on what went wrong.
      logger.error("unhandled exception; returning 500 error:", e);

      response.setStatus(500);
      Util.writeJsonResponse(out, response);
      
      // we should preserve the raw exceptions in the log so that we can debug.
      // this is important because we are not sending detailed messages to the client any more.
      logMetadata.setResponse(response.getStatus(), out);
      logMetadata.setException(e);
      RPCLogger.log(rpc, logMetadata);

      return;
    }
  }
}
