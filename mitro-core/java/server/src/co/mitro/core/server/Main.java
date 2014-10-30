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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.access.servlets.ManageAccessEmailer;
import co.mitro.access.servlets.ShareAccessEmailer;
import co.mitro.core.alerts.BackgroundAlertProcessor;
import co.mitro.core.alerts.EmailAlertManager;
import co.mitro.core.alerts.FirstSecretEmailer;
import co.mitro.core.alerts.NewInactiveUserEmailer;
import co.mitro.core.alerts.NewUserEmailer;
import co.mitro.core.alerts.SecretsSharedWithUserEmailer;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyczarKeyFactory;
import co.mitro.core.crypto.PrecomputingKeyczarKeyFactory;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.OldJsonData;
import co.mitro.core.server.data.RPCLogger;
import co.mitro.core.servlets.AddGroup;
import co.mitro.core.servlets.AddIdentity;
import co.mitro.core.servlets.AddIssue;
import co.mitro.core.servlets.AddPendingGroupServlet;
import co.mitro.core.servlets.AddSecret;
import co.mitro.core.servlets.CreateCustomer;
import co.mitro.core.servlets.EditSecretContent;
import co.mitro.core.servlets.BeginTransactionServlet;
import co.mitro.core.servlets.BuildMetadataServlet;
import co.mitro.core.servlets.CheckTwoFactorRequired;
import co.mitro.core.servlets.CreateOrganization;
import co.mitro.core.servlets.DeleteGroup;
import co.mitro.core.servlets.EditEncryptedPrivateKey;
import co.mitro.core.servlets.EditGroup;
import co.mitro.core.servlets.EditSecret;
import co.mitro.core.servlets.EndTransactionServlet;
import co.mitro.core.servlets.GetAuditLog;
import co.mitro.core.servlets.GetCustomer;
import co.mitro.core.servlets.GetGroup;
import co.mitro.core.servlets.GetMyDeviceKey;
import co.mitro.core.servlets.GetMyPrivateKey;
import co.mitro.core.servlets.GetOrganizationState;
import co.mitro.core.servlets.GetPendingGroupApprovals;
import co.mitro.core.servlets.GetPublicKeyForIdentity;
import co.mitro.core.servlets.GetSecret;
import co.mitro.core.servlets.InviteNewUser;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys;
import co.mitro.core.servlets.MitroServlet;
import co.mitro.core.servlets.MutateOrganization;
import co.mitro.core.servlets.RecordEmail;
import co.mitro.core.servlets.RemoveAllPendingGroupApprovalsForScope;
import co.mitro.core.servlets.RemoveSecret;
import co.mitro.core.servlets.ServerRejectsServlet;
import co.mitro.core.servlets.SubmitPayment;
import co.mitro.core.servlets.TestServlet;
import co.mitro.core.servlets.UpdateSecretFromAgent;
import co.mitro.core.servlets.VerifyAccountServlet;
import co.mitro.core.servlets.VerifyDeviceServlet;
import co.mitro.core.servlets.WebSignupServlet;
import co.mitro.core.util.NullRequestRateLimiter;
import co.mitro.metrics.BatchStatsDClient;
import co.mitro.metrics.StatsDReporter;
import co.mitro.metrics.UDPBatchStatsDClient;
import co.mitro.twofactor.NewUser;
import co.mitro.twofactor.QRGenerator;
import co.mitro.twofactor.TFAPreferences;
import co.mitro.twofactor.TempVerify;
import co.mitro.twofactor.TwoFactorAuth;
import co.mitro.twofactor.TwoFactorSigningService;
import co.mitro.twofactor.Verify;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.codahale.metrics.jetty9.InstrumentedConnectionFactory;
import com.codahale.metrics.jetty9.InstrumentedHandler;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

public class Main {
  public static int HTTP_PORT = Integer.parseInt(System.getProperty("http_port", "8080"));
  public static int HTTPS_PORT = Integer.parseInt(System.getProperty("https_port", "8443"));
  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  /** Path that server-specific secrets will be loaded from. */
  private static final String PRODUCTION_SECRETS_PATH = "mitrocore_secrets";

  public static void registerServlet(ServletContextHandler context, HttpServlet servlet) {
    String[] patterns = getServletPatterns(servlet.getClass());
    if (patterns == null) {
      throw new IllegalArgumentException("No WebServlet annotation for type " +
          servlet.getClass().getName());
    }

    for (String pattern : patterns) {
      context.addServlet(new ServletHolder(servlet), pattern);
    }
  }

