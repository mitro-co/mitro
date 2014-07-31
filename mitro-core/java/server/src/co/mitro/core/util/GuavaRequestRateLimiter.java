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
package co.mitro.core.util;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.RateLimiter;

/** RequestRateLimiter implemented using Guava. */
public class GuavaRequestRateLimiter implements RequestRateLimiter {
  private static final Logger logger = LoggerFactory.getLogger(GuavaRequestRateLimiter.class);
  private static final int MAX_ENTRIES = 4096;
  /** Default rate of permitted requests/second. */
  private static final double DEFAULT_RATE = 2.0;

  private static final String MAGIC_KEY_ENDPOINT = "magic_key_endpoint";

  // Larger values permit email address harvesting; small values limit users
  // (e.g. adding X people at one time to a service).
  // TODO: Should we prevent large requests? Do smarter rate limiting?
  private static final int MAX_USERS_PER_SECOND = 20;
  static {
    // If this triggers, carefully review this setting!
    // Previously group syncing ran into this limit; now we separate that
    assert 10 <= MAX_USERS_PER_SECOND && MAX_USERS_PER_SECOND <= 40;
  }

  /** Restricts critical APIs below the default rate. */
  // NOTE: Guava RateLimiter permits a burst up to the per second rate, then throttles
  // E.g. with rate=2.0, acquires happen after 0, 0, 0, 500, 1000, 1500, ... ms.
  // HOWEVER, new limiters will release acquires after 0, 500, 1000, 1500, ... ms
  // TODO: Replace Guava RateLimiter with something with a longer period bucket
  // e.g. iSEC suggested 10 GetMyPrivateKey in 15 minutes. And after triggering, we should
  // make it more restrictive
  private static final Map<String, Double> ENDPOINT_RATES =
      new ImmutableMap.Builder<String, Double>()
          // Critical low rate endpoints
          .put("/api/GetMyPrivateKey", 0.5)
          .put("/api/AddIdentity", 1/10.)

          // frequently used endpoints need high rates
          .put("/api/ListMySecretsAndGroupKeys", 10.)
          .put("/api/BeginTransaction", 10.)
          .put("/api/EndTransaction", 10.)
          // Triggers when adding multiple secrets to a team? TODO: Investigate? 
          .put("/api/GetSecret", 10.)
          // Editing a secret ACL with many groups can cause many calls
          .put("/api/GetPublicKeyForIdentity", 5.0)

          .put(MAGIC_KEY_ENDPOINT, (double) MAX_USERS_PER_SECOND)
          .build();

  private final LoadingCache<CacheKey, RateLimiter> rateLimits = CacheBuilder.newBuilder()
      // docs say maximum size performs better than soft references
      .maximumSize(MAX_ENTRIES)
      .build(new Loader());

  private static final class CacheKey {
    public final String ip;
    public final String endpoint;

    public CacheKey(String ip, String endpoint) {
      this.ip = Objects.requireNonNull(ip);
      this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ip, endpoint);
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof CacheKey) {
        CacheKey other = (CacheKey) o;
        return ip.equals(other.ip) && endpoint.equals(other.endpoint);
      }

      return false;
    }
  }

  private final static class Loader extends CacheLoader<CacheKey, RateLimiter> {
    @Override
    public RateLimiter load(CacheKey key) {
      double rateLimit = DEFAULT_RATE;
      Double endpointLimit = ENDPOINT_RATES.get(key.endpoint);
      if (endpointLimit != null) {
        rateLimit = endpointLimit;
      }

      logger.debug("new RateLimiter ip={} endpoint={} -> limit={}",
          key.ip, key.endpoint, rateLimit);
      return RateLimiter.create(rateLimit);
    }
  }

  private boolean isRequestPermittedWithCount(String ip, String endpoint, int count) {
    CacheKey key = new CacheKey(ip, endpoint);
    RateLimiter limit = rateLimits.getUnchecked(key);
    if (limit.tryAcquire()) {
      return true;
    }

    // didn't get it right away, log, wait for 1 second, then fail request
    logger.warn("rate limited; waiting for up to 1 second (ip {}, endpoint {})", ip, endpoint);
    return limit.tryAcquire(count, TimeUnit.SECONDS);
  }

  @Override
  public boolean isRequestPermitted(String ip, String endpoint) {
    return isRequestPermittedWithCount(ip, endpoint, 1);
  }

  // TODO: Move this to its own class?
  @Override
  public boolean isPermittedToGetMissingUsers(String user, int count) {
    return isRequestPermittedWithCount(user, MAGIC_KEY_ENDPOINT, count);
  }
}
