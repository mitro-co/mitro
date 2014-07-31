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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.crypto.KeyczarKeyFactory;
import co.mitro.core.exceptions.DoLoginException;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.RetryTransactionException;
import co.mitro.core.exceptions.SendableException;
import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.LogMetadata;
import co.mitro.core.server.data.RPC.MitroException.MitroExceptionReason;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.server.data.RPCLogger;
import co.mitro.core.util.GuavaRequestRateLimiter;
import co.mitro.core.util.RequestRateLimiter;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;

public abstract class MitroServlet extends HttpServlet {
  /** See http://www.postgresql.org/docs/current/static/errcodes-appendix.html */
  private static final String POSTGRES_SERIALIZATION_FAILURE_SQLSTATE = "40001";  
  private static final String POSTGRES_DEADLOCK_DETECTED_SQLSTATE = "40P01";
  private static final Set<String> POSTGRES_RETRY_SQLSTATES = ImmutableSet.of(
      POSTGRES_SERIALIZATION_FAILURE_SQLSTATE, POSTGRES_DEADLOCK_DETECTED_SQLSTATE);

  private static final Logger logger = LoggerFactory
      .getLogger(MitroServlet.class);
  private static final long serialVersionUID = 1L;
  private static double fractionReadRequestsToReject = -1;
  private static final Random insecureRandomGenerator = new Random();

  public static void setPercentReadRequestsToRejectForUseOnlyInEmergencies(Double d) {
    if (d == null || d < 0.0 || d > 1.0) {
      logger.error("invalid reject value {}, setting to 0.0", d);
      d = 0.0;
    } else {
      logger.warn("EMERGENCY -- rejecting {} fraction of traffic", d);
    }
    fractionReadRequestsToReject = d;
  }
  
  protected static final Gson gson = new Gson();

  // Injected dependencies for testing
  private final ManagerFactory managerFactory;
  protected final KeyFactory keyFactory;

  /** Rate limits requests; shared across servlets. */
  // TODO: Inject this properly; quick hack ATM (technical debt right here)
  // TODO: Make this final?
  private RequestRateLimiter requestLimiter = DEFAULT_LIMITER;

  // TODO: Remove both of these?
  private static final RequestRateLimiter DEFAULT_LIMITER = new GuavaRequestRateLimiter();
  public void setRateLimiterForTest(RequestRateLimiter limiter) {
    requestLimiter = limiter;
  }

  protected boolean isPermittedToGetMissingUsers(String user, int count) {
    return requestLimiter.isPermittedToGetMissingUsers(user, count);
  }

  // TODO: Remove this default constructor to force dependencies to be injected?
  public MitroServlet() {
    this(ManagerFactory.getInstance(), new KeyczarKeyFactory());
  }

  public MitroServlet(ManagerFactory managerFactory, KeyFactory keyFactory) {
    this.managerFactory = managerFactory;
    this.keyFactory = keyFactory;
  }

  public static class MitroRequestContext {
    public final DBIdentity requestor;
    public final String jsonRequest;
    public final Manager manager;
    public final String requestServerUrl;
    public final String platform;
    private boolean isGroupSyncRequest;

    public MitroRequestContext(DBIdentity requestor, String jsonRequest,
        Manager manager, String requestServerUrl, String platform) {
      this.requestor = requestor;
      this.jsonRequest = jsonRequest;
      this.manager = manager;
      this.requestServerUrl = requestServerUrl;
      this.platform = platform;
    }

    /**
     * Constructor to create a context without server information.
     * This is used primarily by tests.
     */
    public MitroRequestContext(DBIdentity iden, String json,
        Manager mgr, String requestServerUrl) {
      this(iden, json, mgr, requestServerUrl, null);

    }

    public void setIsGroupSyncRequest(boolean isGroupSyncRequest) {
      this.isGroupSyncRequest = isGroupSyncRequest;
    }

    public boolean isGroupSyncRequest() {
      return isGroupSyncRequest;
    }
  }

  /**
   * Executes the command using information in MitroRequestContext.  
   * The command was issued by context.requestor. Uses context.manager
   * to connect to DB. 
   */
  abstract protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException,
      MitroServletException;