  /** Returns the patterns on the servlet's @WebServlet annotation, or null if it doesn't exist. */
  // TODO: There must be a better home for this?
  public static String[] getServletPatterns(Class<? extends HttpServlet> servletClass) {
    WebServlet info = servletClass.getAnnotation(WebServlet.class);
    if (info == null) {
      return null;
    }

    // TODO: Accept urlPatterns or value, but not both, as per Javadoc
    assert info.urlPatterns().length == 0;
    return info.value();
  }

  /** Stops a server if an error occurs during startup. */
  public static class StartupErrorStopper implements LifeCycle.Listener {
    private final Server server;

    public StartupErrorStopper(Server server) {
      this.server = server;
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
      try {
        server.stop();
      } catch (Exception e) {
        Log.getLogger(Main.class).warn("Exception thrown while shutting down after failure ", e);
      }
    }

    @Override
    public void lifeCycleStarted(LifeCycle event) {
    }

    @Override
    public void lifeCycleStarting(LifeCycle event) {
    }

    @Override
    public void lifeCycleStopped(LifeCycle event) {
    }

    @Override
    public void lifeCycleStopping(LifeCycle event) {
    }
  }

  /** Starts/stops StatsDReporter with a Jetty server. */
  public static class StatsDReporterLifecycle implements LifeCycle.Listener {
    private final StatsDReporter reporter;
    private final long period;
    private final TimeUnit unit;

    public StatsDReporterLifecycle(StatsDReporter reporter, long period, TimeUnit unit) {
      this.reporter = reporter;
      this.period = period;
      this.unit = unit;
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
    }

    @Override
    public void lifeCycleStarted(LifeCycle event) {
    }

    @Override
    public void lifeCycleStarting(LifeCycle event) {
      reporter.start(period, unit);
    }

    @Override
    public void lifeCycleStopped(LifeCycle event) {
    }

    @Override
    public void lifeCycleStopping(LifeCycle event) {
      reporter.stop();
    }
  }

  // use a List instead of an array: generic arrays don't work?
  private static final List<Class<? extends HttpServlet>> SERVLETS = Arrays.asList(
    AddGroup.class, GetSecret.class, AddIdentity.class, AddSecret.class, EditGroup.class,
    GetGroup.class, GetMyPrivateKey.class, GetPublicKeyForIdentity.class,
    EditEncryptedPrivateKey.class, EditSecret.class, ListMySecretsAndGroupKeys.class, 
    GetOrganizationState.class, RemoveSecret.class,
    TestServlet.class, BeginTransactionServlet.class, EndTransactionServlet.class, AddIssue.class,
    GetPendingGroupApprovals.class, AddPendingGroupServlet.class,
    RemoveAllPendingGroupApprovalsForScope.class, BuildMetadataServlet.class, DeleteGroup.class,
    VerifyAccountServlet.class, TempVerify.class, Verify.class, NewUser.class,
    TwoFactorAuth.class, QRGenerator.class, GetAuditLog.class, TFAPreferences.class,
    InviteNewUser.class, GetMyDeviceKey.class, WebSignupServlet.class, VerifyDeviceServlet.class,
    ServerRejectsServlet.class, CreateOrganization.class, MutateOrganization.class,
    CheckTwoFactorRequired.class, RecordEmail.class, EditSecretContent.class,
    CreateCustomer.class, GetCustomer.class, SubmitPayment.class, ShareAccessEmailer.class, 
    ManageAccessEmailer.class, UpdateSecretFromAgent.class);

  public static void exitIfAssertionsDisabled() {
    try {
      assert false;
      System.err.println("Assertions must be enabled; rerun with -ea flag");
      System.exit(1);
    } catch (AssertionError ignored) {
      // assertions are enabled: we are happy
    }
  }

