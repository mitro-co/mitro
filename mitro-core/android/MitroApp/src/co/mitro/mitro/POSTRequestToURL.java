package co.mitro.mitro;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class POSTRequestToURL implements POSTRequestSender {
  private static final int BUFSIZE = 4096;

  private byte[] readBinaryData(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[BUFSIZE];
    int n;

    while ((n = in.read(buffer)) != -1) {
      out.write(buffer, 0, n);
    }
    out.close();
    in.close();

    return out.toByteArray();
  }

  private byte[] readResponse(HttpURLConnection cxn) throws IOException {
    // If the HTTP status code indicates an error, getInputStream() throws an
    // IOException.  The error response can be read from getErrorStream().
    InputStream in;
    try {
      in = cxn.getInputStream();
    } catch (IOException e) {
      in = cxn.getErrorStream();
    }

    // getErrorStream can return null if no data sent.
    if (in == null) {
      return new byte[0];
    } else {
      return readBinaryData(in);
    }
  }

  // Warning: assumes response is a UTF-8 string.
  @Override
  public String getResponse(String url, String params) throws IOException {
    URL obj = new URL(url);
    HttpsURLConnection cxn = (HttpsURLConnection) obj.openConnection();
    cxn.setSSLSocketFactory(new MitroSocketFactory(cxn.getSSLSocketFactory()));
    cxn.setRequestMethod("POST");
    cxn.setDoOutput(true);

    DataOutputStream out = new DataOutputStream(cxn.getOutputStream());
    out.writeBytes(params);
    out.flush();
    out.close();

    return new String(readResponse(cxn), "UTF-8");
  }
}
