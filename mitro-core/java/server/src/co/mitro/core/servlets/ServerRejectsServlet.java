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
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

@WebServlet("/ServerRejects")
public class ServerRejectsServlet extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(ServerRejectsServlet.class);
  private static final long serialVersionUID = 1L;
  private static final Gson gson = new Gson();

  // Magic to deserialize a generic typed list using Gson
  // See https://sites.google.com/site/gson/gson-user-guide#TOC-Serializing-and-Deserializing-Generic-Types
  private static final Type listType = new TypeToken<List<HintEntry>>(){}.getType();

  private static String serverHintsJson = null;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (serverHintsJson != null) {
      // allow cross origin requests for the web version
      Util.allowCrossOriginRequests(response);
      response.setContentType(Util.JSON_CONTENT_TYPE);
      response.getWriter().write(serverHintsJson);
    } else {
      response.sendError(500);
    }
  }

  /**
   * Sets the server hints to hintsString, after validation. Throws exceptions if the JSON data
   * is incorrect, although the validation isn't currently extremely careful.
   */
  public static void setServerHintsJson(String hintsString) {
    try {
      // Parse and validate the hints
      // TODO: Unit test this more carefully

      // can't use gson.fromJson() because it always uses lenient parsing; copied from there
      // See https://code.google.com/p/google-gson/issues/detail?id=372
      JsonReader jsonReader = new JsonReader(new StringReader(hintsString));
      TypeAdapter<?> typeAdapter = gson.getAdapter(TypeToken.get(listType));
      @SuppressWarnings("unchecked")
      List<HintEntry> hints = (List<HintEntry>) typeAdapter.read(jsonReader);

      for (HintEntry hint : hints) {
        @SuppressWarnings("unused")
        Pattern regexp = Pattern.compile(hint.regex);

        if (hint.additional_submit_button_ids != null) {
          // optional: just don't include it instead of including an empty list
          assert hint.additional_submit_button_ids.size() > 0;
          for (String submitIds : hint.additional_submit_button_ids) {
            assert !Strings.isNullOrEmpty(submitIds);
          }
        }

        if (hint.allow_empty_username != null) {
          assert hint.allow_empty_username : "omit allow_empty_username if false";
        }

        if (hint.empty_password_username_selector != null) {
          // TODO: Validate that this is a valid CSS selector?
          assert !hint.empty_password_username_selector.isEmpty() :
              "omit empty_password_username_selector if there is no selector";
          assert hint.allow_empty_username != null && hint.allow_empty_username.booleanValue() :
              "allow_empty_username must be true if empty_password_username_selector is present";
        }

        validateRules(hint.reject.login_submit);
        validateRules(hint.reject.submit);
        validateRules(hint.reject.password);
        validateRules(hint.reject.username);
        validateRules(hint.reject.form);
      }
      logger.info("setting {} server hints", hints.size());

      serverHintsJson = hintsString;
    } catch (IOException|IllegalStateException e) {
      // Rethrow the same way as gson.fromJson()
      throw new JsonSyntaxException(e);
    }
  }

  private static void validateRules(List<List<Attribute>> rules) {
    for (List<Attribute> andRule : rules) {
      // if an AND rule exists, it must be non-empty
      assert andRule.size() > 0;
      for (Attribute attribute : andRule) {
        assert !Strings.isNullOrEmpty(attribute.attributeName);
        // Exactly one of exactMatch or regexMatch must be non-empty; the other must be null
        if (!Strings.isNullOrEmpty(attribute.regexMatch)) {
          @SuppressWarnings("unused")
          Pattern regexp = Pattern.compile(attribute.regexMatch);
          assert attribute.exactMatch == null;
        } else {
          assert !Strings.isNullOrEmpty(attribute.exactMatch);
          assert attribute.regexMatch == null;
        }
      }
    }
  }

  private static class Attribute {
    public String attributeName;
    public String exactMatch;
    public String regexMatch;
  }

  private static class RejectEntry {
    public List<List<Attribute>> login_submit;
    public List<List<Attribute>> submit;
    public List<List<Attribute>> password;
    public List<List<Attribute>> username;
    public List<List<Attribute>> form;
  }

  private static class HintEntry {
    public String regex;
    public List<String> additional_submit_button_ids;
    /** Boolean so this is null if absent. */
    public Boolean allow_empty_username;
    public String empty_password_username_selector;
    public RejectEntry reject;
  }
}
