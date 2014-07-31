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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.crypto.KeyInterfacesTest;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.ManagerFactory.ConnectionMode;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBPendingGroup;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.BeginTransactionRequest;
import co.mitro.core.server.data.RPC.MitroException;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.server.data.RPC.SignedRequest;
import co.mitro.core.servlets.MitroServlet.DecodedCookie;
import co.mitro.core.util.NullRequestRateLimiter;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

import com.google.gson.Gson;

public class MitroServletTest {
  public static TemporaryFolder tempFolder = new TemporaryFolder();

  private static Process postgres;
  // TODO: Find an available port
  private static final int postgresPort = 12345;

  private ManagerFactory managerFactory;

  private Manager manager;
  private final Gson gson = new Gson();

  private final static String DBNAME = "testdb";
  private static String getDatabaseUrl() {
    return "jdbc:postgresql://[::1]:" + postgresPort + "/" + DBNAME;
  }

  private final static String IDENTITY = "u@example.com";
  private final static String DEVICE_ID = "device_id";

  /** Locations to look for Postgres binaries. */
  private final static String[] EXTRA_POSTGRES_LOCATIONS = {
    // Homebrew Mac OS X
    "/usr/local/bin",
    // Ubuntu
    "/usr/lib/postgresql/9.1/bin",
  };

  private final static String INITDB = "initdb";

  /** Creates a database in directoryPath named databaseName and starts Postgres. */
  public static Process createPostgres(String directoryPath, String databaseName) throws IOException, InterruptedException {
    // Look for the initdb command in some extra locations
    // If we fail, it will use the default search path
    String extraPath = "";
    for (String testPath : EXTRA_POSTGRES_LOCATIONS) {
      if ((new File(testPath + "/" + INITDB)).canExecute()) {
        extraPath = testPath + "/";
        break;
      }
    }

    // Create a new postgres DB
    Runtime runtime = Runtime.getRuntime();
    String[] args = {extraPath + INITDB, directoryPath};
    Process initdb = runtime.exec(args);

    byte[] output = new byte[4096];
    int bytesRead = initdb.getErrorStream().read(output, 0, output.length);
    if (bytesRead < 0) {
      throw new RuntimeException("Reading from error stream failed");
    }
    System.out.write(output, 0, bytesRead);
    int result = initdb.waitFor();
    if (result != 0) {
      throw new RuntimeException("initdb failed: " + result);
    }

    // Start postgres
    // -k = unix_socket_directory; Ubuntu defaults to /var/run/postgresql which is not writable
    // -h = listen_addresses (both IPv4 and IPv6 localhost)
    // TODO: Open/close a socket to find a likely available port?
    String[] args2 = {extraPath + "postgres", "-D", directoryPath,
        "-p", Integer.toString(postgresPort), "-k", "/tmp", "-h", "::1,127.0.0.1"};
    Process postgres = runtime.exec(args2);
    
    System.out.println(directoryPath);

    // Create a database
    // TODO: Wait for postgres to start in a more intelligent way
    Thread.sleep(1000);
    String[] args3 = {extraPath + "createdb", "--host=localhost", "--port=" + postgresPort, databaseName};
    Process createdb = runtime.exec(args3);
    result = createdb.waitFor();
    if (result != 0) {
      throw new RuntimeException("createdb failed? " + result);
    }

    return postgres;
  }

  @BeforeClass
  public static void setUpSuite() throws IOException, InterruptedException {
    tempFolder.create();
    postgres = createPostgres(tempFolder.getRoot().getAbsolutePath(), DBNAME);
    ManagerFactory.setDatabaseUrlForTest(getDatabaseUrl());
  }

  public static void main(String[] arguments) throws IOException, InterruptedException {
    if (arguments.length != 2) {
      System.err.println("MitroServletTest (directory to create postgres database) (database name)");
      System.err.println("  Creates a database named (database name) in (directory) and starts postgres");
      System.exit(1);
    }
    postgres = createPostgres(arguments[0], arguments[1]);
    System.out.println("Postgres running on localhost:" + postgresPort);
  }

