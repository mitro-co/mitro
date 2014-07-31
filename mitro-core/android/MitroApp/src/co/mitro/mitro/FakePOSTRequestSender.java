package co.mitro.mitro;

import java.io.IOException;
import java.net.MalformedURLException;

public class FakePOSTRequestSender implements POSTRequestSender {
  public String nextResponse;
  public String lastRequest;

  @Override
  public String getResponse(String url, String params) throws MalformedURLException, IOException {
    lastRequest = params;
    return nextResponse;
  }
}