  public static void main(String[] args) throws Exception {
    exitIfAssertionsDisabled();

    // TODO: Create a new ManagerFactory to specify the database to use
    // This is a HUGE HACK to inject the database location. REMOVE
    String databaseProperty = System.getProperty("dburl");
    if (databaseProperty != null) {
      ManagerFactory.setDatabaseUrlForTest(databaseProperty);
    }

    // TODO: make this way less ugly.
    boolean databaseReadOnly = false;
    if (args.length > 0) {
      if (args[0].equals("--readonly")) {
        databaseReadOnly = true;
        logger.info("Setting database connections to read-only mode");
        ManagerFactory.unsafeRecreateSingleton(ManagerFactory.ConnectionMode.READ_ONLY);
      }
    }

    // Load the secrets bundle: TODO: Inject into instances that need this
    final String GENERATE_PROPERTY_NAME = "generateSecretsForTest";
    String propertyValue = System.getProperty(GENERATE_PROPERTY_NAME);
    SecretsBundle secrets;
    if (propertyValue != null) {
      if (propertyValue.equals("true")) {
        logger.warn("Generating random secrets for testing; should not happen in production");
        secrets = SecretsBundle.generateForTest();
      } else {
        throw new RuntimeException(
            "Invalid value for system property " + GENERATE_PROPERTY_NAME + "=" + propertyValue);
      }
    } else {
      secrets = new SecretsBundle(PRODUCTION_SECRETS_PATH);
    }
    TwoFactorSigningService.initialize(secrets);

    ManagerFactory managerFactory = ManagerFactory.getInstance();
    KeyczarKeyFactory keyFactory = new PrecomputingKeyczarKeyFactory();

    // TODO: Remove this hack for a proper debug flag?
    boolean disableRateLimits = Objects.equals(System.getProperty("disableRateLimits"), "true");
    if (disableRateLimits) {
      logger.warn("Disabling rate limits for testing purposes");
    }

    // Create servlet instances
    List<HttpServlet> servlets = Lists.newArrayList();
    for (Class<? extends HttpServlet> type : SERVLETS) {
      HttpServlet servlet = null;
      try {
        servlet = type.getConstructor(ManagerFactory.class, KeyFactory.class)
            .newInstance(managerFactory, keyFactory);
      } catch (NoSuchMethodException ignored) {
         servlet = type.getConstructor().newInstance();
      }
      if (disableRateLimits && servlet instanceof MitroServlet) {
        ((MitroServlet) servlet).setRateLimiterForTest(new NullRequestRateLimiter());
      }
      servlets.add(servlet);
    }

    // optionally log all RPCs (useful for debugging)
    String logfilePrefix = System.getProperty("rpclogfile");
    if (!Strings.isNullOrEmpty(logfilePrefix)) {
      RPCLogger.startLoggingWithPrefix(logfilePrefix);
    }

    String disableVerification = System.getProperty("disableEmailVerification");
    if (!Strings.isNullOrEmpty(disableVerification)) {
      logger.warn("Disabling email verification; value: {}", disableVerification);
      if ("true".equals(disableVerification)) {
        AddIdentity.defaultVerifiedState = true;
        DBIdentity.kUseEmailAsVerificationForTestsOnly = true;
        GetMyPrivateKey.doEmailVerification = false;
      } else if ("test".equals(disableVerification)) {
        // this is required for the regression test. It's really hard to query postgres from JS, so
        // we use a fake verification code to test email verification
        AddIdentity.defaultVerifiedState = false;
        DBIdentity.kUseEmailAsVerificationForTestsOnly = true;
      } else {
        throw new RuntimeException("invalid flag for 'disableVerification' : " + disableVerification);
      }
    }

    
    String emergencyRejectRequests = System.getProperty("emergencyRejectTrafficFrac");
    if (emergencyRejectRequests != null) {
      double d = Double.parseDouble(emergencyRejectRequests);
      MitroServlet.setPercentReadRequestsToRejectForUseOnlyInEmergencies(d);
    }
    MetricRegistry metrics = new MetricRegistry();
    HealthCheckRegistry healthChecks = new HealthCheckRegistry();

    // Register all servlets, using the path specified by @WebServlet
    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/mitro-core");
    for (HttpServlet servlet : servlets) {
      registerServlet(context, servlet);
    }

    // Export metrics and health checks using the admin servlet
    context.setAttribute(MetricsServlet.METRICS_REGISTRY, metrics);
    context.setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, healthChecks);
    context.addServlet(new ServletHolder(new AdminServlet()), "/admin/*");


    // Add the deadlock health check and built-in JVM metrics
    healthChecks.register("deadlock", new ThreadDeadlockHealthCheck());
    metrics.register("jvm.gc", new GarbageCollectorMetricSet());
    metrics.register("jvm.mem", new MemoryUsageGaugeSet());

    // Report statistics to StatsD (datadog)
    // datadog reports once every 10 seconds, so no point in us doing anything more
    BatchStatsDClient statsd = new UDPBatchStatsDClient("localhost", 8125);
    StatsDReporter reporter = StatsDReporter.forRegistry(metrics).prefixedWith("mitrocore")
        .build(statsd);
    StatsDReporterLifecycle reporterLifecycle =
        new StatsDReporterLifecycle(reporter, 10, TimeUnit.SECONDS);

    // Log requests using SLF4J at INFO; Use X-Forwarded-For as source IP
    RequestLogHandler requestLogHandler = new RequestLogHandler();
    Slf4jRequestLog requestLog = new Slf4jRequestLog();
    requestLog.setPreferProxiedForAddress(true);
    requestLog.setExtended(true);
    requestLog.setLogTimeZone("UTC");
    requestLogHandler.setRequestLog(requestLog);

