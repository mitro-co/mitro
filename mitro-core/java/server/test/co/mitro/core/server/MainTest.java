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
package co.mitro.core.server;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Charsets;
import com.google.gson.Gson;

public class MainTest {
  private static final Gson gson = new Gson();

  private static final class RequestData {
    public final String scheme;
    public final String remoteAddr;
    public final String requestURL;

    public RequestData(String scheme, String remoteAddr, String requestURL) {
      this.scheme = scheme;
      this.remoteAddr = remoteAddr;
      this.requestURL = requestURL;
    }
  }

  @WebServlet("/Test")
  private static final class TestHandler extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      RequestData response = new RequestData(
          req.getScheme(), req.getRemoteAddr(), req.getRequestURL().toString());
      resp.setContentType("application/json");
      gson.toJson(response, resp.getWriter());
    }
  }

  private RequestData readResponse(URLConnection conn) throws IOException {
    InputStream input = conn.getInputStream();
    RequestData response = gson.fromJson(
        new InputStreamReader(input, Charsets.UTF_8), RequestData.class);
    input.close();
    return response;
  }

  /** Returns a port that is most likely available. No guarantees though. */
  private static int getProbablyAvailablePort() {
    // Open an available port then close it. There is a race condition here.
    int port = -1;
    try {
      ServerSocket s = new ServerSocket();
      s.setReuseAddress(true);
      s.bind(null);
      port = s.getLocalPort();
      s.close();
    } catch (IOException e) {
      throw new RuntimeException("Unexpected: could not bind an available port", e);
    }
    return port;
  }

  @Before
  public void setUp() throws NoSuchAlgorithmException, KeyManagementException {
    // Do not validate SSL certificates
    TrustManager[] trustAllCerts = new TrustManager[] {
      new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
        }
      }
    };
    final SSLContext sc = SSLContext.getInstance("SSL");
    sc.init(null, trustAllCerts, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

    HostnameVerifier allHostsValid = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

    // Permit overriding the Host header
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
  }

  @Test
  public void testHttpsScheme() throws Exception {
    // start a local server
    // TODO: Retry if ports are not available; this could fail in weird circumstances
    Main.HTTP_PORT = getProbablyAvailablePort();
    Main.HTTPS_PORT = getProbablyAvailablePort();
    MetricRegistry metrics = new MetricRegistry();

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/servlets");
    Main.registerServlet(context, new TestHandler());

    Server server = Main.createJetty(metrics, context);
    server.start();

    // Make an HTTP request and read the response
    URL url = new URL("http://localhost:" + Main.HTTP_PORT + "/servlets/Test");
    URLConnection conn = url.openConnection();
    RequestData response = readResponse(conn);
    assertEquals("http", response.scheme);
    assertEquals("127.0.0.1", response.remoteAddr);
    assertEquals(url.toString(), response.requestURL);

    // Make an HTTP request with X-Forwarded-For, Host, and X-Forwarded-Proto
    conn = url.openConnection();
    // client, proxy1, proxy2 see http://en.wikipedia.org/wiki/X-Forwarded-For
    conn.setRequestProperty("X-Forwarded-For", "1.2.3.4, 9.9.9.9, 10.10.10.10");
    conn.setRequestProperty("Host", "foobar.com");
    conn.setRequestProperty("X-Forwarded-Proto", "https");
    response = readResponse(conn);
    assertEquals("https", response.scheme);
    assertEquals("1.2.3.4", response.remoteAddr);
    assertEquals("https://foobar.com/servlets/Test", response.requestURL);

    // HTTPS request
    url = new URL("https://localhost:" + Main.HTTPS_PORT + "/servlets/Test");
    conn = url.openConnection();
    response = readResponse(conn);
    assertEquals("https", response.scheme);
    assertEquals("127.0.0.1", response.remoteAddr);
    assertEquals(url.toString(), response.requestURL);

    // HTTPS with X-Forwarded-For, Host,
    conn = url.openConnection();
    conn.setRequestProperty("X-Forwarded-For", "1.2.3.4, 9.9.9.9, 10.10.10.10");
    conn.setRequestProperty("Host", "foobar.com");
    response = readResponse(conn);
    assertEquals("1.2.3.4", response.remoteAddr);
    assertEquals("https://foobar.com/servlets/Test", response.requestURL);

    server.stop();
    server.join();
  }

  @Test
  public void testLoadDefaultServerHints() throws Exception {
    // validates the default server_hists.json file
    Main.loadDefaultServerHints();
  }
}
