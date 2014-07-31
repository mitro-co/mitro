package co.mitro.mitro;

import java.io.IOException;
import java.net.MalformedURLException;

public interface POSTRequestSender {
  /**
   * @param args
   * @throws MalformedURLException
   * @throws IOException
   */

  public String getResponse(String url, String params) throws MalformedURLException, IOException;
}