    // Install both the servlet handler and the logging handler
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[]{context, requestLogHandler});

    OldJsonData ojd = OldJsonData.createFromStream(
        Main.class.getResourceAsStream("service_list.json"));
    Manager.setOldJsonData(ojd);
    
    loadDefaultServerHints();

    // Create and start the server
    // TODO: Use new InstrumentedQueuedThreadPool(metrics) to instrument Jetty threads?
    final Server server = createJetty(metrics, handlers);

    // Stop if an error occurs on startup (e.g. BindException due to reusing a port)
    StartupErrorStopper stopper = new StartupErrorStopper(server);
    server.addLifeCycleListener(stopper);
    // report statsd
    server.addLifeCycleListener(reporterLifecycle);
    // clean up idle transactions
    server.addLifeCycleListener(managerFactory);

    // start up background alert processing
    if (!databaseReadOnly) {
      BackgroundAlertProcessor.startBackgroundService(managerFactory);
    } else {
      logger.info("Database read-only: Not starting BackgroundAlertProcessor (running on secondary?)");
    }
    
    // initialize email alerters.
    EmailAlertManager.getInstance().registerAlerter(new FirstSecretEmailer());
    EmailAlertManager.getInstance().registerAlerter(new NewUserEmailer());
    EmailAlertManager.getInstance().registerAlerter(new NewInactiveUserEmailer());
    EmailAlertManager.getInstance().registerAlerter(new SecretsSharedWithUserEmailer());
    
    
    server.start();
    server.join();
  }

  /** Loads site overrides from server_hints.json, validating them. */
  static void loadDefaultServerHints() throws IOException {
    try (InputStream stream = Main.class.getResourceAsStream("server_hints.json")) {
      String content = CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8));
      ServerRejectsServlet.setServerHintsJson(content);
    }
  }

  /** Creates a Jetty server listening on HTTP and HTTPS, serving handlers. */
  public static Server createJetty(MetricRegistry metrics, Handler handler)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
    // TODO: Use new InstrumentedQueuedThreadPool(metrics) to instrument Jetty threads?
    final Server server = new Server();
    // TODO: Instrument each handler individually for finer grained metrics
    InstrumentedHandler instrumented = new InstrumentedHandler(metrics);
    instrumented.setHandler(handler);
    server.setHandler(instrumented);

    HttpConfiguration httpConfig = new HttpConfiguration();
    // Parses X-Forwarded-For headers for Servlet.getRemoteAddr()
    httpConfig.addCustomizer(new ForwardedRequestCustomizer());
    // Collect statistics from the server
    final ServerConnector connector = new ServerConnector(server,
        new InstrumentedConnectionFactory(new HttpConnectionFactory(httpConfig),
            metrics.timer("http.connections")));
    server.addConnector(connector);

    connector.setPort(HTTP_PORT);

    // Enable SSL on port 8443 using the debug keystore
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    InputStream keyStream = Main.class.getResourceAsStream("debug_keystore.jks");
    keyStore.load(keyStream, null);
    keyStream.close();

    SslContextFactory ssl = new SslContextFactory();
    ssl.setKeyStore(keyStore);
    ssl.setKeyStorePassword("password");
    SslConnectionFactory sslFactory = new SslConnectionFactory(ssl, "http/1.1");
    // SecureRequestCustomizer is required to correctly set scheme to https
    HttpConfiguration httpsConfig = new HttpConfiguration();
    httpsConfig.addCustomizer(new SecureRequestCustomizer());
    httpsConfig.addCustomizer(new ForwardedRequestCustomizer());
    ConnectionFactory httpsFactory = new InstrumentedConnectionFactory(new HttpConnectionFactory(httpsConfig),
        metrics.timer("https.connections"));

    ServerConnector sslConnector = new ServerConnector(server, sslFactory, httpsFactory);
    sslConnector.setPort(HTTPS_PORT);
    server.addConnector(sslConnector);

    registerShutdownHook(server);
    return server;
  }

  protected static void registerShutdownHook(final Server server) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Process terminating; stopping Jetty");
        try {
          server.stop();
        } catch (Exception e) {
          logger.error("error stopping Jetty", e);
        }

        // TODO: Make this a Jetty LifeCycle so its part of server.stop()?
        try {
          RPCLogger.stopLogging();
        } catch (Exception e) {
          logger.error("error stopping RPCLogger", e);
        }
      }
    });
  }
}
