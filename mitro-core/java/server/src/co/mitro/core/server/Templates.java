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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.google.common.base.Charsets;

/** Utilities for rendering Mustache templates. */
public class Templates {
  private static final ResourceMustacheFactory MUSTACHE_FACTORY = new ResourceMustacheFactory();

  // Currently just a utility class (not constructible)
  private Templates() {}

  /** Searches for Mustache templates as Java resources relative to this class. */
  public static class ResourceMustacheFactory extends DefaultMustacheFactory {
    @Override
    public Reader getReader(String resourceName) {
      InputStream is = getClass().getResourceAsStream(resourceName);
      if (is != null) {
        return new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
      }
      return super.getReader(resourceName);
    }

    /** Compiles the Mustache template from the resource templateName, relative to packageClass. */
    public Mustache compilePackageRelative(Class<?> packageClass, String templateName) {
      String packagePath = packageClass.getPackage().getName().replace('.', '/') + "/";
      return compile(packagePath + templateName);
    }
  }

  /** Compiles the template from Java resource templateName, relative to this class. */
  public static Mustache compile(String templateName) {
    return MUSTACHE_FACTORY.compilePackageRelative(Templates.class, templateName);
  }
}
