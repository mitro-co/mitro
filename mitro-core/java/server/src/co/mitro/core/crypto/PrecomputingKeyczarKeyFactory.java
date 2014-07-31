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
package co.mitro.core.crypto;

import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;

public class PrecomputingKeyczarKeyFactory extends KeyczarKeyFactory {
  /** Number of keys to keep cached: This also controls the maximum number of GetPublicKey invites. */
  public static final int DEFAULT_QUEUE_SIZE = 200;

  /** Keys expire after a random timeout in range [x/2, x). ~1 key/2 minutes. */
  private static final int DEFAULT_MAX_KEY_EXPIRY_SEC = DEFAULT_QUEUE_SIZE*4*60;
  
  private static final Logger logger = LoggerFactory.getLogger(PrecomputingKeyczarKeyFactory.class);
  private static class Container implements Comparable<Container> {
    private static final Random rng = new Random();
    public Container(PrivateKeyInterface pki, int maxAgeMs) {
      assert (maxAgeMs > 1);
      // randomize expiry times to ensure we don't expire everything simultaneously.
      expiryTimeMs = System.currentTimeMillis() + maxAgeMs/2 + rng.nextInt(maxAgeMs/2);
      this.pki = pki;
    }
    public final PrivateKeyInterface pki;
    private final long expiryTimeMs;

    @Override
    public int compareTo(Container other) {
      // backwards due to priority queue sorting from greatest to least (we want backwards sort)
      return (int)(- other.expiryTimeMs + this.expiryTimeMs);
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof Container)) {
        return false;
      }
      return compareTo((Container) other) == 0;
    }

    @Override
    public int hashCode() {
      return Long.valueOf(expiryTimeMs).hashCode();
    }
  }

  private final PriorityBlockingQueue<Container> queue = new PriorityBlockingQueue<Container>();
  private final int kMaxAgeMs;
  private final int kMaxQueueSize;
  public PrecomputingKeyczarKeyFactory(int keysToPrecompute, int maxKeyAgeSeconds) {
    kMaxQueueSize = keysToPrecompute;
    kMaxAgeMs = maxKeyAgeSeconds * 1000;
    logger.info("Starting keyczar factory background task");
    Thread backgroundTask = new Thread(new QueueFiller());
    // mark as daemon so JVM exits if this thread is still running
    backgroundTask.setDaemon(true);
    backgroundTask.start();
  }
  
  public PrecomputingKeyczarKeyFactory() {
    this(DEFAULT_QUEUE_SIZE, DEFAULT_MAX_KEY_EXPIRY_SEC);
  }
  
  @Override
  public PrivateKeyInterface generate() throws CryptoError {
    for (;;) {
      try {
        return queue.take().pki;  
      } catch (InterruptedException e) {
        logger.error("This should not have happened", e);
      }
    }
  }

  private class QueueFiller implements Runnable {

    public void run() {
      logger.debug("Started filling queue");
      
      for (;;) {
        // get rid of expired keys
        for (;;) {
          Container frontKey = queue.peek();
          if (frontKey == null || frontKey.expiryTimeMs > System.currentTimeMillis()) {
            break;
          }
          
          // use poll, not take so we don't block (in case something has emptied 
          //queue between this and previous statement.)
          if (null != (frontKey = queue.poll())) {
            // to avoid race condition
            logger.info("expiring precomputed key with timestamp {}", frontKey.expiryTimeMs);
          }
        }
        
        // make new keys
        while (queue.size() < kMaxQueueSize) {
          try {
            logger.info("precomputing key. ({}/{})", queue.size(), kMaxQueueSize);
            queue.put(new Container(PrecomputingKeyczarKeyFactory.super.generate(), kMaxAgeMs));
          } catch (CryptoError e) {
            logger.error("unexpected crypto error when generating keys; ignored. Hope this doesn't break something", e);
          }
        }

        // wait, then try this again.
        try {
          Thread.sleep(250);
        } catch (InterruptedException e) {
          logger.error("unexpected exception", e);
        }
      } 
    }
  }
}
