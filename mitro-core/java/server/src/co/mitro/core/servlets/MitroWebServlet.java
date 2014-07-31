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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.SignedRequest;
import co.mitro.core.server.data.RPCLogger;

import com.google.gson.Gson;

/**
 * Base class for servlets accessed directly from the web browser (no extension).
 */
abstract public class MitroWebServlet extends HttpServlet {

  private static final Logger logger = LoggerFactory.getLogger(MitroWebServlet.class);
  private static final long serialVersionUID = 1L;

  protected static final Gson gson = new Gson();

  abstract protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception;

  protected void doCommon(HttpServletRequest request, HttpServletResponse response) throws IOException {
    RPC.LogMetadata logMetadata = new RPC.LogMetadata(request);
    
    // Convert the POST parameters into a json object for the RPC log.
    SignedRequest rpc = new SignedRequest();
    rpc.request = gson.toJson(request.getParameterMap());

    try {
      handleRequest(request, response);

      logMetadata.setResponse(response.getStatus(), "");
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

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    doCommon(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    doCommon(request, response);
  }
}