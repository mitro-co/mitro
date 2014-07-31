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
package co.mitro.core.server.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class OldJsonData {
  // hack for gson
  static final Type LIST_OF_SERVICES_TYPE = new TypeToken<List<OldJsonService>>() {}.getType();
  static final String STATIC_HOSTNAME = "https://www.mitro.co";

  public static class OldJsonService {
    String auth_type;
    String home_url;
    String login_url;
    List<String> good_url_title;
    String username_field;
    String password_field;
    List<String> icons;
    String name="";
  };
  
  public OldJsonData () {
    ;
  }
  
  public static OldJsonData createFromStream(InputStream is) {
    List<OldJsonService> svcList = Lists.newArrayList();
    Gson gson = new Gson();
    svcList = gson.fromJson(new InputStreamReader(is, Charsets.UTF_8), LIST_OF_SERVICES_TYPE);
    
    return new OldJsonData(svcList);
  }
  
  public List<String> getIcons(String url) {
    List<String> rval =  Lists.newArrayList();
    OldJsonService svc = getServiceForUrl(url);
    if (null != svc && null != svc.icons) {
      for (String iconUrl : svc.icons) {
        // ensure this url is valid
        try {
          URL u = new URL(iconUrl.startsWith("/") ? STATIC_HOSTNAME + iconUrl : iconUrl);
          rval.add(u.toString());
        } catch (MalformedURLException e) {
          ;
        }
        
      }
    }
    return rval;
  }
  
  public String getTitle(String url) {
    OldJsonService svc = getServiceForUrl(url);
    return  (null == svc) ? "" : svc.name;
  }

  /**
   * @param url
   * @return
   */
  protected OldJsonService getServiceForUrl(String url) {
    OldJsonService svc = null;
    try {
      svc = urlToServiceMap.get(getCanonicalHost(url));
    } catch (MalformedURLException e) {
      ;
    }
    return svc;
  }
  private final Map<String, OldJsonService> urlToServiceMap = Maps.newTreeMap();
  
  private static String getCanonicalHost(String url) throws MalformedURLException {
      final String WWW_PREFIX = "www.";

      String host = new URL(url).getHost();
      if (host.startsWith(WWW_PREFIX)) {
          host = host.substring(WWW_PREFIX.length());
      }
      return host;
  }

  public OldJsonData(List<OldJsonService> svcList) {
    for (OldJsonService svc: svcList) {
      try {
        urlToServiceMap.put(getCanonicalHost(svc.login_url), svc);
      } catch (MalformedURLException e) {
        ;
      }
      try {
        urlToServiceMap.put(getCanonicalHost(svc.home_url), svc);
      } catch (MalformedURLException e) {
        ;
      }
    }
  }
/*   
  {
    "auth_type": "ext", 
    "bad_url_title": [
      "https://github.com/users", 
      ""
    ], 
    "category": "Software Development", 
    "cookies": "logged_in=no; tracker=direct; __utma=1.930857028.1358264559.1358264559.1358264559.1; __utmb=1.1.10.1358264559; __utmc=1; __utmz=1.1358264559.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none); _gauges_cookie=1; _gauges_unique_hour=1; _gauges_unique_day=1; _gauges_unique_month=1; _gauges_unique_year=1; _gauges_unique=1", 
    "form_template": "<form>", 
    "good_url_title": [
      "https://github.com/users", 
      "Sign up \u00b7 GitHub"
    ], 
    "home_url": "https://github.com/", 
    "icons": [
      "/static/service_img/2145027955-favicon.png", 
      "https://github.com/favicon.ico"
    ], 
    "logged_in": {
      "cookies": [
        {
          "name": "logged_in", 
          "value_re": "^yes$"
        }
      ], 
      "domain": "github.com", 
      "username": [
        {
          "name": "spy_user", 
          "value_re": "(.*)"
        }
      ]
    }, 
    "login_url": "https://github.com/login", 
    "matching_cookies": [], 
    "name": "GitHub", 
    "password": "tempass1", 
    "password_field": "password", 
    "supported_auths": {
      "extension": true, 
      "form": false
    }, 
    "uid": 2145027955, 
    "username": "lectorius", 
    "username_field": "login"
  }
*/
  
}