  @AfterClass
  public static void tearDownSuite() throws InterruptedException {
    postgres.destroy();
    int code = postgres.waitFor();
    assert code == 0;

    tempFolder.delete();
  }

  @Before
  public void setUp() throws SQLException, CryptoError, MitroServletException {
    managerFactory = new ManagerFactory(getDatabaseUrl(), new Manager.Pool(),
        ManagerFactory.IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS, ConnectionMode.READ_WRITE);
    manager = managerFactory.newManager();
    manager.identityDao.delete(manager.identityDao.deleteBuilder().prepare());
    manager.userNameDao.delete(manager.userNameDao.deleteBuilder().prepare());

    // Create an identity with a key
    DBIdentity id = new DBIdentity();
    id.setName(IDENTITY);
    PrivateKeyInterface key = KeyInterfacesTest.loadTestKey();
    id.setEncryptedPrivateKeyString(key.toString());
    PublicKeyInterface publicKey = key.exportPublicKey();
    id.setPublicKeyString(publicKey.toString());
    DBIdentity.createUserInDb(manager, id);

    // Create a "valid" device id
    GetMyDeviceKey.maybeGetOrCreateDeviceKey(manager, id, DEVICE_ID, false, "UNKNOWN");
    manager.commitTransaction();
  }

  @After
  public void tearDown() {
    manager.close();
  }

  public SignedRequest createValidRequest(RPC.MitroRPC request, MitroRPC rpcWithTransactionInfo) throws CryptoError, SQLException {
    SignedRequest sr = createValidRequest(request);
    sr.transactionId = rpcWithTransactionInfo.transactionId;
    return sr;
  }

  public SignedRequest createValidRequest(RPC.MitroRPC request) throws CryptoError, SQLException {
    request.deviceId = DEVICE_ID;

    SignedRequest signed = new SignedRequest();
    signed.identity = IDENTITY;
    signed.request = gson.toJson(request);

    PrivateKeyInterface key = KeyInterfacesTest.loadTestKey();
    signed.signature = key.sign(signed.request);
    return signed;
  }

  public String makeRequest(MitroServlet servlet, SignedRequest requestMessage)
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestBody(gson.toJson(requestMessage).getBytes("UTF-8"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    servlet.doPost(request, response);
    return response.getOutput();
  }

  public static class ReadIdentityServlet extends MitroServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    protected MitroRPC processCommand(MitroRequestContext parameterObject)
        throws IOException, SQLException, MitroServletException {
      // TODO Auto-generated method stub
      // begin transaction
      parameterObject.manager.identityDao.queryForAll();
      return new MitroRPC();
    }
  }

