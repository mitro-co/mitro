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
package co.mitro.twofactor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import com.google.common.base.Preconditions;

@WebServlet("/TwoFactorAuth/QRGenerator")
public class QRGenerator extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String ISSUER = "Mitro";

  private static String urlEncode(String input) {
    try {
      return URLEncoder.encode(input, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 must be supported", e);
    }
  }

  // See: https://code.google.com/p/google-authenticator/wiki/KeyUriFormat
  public static String makeTotpUrl(String issuer, String accountName, String secret) {
    Preconditions.checkArgument(!issuer.isEmpty());
    Preconditions.checkArgument(!accountName.isEmpty());
    Preconditions.checkArgument(!secret.isEmpty());
    // "Neither issuer nor account name may themselves contain a colon"
    Preconditions.checkArgument(!issuer.contains(":"));
    Preconditions.checkArgument(!accountName.contains(":"));

    StringBuilder output = new StringBuilder(urlEncode(issuer));
    output.append(':');
    output.append(urlEncode(accountName));

    output.append("?secret=");
    output.append(urlEncode(secret));
    output.append("&issuer=");
    output.append(urlEncode(issuer));
    return "otpauth://totp/" + output.toString();
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    TwoFactorServlet.preventCaching(response);
    String username = request.getParameter("username");
    String secret = request.getParameter("secret");

    String pic = makeTotpUrl(ISSUER, username, secret);
    ByteArrayOutputStream qrImageOutput = QRCode.from(pic).to(ImageType.PNG)
        .stream();
    response.setContentType("image/png");
    response.setContentLength(qrImageOutput.size());
    OutputStream outStream = response.getOutputStream();
    outStream.write(qrImageOutput.toByteArray());
    outStream.flush();
    outStream.close();
  }
}