  protected boolean isReadOnly() {
    return true;
  }

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
   *      response)
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setStatus(400);
  }

  protected boolean isCloseTransactionOperation() { return false; }
  protected boolean isBeginTransactionOperation() { return false; }

  private static final class RateLimitedException extends Exception implements SendableException {
    private static final long serialVersionUID = 1L;
    @Override
    public String getUserVisibleMessage() {
      return "Rate limit exceeded; wait between requests";
    }
  }

  static final class DecodedCookie {
    public String guid = null;
    public String referrer = null;
  
    private static final Pattern GUID_MATCHER = Pattern.compile("^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(&ref=(.*))?$"); 
    private static final String MITRO_UID_COOKIE_NAME = "gauuid";

    private DecodedCookie() {};
    
    public static DecodedCookie maybeMakeFromRequest(HttpServletRequest request) throws UnsupportedEncodingException {
      DecodedCookie rval = null;
      Cookie[] cookies = request.getCookies();
      if (null != cookies) {
        for (int i = 0; i < cookies.length; ++i) {
          // "9763e04b-6afd-4859-94d8-1a115766e52e%26ref%3Dwww.google.com"
          if (MITRO_UID_COOKIE_NAME.equals(cookies[i].getName())) {
            if (Strings.isNullOrEmpty(cookies[i].getValue())) {
              continue;
            }
            // the cookie value is a url encoded string that looks like 
            // "0000000-0000-0000-0000-000000000000%26ref%3Dwww.google.com"
            String decodedCookie = URLDecoder.decode(cookies[i].getValue(), "UTF-8");
            Matcher matcher = GUID_MATCHER.matcher(decodedCookie);
            if (matcher.matches()) {
              rval = new DecodedCookie();
              rval.guid = matcher.group(1);
              if (3 == matcher.groupCount() && null != matcher.group(3) && !matcher.group(3).equalsIgnoreCase("undefined")) {
                rval.referrer = matcher.group(3);
              }
              break;
            }
          }
        }
      }
      return rval; 
      
    }
  }

  /** Ignores the rate limit for this operation type. */
  // TODO: Improve the API so this is not required
  static final String HACK_OPERATION = "applyPendingGroups";
  static final String HACK_ENDPOINT = Main.getServletPatterns(GetPublicKeyForIdentity.class)[0];

  /**
   * Returns true if this appears to be a group synchronization request. The group sync API
   * should be improved to be a single request per group, so this can be removed.
   */
  static boolean isGroupSyncRequestHack(Manager manager, String identity, String servletPath,
      String operationName) throws SQLException {
    // identity and operationName come from the client and may be null
    Preconditions.checkNotNull(manager);
    if (identity == null) {
      // can't possibly be a correct group sync request
      return false;
    }

    if (!HACK_ENDPOINT.equals(servletPath) || !HACK_OPERATION.equals(operationName)) {
      return false;
    }

    // potential match: check if the user has pending groups
    DBIdentity requestor = DBIdentity.getIdentityForUserName(manager, identity);
    if (requestor == null) {
      return false;
    }

    // check the pending groups table to see if we are an admin for any pending groups
    // TODO: vijay fix this by using the new infrastructure to figure this out.
    return true;
  }

  /**
   * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
   *      response)
   */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Util.allowCrossOriginRequests(response);
    // Parse the JSON message
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        request.getInputStream(), "UTF-8"));
    final RPC.SignedRequest rpc = gson
        .fromJson(reader, RPC.SignedRequest.class);
    reader.close();

    if (rpc.platform == null) {
      if (rpc.clientIdentifier.contains("Android")) {
        rpc.platform = "ANDROID";
      } else if (rpc.clientIdentifier.contains("iOS")) {
        rpc.platform = "IOS";
      } else {
        rpc.platform = "UNKNOWN";
      }
    }
    
    logger.info("servlet={} id={} txn={} client={} platform={}", request.getServletPath(),
        rpc.identity, rpc.transactionId, rpc.clientIdentifier, rpc.platform);

    final LogMetadata logMetadata = new LogMetadata(request);

    Manager mgr = null;
    DBIdentity requestor = null;
    boolean commitAndCloseOnSuccess = isCloseTransactionOperation() || (rpc.implicitEndTransaction);
    boolean startTransaction = isBeginTransactionOperation() || rpc.implicitBeginTransaction;
    try {

      if (null == rpc.transactionId) {
        commitAndCloseOnSuccess = !startTransaction;

        // in case of emergencies, we can reject some fraction of read traffic which 
        // will allow our replicas to handle that traffic. (Client will re-try)
        if (!startTransaction && fractionReadRequestsToReject > 0.0
            // this readonly check will ensure we do not match AddIdentity
            && isReadOnly()) {
          double random = insecureRandomGenerator.nextDouble();
          if (random < fractionReadRequestsToReject) {
            throw new MitroServletException("rejecting request due to random reject");
          }
        }
        mgr = managerFactory.newManager();
        if (!commitAndCloseOnSuccess) {
          mgr.enableCaches();
        }
      } else {
        assert mgr == null;
        mgr = managerFactory.getTransaction(rpc.transactionId);
      }
      assert mgr != null : "no transaction found for id " + rpc.transactionId;
      mgr.setUserIp(request.getRemoteAddr());
      if (mgr.lock.tryLock()) {
        try {
          // Rate limit requests according to policy
          boolean isGroupSyncRequest = isGroupSyncRequestHack(
              mgr, rpc.identity, request.getServletPath(), mgr.getOperationName());
          if (!requestLimiter.isRequestPermitted(request.getRemoteAddr(), request.getServletPath())) {
            // Check if we should ignore this for group sync
            // TODO: This is a hack! We should remove this
            if (isGroupSyncRequest) {
              logger.info("ignoring rate limit for group sync operation");
            } else {
              // TODO: Monitor rate limits that are triggered
              // TODO: After a limit is triggered, throttle the IP?
              logger.error("rate limited: ip={} endpoint={}",
                  request.getRemoteAddr(), request.getServletPath());
              throw new RateLimitedException();
            }
          }

          boolean validSignature = this instanceof GetMyPrivateKey || this instanceof UpdateSecretFromAgent;
          if (rpc.identity != null && rpc.signature != null) {
            requestor = DBIdentity.getIdentityForUserName(mgr, rpc.identity);
            String publicKeyString = null;
            if (requestor != null) {
              publicKeyString = requestor.getPublicKeyString();
              if (updateIdentityWithCookies(request, requestor)) {
                mgr.identityDao.update(requestor);
              }
              // try to set the referrer and guid info if they're not already in the DB.
            } else if (this instanceof AddIdentity) {
              // Verifies that the user really does have access to this private
              // key
              RPC.AddIdentityRequest r = gson.fromJson(rpc.request,
                  RPC.AddIdentityRequest.class);
              publicKeyString = r.publicKey;
            }
            if (publicKeyString != null) {
              // check the signature
              PublicKeyInterface key = keyFactory
                  .loadPublicKey(publicKeyString);
              validSignature = key.verify(rpc.request, rpc.signature);
              mgr.setRequestor(requestor, null);
            }
          }

          if (!validSignature) {
            // Generic error message: ensures users can't tell if an identity
            // exists or not
            // although there is almost certainly a timing attack here
            throw new InvalidRequestException("Invalid identity or signature");
          }

          if (requestor != null) {
            // verify that the user has not been logged out.
            RPC.MitroRPC genericRpc = gson.fromJson(rpc.request, RPC.MitroRPC.class);
            mgr.setRequestor(requestor, genericRpc.deviceId);
            if (null == GetMyDeviceKey.maybeGetClientKeyForLogin(mgr, requestor, genericRpc.deviceId, rpc.platform)) {
              // device is no longer authorized (e.g. it was, but the user changed the password)
              // they need to retype their password before any requests will work
              throw new DoLoginException();
            }
          }

          String requestServerUrl = new URL(request.getScheme(),
              request.getServerName(),
              request.getServerPort(),
              "").toString();
          
          if (rpc.implicitBeginTransaction && rpc.transactionId != null) {
            throw new MitroServletException("Cannot create a new transaction when you're in one");
          }
          // if we have to implicitly open a transaction, write the audit logs and 
          // record the operation name now
          if (!isBeginTransactionOperation() && rpc.implicitBeginTransaction) {
            BeginTransactionServlet.beginTransaction(mgr, rpc.operationName, requestor);
          }
          
          MitroRequestContext requestContext = new MitroRequestContext(requestor, rpc.request, mgr, requestServerUrl, rpc.platform);
          requestContext.setIsGroupSyncRequest(isGroupSyncRequest);

          MitroRPC out = processCommand(requestContext);
          
          // if transaction id is cleared by the servlet, we need to close the
          // connection.
          if (commitAndCloseOnSuccess) {
            mgr.commitTransaction();
            mgr.close();
          } else {
            out.transactionId = mgr.getTransactionId();
          }

          Util.writeJsonResponse(out, response);
          logMetadata.setResponse(response.getStatus(), out);
          RPCLogger.log(rpc, logMetadata);
        } catch (Exception e) {
          try {
            mgr.rollbackTransaction();
          } finally {
            mgr.close();
          }
          throw e;
        } finally {
          mgr.lock.unlock();
        }
      } else {
        throw new InvalidRequestException("transaction id already in use: "
            + mgr.getTransactionId());
      }
    } catch (Throwable e) {
      // User-visible exceptions are "forbidden" so they don't get retried on the secondary
      // TODO: Add HTTP status code to SendableException?
      int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
      // try to see if there's a pivot wrapped in there somewhere
    
      PSQLException p = Manager.extractPSQLException(e); 
      if (null != p && POSTGRES_RETRY_SQLSTATES.contains(p.getSQLState())) {
        e = new RetryTransactionException(e);
        // pivot exceptions should not send forbidden
      } else if (e instanceof SendableException) {
        // SQLExceptions are not sendable so it's okay that this is in the else block
        statusCode = HttpServletResponse.SC_FORBIDDEN;
      }
    
      RPC.MitroException out = RPC.MitroException.createFromException(e, MitroExceptionReason.FOR_USER);
      
      // TODO: throw different exceptions based on what went wrong.
      logger.error("unhandled exception (exceptionId:{}); returning error code {}:", out.exceptionId, statusCode, e);

      response.setHeader("Content-Type", "application/json");
      response.setStatus(statusCode);
      response.getWriter().write(gson.toJson(out));
      
      // we should preserve the raw exceptions in the log so that we can debug.
      // this is important because we are not sending detailed messages to the client any more.
      logMetadata.setResponse(response.getStatus(), out);
      logMetadata.setException(e);
      RPCLogger.log(rpc, logMetadata);
      return;
    }
  }

  /**
   * Updates an identity object with cookie info from a request, and 
   * returns whether or not the identity should be updated in the DB
   * @return should the identity be updated?
   * @throws UnsupportedEncodingException
   */
  static boolean updateIdentityWithCookies(HttpServletRequest request,
      DBIdentity requestor) throws UnsupportedEncodingException {
    boolean dirty = false;
    DecodedCookie refGuid = DecodedCookie.maybeMakeFromRequest(request);
    if (null != refGuid) {
      if (requestor.getReferrer() == null && null != refGuid.referrer) {
        dirty = true;
        requestor.setReferrer(refGuid.referrer);
      }
      if (requestor.getGuidCookie() == null && null != refGuid.guid) {
        dirty = true;
        requestor.setGuidCookie(refGuid.guid);
      }
    }
    return dirty;
  }

  // TODO: Remove once all deprecated user ids are removed.
  protected void throwIfRequestorDoesNotEqualUserId(DBIdentity requestor, String user)
      throws InvalidRequestException {
    if (user != null && !requestor.getName().equals(user)) {
      // this does not leak information: there is exactly one permitted value: the requestor's id
      throw new InvalidRequestException("User ID does not match rpc requestor");
    }
  }

  public static <T, R> Map<T,R> createMapIfNull(Map<T,R> map) {
    return map == null ? new HashMap<T,R>() : map;
  }

  public static <T> List<T> uniquifyCollection(Collection<T> collection) {
    if (collection != null) {
      return Lists.newArrayList(Sets.newHashSet(collection));
    } else {
      // caller may add objects to this set so we can't use Collections.EmptyList
      return Lists.newArrayList();
    }
  }
}
