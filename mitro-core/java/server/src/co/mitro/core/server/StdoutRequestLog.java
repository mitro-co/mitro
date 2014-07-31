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

import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.DateCache;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import com.google.common.net.HttpHeaders;

/**
 * Outputs request logs to STDOUT, in "standard" HTTPD format (stolen from Dropwizard)
 * @deprecated Replaced with Jetty's Slf4jRequestLog
 */
// TODO: Remove this since it is no longer used?
@Deprecated
final class StdoutRequestLog extends AbstractLifeCycle implements RequestLog {
  // DateCache is thread-safe, but is synchronized so could potentially cause contention
  // TODO: Looking briefly at the source, it looks like DateCache.format doesn't cache anything,
  // and only DateCache.formatNow does. This may be useless? Look at what DropWizard does.
  private final DateCache dateCache = new DateCache();

  @Override
  public void log(Request request, Response response) {
    // copied almost entirely from NCSARequestLog
    final StringBuilder buf = new StringBuilder(256);
    String address = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
    if (address == null) {
      address = request.getRemoteAddr();
    }

    buf.append(address);
    buf.append(" - ");
    final Authentication authentication = request.getAuthentication();
    if (authentication instanceof Authentication.User) {
      buf.append(((Authentication.User) authentication).getUserIdentity()
          .getUserPrincipal().getName());
    } else {
      buf.append('-');
    }

    buf.append(" [");
    buf.append(dateCache.format(request.getTimeStamp()));

    buf.append("] \"");
    buf.append(request.getMethod());
    buf.append(' ');
    buf.append(request.getUri().toString());
    buf.append(' ');
    buf.append(request.getProtocol());
    buf.append("\" ");

    // TODO: Handle async requests?
    // e.g. if (request.getAsyncContinuation().isInitial())
    assert !request.isAsyncStarted();

    int status = response.getStatus();
    if (status <= 0) {
      if (request.isHandled()) {
        status = 200;
      } else {
        status = 404;
      }
    }
    buf.append((char) ('0' + ((status / 100) % 10)));
    buf.append((char) ('0' + ((status / 10) % 10)));
    buf.append((char) ('0' + (status % 10)));

    final long responseLength = response.getContentCount();
    if (responseLength >= 0) {
      buf.append(' ');
      if (responseLength > 99999) {
        buf.append(responseLength);
      } else {
        if (responseLength > 9999) {
          buf.append((char) ('0' + ((responseLength / 10000) % 10)));
        }
        if (responseLength > 999) {
          buf.append((char) ('0' + ((responseLength / 1000) % 10)));
        }
        if (responseLength > 99) {
          buf.append((char) ('0' + ((responseLength / 100) % 10)));
        }
        if (responseLength > 9) {
          buf.append((char) ('0' + ((responseLength / 10) % 10)));
        }
        buf.append((char) ('0' + (responseLength % 10)));
      }
    } else {
      buf.append(" -");
    }

    final long now = System.currentTimeMillis();
    final long dispatchTime = request.getDispatchTime();

    buf.append(' ');
    buf.append(now
        - ((dispatchTime == 0) ? request.getTimeStamp() : dispatchTime));

    buf.append(' ');
    buf.append(now - request.getTimeStamp());

    System.out.println(buf.toString());
  }
}