  @Test(timeout=2000)
  public void testTransactions() throws Exception {
    final CountDownLatch completeTransaction = new CountDownLatch(1);
    final CountDownLatch completeTransaction2 = new CountDownLatch(1);
    //private CountDownLatch synchrno;
    //final SynchronousQueue<Integer> groupIdQueue = new SynchronousQueue<Integer>();
    Thread t = new Thread(new Runnable() {

      public void run() {
        try {
          completeTransaction2.await();
          // mess with the db
          DBIdentity i = new DBIdentity();
          i.setName("crazyuser");
          try (Manager m = managerFactory.newManager()) {
            m.identityDao.queryForAll();
            DBIdentity.createUserInDb(m, i);
            m.commitTransaction();
          }
        } catch (InterruptedException | SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        completeTransaction.countDown();
      }
    });
    t.start();

    BeginTransactionRequest request = new BeginTransactionRequest();
    Gson gson = new Gson();
    SignedRequest requestMessage;
    requestMessage = createValidRequest(request);
    String beginxaction = makeRequest(new BeginTransactionServlet(), requestMessage);
    MitroRPC rpcWithTransaction = gson.fromJson(beginxaction, MitroRPC.class);
    makeRequest(new ReadIdentityServlet(), createValidRequest(new RPC.MitroRPC(), rpcWithTransaction));
    // now wait for a signal to go
    completeTransaction2.countDown();
    completeTransaction.await();

    final ArrayList<SQLException> exceptionHolder = new ArrayList<SQLException>();
    MitroServlet m = new MitroServlet() {

      private static final long serialVersionUID = 1L;

      @Override
      protected MitroRPC processCommand(MitroRequestContext parameterObject)
          throws IOException, SQLException, MitroServletException {
        DBGroup g = new DBGroup();
        g.setName("groupname222");
        g.setPublicKeyString("pubstr");
        parameterObject.manager.groupDao.create(g);
        DBIdentity i = new DBIdentity();
        i.setEncryptedPrivateKeyString("pk");
        i.setPublicKeyString("pk");
        i.setName("moooo");
        try {
          // this is designed to throw a serializability exception
          DBIdentity.createUserInDb(parameterObject.manager, i);
        } catch (SQLException e) {
          exceptionHolder.add(e);
          throw new IllegalStateException("hello", e);
        }
        return new MitroRPC();
      }
    };

    // do a request that does a write: it must fail due to serializability
    String response = makeRequest(m, createValidRequest(new RPC.MitroRPC(), rpcWithTransaction));
    RPC.MitroException errs = gson.fromJson(response, RPC.MitroException.class);
    
    // This exception is now hidden from the client due to security concerns. 
    //assertThat(errs.userVisibleError, containsString("Unable to run insert"));
    assertThat(errs.userVisibleError, containsString("Please retry"));
    assertEquals("RetryTransactionException", errs.exceptionType);

    assertThat(exceptionHolder.get(0).getCause().getMessage(), containsString("pivot"));
  }

  private static final class DoNothingServlet extends MitroServlet {
    private static final long serialVersionUID = 1L;
    final AtomicBoolean called = new AtomicBoolean(false);

    @Override
    protected MitroRPC processCommand(MitroRequestContext parameterObject)
        throws IOException, SQLException, MitroServletException {
      called.set(true);
      return new MitroRPC();
    }

    @Override
    protected boolean isReadOnly() {
      return false;
    }
  };

  @Test(timeout=1000)
  public void testSignatures() throws Exception {
    // call a transaction that will block
    final DoNothingServlet doNothingServlet = new DoNothingServlet();
    doNothingServlet.setRateLimiterForTest(new NullRequestRateLimiter());

    // null identity null signature
    SignedRequest requestMessage = createValidRequest(new RPC.MitroRPC());
    requestMessage.identity = null;
    requestMessage.signature = null;
    String output = makeRequest(doNothingServlet, requestMessage);
    RPC.MitroException response = gson.fromJson(output, RPC.MitroException.class);
    assertThat(response.userVisibleError, containsString("Error"));

    // null signature
    requestMessage.identity = IDENTITY;
    requestMessage.signature = null;
    output = makeRequest(doNothingServlet, requestMessage);
    response = gson.fromJson(output, RPC.MitroException.class);
    assertThat(response.userVisibleError, containsString("Error"));

    // valid identity but bad signature
    requestMessage = createValidRequest(new RPC.MitroRPC());
    requestMessage.request += " ";
    output = makeRequest(doNothingServlet, requestMessage);
    response = gson.fromJson(output, RPC.MitroException.class);
    assertThat(response.userVisibleError, containsString("Error"));

    // no such identity
    requestMessage = createValidRequest(new RPC.MitroRPC());
    requestMessage.identity = "other@example.com";
    output = makeRequest(doNothingServlet, requestMessage);
    response = gson.fromJson(output, RPC.MitroException.class);
    assertThat(response.userVisibleError, containsString("Error"));

    // valid identity and signature: successful request!
    assertFalse(doNothingServlet.called.get());
    output = makeRequest(doNothingServlet, createValidRequest(new RPC.MitroRPC()));
    MitroRPC out = gson.fromJson(output, MitroRPC.class);
    assertTrue(doNothingServlet.called.get());
    assertNull(out.transactionId);
  }

  @Test(timeout=5000)
  public void testImplicitTransactions() throws Exception {
    final DoNothingServlet doNothingServlet = new DoNothingServlet();
    SignedRequest requestMessage = createValidRequest(new RPC.MitroRPC());
    requestMessage.implicitBeginTransaction = true;
    MitroRPC response = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroRPC.class);
    assertNotNull(response.transactionId);
    String openedTransactionId = response.transactionId;
    
    requestMessage.implicitBeginTransaction = false;
    requestMessage.transactionId = openedTransactionId;
    response = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroRPC.class);
    assertNotNull(response.transactionId);
    assertEquals(openedTransactionId, response.transactionId);
    
