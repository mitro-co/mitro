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
package co.mitro.core.server;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import java.util.HashMap;

import org.junit.Test;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;

public class TemplatesTest {
  @Test(expected=MustacheException.class)
  public void compileMissing() {
    Templates.compile("missing.mustache");
  }

  @Test(expected=MustacheException.class)
  public void compileMissingPartial() {
    Templates.compile("missing_partial.mustache");
  }

  @Test
  public void compileSuccess() {
    Mustache mustache = Templates.compile("correct.mustache");

    HashMap<String, String> variables = new HashMap<>();
    variables.put("outer_variable", "1");
    variables.put("partial_variable", "2");
    StringWriter writer = new StringWriter();
    mustache.execute(writer, variables);
    assertEquals("outer: 1\npartial: 2\n\n", writer.getBuffer().toString());
  }
}