    requestMessage.implicitEndTransaction = true;
    response = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroRPC.class);
    assertNull(response.transactionId);
  
    // this isn't allowed since the transaction has already been closed
    requestMessage.implicitEndTransaction = false;
    MitroException exceptionMsg = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroException.class);
    assertNotNull(exceptionMsg.exceptionId);
  }

  @Test(timeout=5000)
  public void testImplicitTransactionError() throws Exception {
    final DoNothingServlet doNothingServlet = new DoNothingServlet();
    SignedRequest requestMessage = createValidRequest(new RPC.MitroRPC());
    requestMessage.implicitBeginTransaction = true;
    MitroRPC response = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroRPC.class);
    assertNotNull(response.transactionId);
    String openedTransactionId = response.transactionId;
    

    // cannot request transaction open if the transaction has already been opened.
    requestMessage.transactionId = openedTransactionId;
    MitroException exceptionMsg = gson.fromJson(makeRequest(doNothingServlet, requestMessage), MitroException.class);
    assertNotNull(exceptionMsg.exceptionId);
  }
  
  @Test(timeout=1000)
  public void testIdentityByName()
      throws SQLException, IOException, MitroServletException {
    // create two groups for the secret
    DBIdentity id1 = new DBIdentity();
    id1.setName("id1");
    DBIdentity.createUserInDb(manager, id1);

    // get the object: different instances but same value
    DBIdentity id2 = DBIdentity.getIdentityForUserName(manager, id1.getName());
    assertEquals(id1, id2);
    assertTrue(id1 != id2);

    assertNull(DBIdentity.getIdentityForUserName(manager, "missing"));

    try {
      DBIdentity.getIdentityForUserName(manager, null);
      fail("expected exception");
    } catch (NullPointerException e) {}
  }

  @Test(timeout=1000)
  public void testResponseEncoding() throws Exception {
    final DoNothingServlet doNothingServlet = new DoNothingServlet();
    SignedRequest requestMessage = createValidRequest(new RPC.MitroRPC());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestBody(gson.toJson(requestMessage).getBytes("UTF-8"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    doNothingServlet.doPost(request, response);
    // it is critical that this specifies a charset of UTF-8
    assertEquals("application/json; charset=UTF-8", response.getContentType());
  }

  @Test
  public void readOnlyManagers()
      throws SQLException, IOException, MitroServletException {
    // TODO: Move this test elsewhere, but it depends on running postgres
    ManagerFactory.unsafeRecreateSingleton(ConnectionMode.READ_ONLY);

    try (Manager manager = ManagerFactory.getInstance().newManager()) {
      DBIdentity id = new DBIdentity();
      id.setName("readonly@example.com");
      try {
        DBIdentity.createUserInDb(manager, id);
        fail("expected exception");
      } catch (SQLException expected) {
        assertThat(expected.getMessage(), containsString("read-only transaction"));
      }
    }
  }

  @Test
  public void testCookieDecoder() throws UnsupportedEncodingException { 
    DecodedCookie cookie = null;
    DBIdentity id = new DBIdentity();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addCookie("bad", "this is a bad string");
    assertNull(MitroServlet.DecodedCookie.maybeMakeFromRequest(request));
    assertFalse(MitroServlet.updateIdentityWithCookies(request, id));
    assertNull(id.getGuidCookie());
    assertNull(id.getReferrer());
    id = new DBIdentity();
    request.addCookie("gauuid", "this is a bad string2");
    assertNull(MitroServlet.DecodedCookie.maybeMakeFromRequest(request));
    assertFalse(MitroServlet.updateIdentityWithCookies(request, id));
    assertNull(id.getGuidCookie());
    assertNull(id.getReferrer());

    request.clearCookies();
    request.addCookie("gauuid", "00000000-0000-0000-0000-000000000000%26ref%3Dwww.google.com");
    cookie = MitroServlet.DecodedCookie.maybeMakeFromRequest(request);
    assertEquals(cookie.guid, "00000000-0000-0000-0000-000000000000");
    assertEquals(cookie.referrer, "www.google.com");
    assertTrue(MitroServlet.updateIdentityWithCookies(request, id));
    assertEquals(id.getGuidCookie(), cookie.guid);
    assertEquals(id.getReferrer(), cookie.referrer);
    
    id = new DBIdentity();
    request.clearCookies();
    request.addCookie("gauuid", "00000000-0000-0000-0000-000000000000");
    cookie = MitroServlet.DecodedCookie.maybeMakeFromRequest(request);
    assertEquals(cookie.guid, "00000000-0000-0000-0000-000000000000");
    assertNull(cookie.referrer);
    assertTrue(MitroServlet.updateIdentityWithCookies(request, id));
    assertEquals(id.getGuidCookie(), cookie.guid);
    assertNull(id.getReferrer());

    request.clearCookies();
    request.addCookie("gauuid", "00ff0000-0000-0000-0000-000000000000%26ref%3Dwww.cnn.com");
    DecodedCookie newCookie = MitroServlet.DecodedCookie.maybeMakeFromRequest(request);
    assertEquals(newCookie.guid, "00ff0000-0000-0000-0000-000000000000");
    assertEquals(newCookie.referrer, "www.cnn.com");

    // this should not overwrite existing info in the cookie.
    assertTrue(MitroServlet.updateIdentityWithCookies(request, id));
    assertEquals(id.getGuidCookie(), cookie.guid);
    assertFalse(id.getGuidCookie().equals(newCookie.guid));
    assertEquals(id.getReferrer(), newCookie.referrer);
    
    // doing it a second time should make no updates
    assertFalse(MitroServlet.updateIdentityWithCookies(request, id));
  }
  
  

  // TODO: re-enable this test once we figure out how to handle this.
  //@Test
  public void isGroupSyncRequest() throws Exception {
    // lots of nulls!
    assertFalse(MitroServlet.isGroupSyncRequestHack(manager, null, null, null));
    assertFalse(MitroServlet.isGroupSyncRequestHack(manager, "user@example.com", null, null));
    assertFalse(MitroServlet.isGroupSyncRequestHack(manager, "user@example.com", MitroServlet.HACK_ENDPOINT, null));

    // no user
    assertFalse(MitroServlet.isGroupSyncRequestHack(
        manager, "user@example.com", MitroServlet.HACK_ENDPOINT, MitroServlet.HACK_OPERATION));

    // no groups at all for this user
    assertFalse(MitroServlet.isGroupSyncRequestHack(
        manager, IDENTITY, MitroServlet.HACK_ENDPOINT, MitroServlet.HACK_OPERATION));

    // create a group for this user: no pending groups
    DBIdentity identity = DBIdentity.getIdentityForUserName(manager, IDENTITY);
    DBGroup idGroup = MemoryDBFixture.createGroupContainingIdentityStatic(manager, identity);
    assertFalse(MitroServlet.isGroupSyncRequestHack(
        manager, IDENTITY, MitroServlet.HACK_ENDPOINT, MitroServlet.HACK_OPERATION));
    
    // create a pending group
    DBPendingGroup pendingGroup =
        new DBPendingGroup(identity, "group name", "scope", "[]", "signature", idGroup);
    manager.pendingGroupDao.create(pendingGroup);
    assertTrue(MitroServlet.isGroupSyncRequestHack(
        manager, IDENTITY, MitroServlet.HACK_ENDPOINT, MitroServlet.HACK_OPERATION));
  }
}
