/**
 *
 *
 */


package com.dantin.omnilinkiot;

import com.digitaldan.jomnilinkII.MessageTypes.statuses.*;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.UPBLinkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitaldan.jomnilinkII.Connection;
import com.digitaldan.jomnilinkII.DisconnectListener;
import com.digitaldan.jomnilinkII.Message;
import com.digitaldan.jomnilinkII.NotificationListener;
import com.digitaldan.jomnilinkII.OmniInvalidResponseException;
import com.digitaldan.jomnilinkII.OmniNotConnectedException;
import com.digitaldan.jomnilinkII.OmniUnknownMessageTypeException;
import com.digitaldan.jomnilinkII.MessageTypes.AudioSourceStatus;
import com.digitaldan.jomnilinkII.MessageTypes.EventLogData;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectProperties;
import com.digitaldan.jomnilinkII.MessageTypes.ObjectStatus;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.ButtonEvent;
import com.digitaldan.jomnilinkII.MessageTypes.systemevents.SystemEvent;
import com.digitaldan.jomnilinkII.MessageTypes.CommandMessage;
import com.digitaldan.jomnilinkII.MessageUtils;

import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.CrtResource;
import software.amazon.awssdk.crt.CrtRuntimeException;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.HostResolver;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;
import software.amazon.awssdk.iot.iotjobs.model.RejectedError;
import software.amazon.awssdk.iot.discovery.DiscoveryClient;
import software.amazon.awssdk.iot.discovery.DiscoveryClientConfig;
import software.amazon.awssdk.iot.discovery.model.ConnectivityInfo;
import software.amazon.awssdk.iot.discovery.model.DiscoverResponse;
import software.amazon.awssdk.iot.discovery.model.GGCore;
import software.amazon.awssdk.iot.discovery.model.GGGroup;

import org.json.simple.JSONObject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.util.*;
import java.nio.charset.StandardCharsets;

import static software.amazon.awssdk.iot.discovery.DiscoveryClient.TLS_EXT_ALPN;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static String host;
    static int port;
    static String key;


    static String clientId;
    static String thingName;
    static String rootCaPath;
    static String certPath;
    static String keyPath;
    static String endpoint;

    static String topic = "hai/#";

    static String region = "us-east-1";

    static int[] numDevices = new int[5];


    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                logger.info("Usage:com.dantin.omnilinkiot.Main config.properties");
                System.exit(-1);
            }

            try (InputStream input = new FileInputStream(args[0])) {

                Properties prop = new Properties();

                // load a properties file
                prop.load(input);

                // get the property value
                host = prop.getProperty("host");
                port = Integer.parseInt(prop.getProperty("port"));
                key = prop.getProperty("key");

                endpoint = prop.getProperty("clientEndpoint");
                clientId = prop.getProperty("clientId");
                thingName = prop.getProperty("thingName");
                certPath = prop.getProperty("certificateFile");
                keyPath = prop.getProperty("privateKeyFile");
                rootCaPath = prop.getProperty("rootCaFile");
                region = prop.getProperty("region");

                String[] numDevicesArray = prop.getProperty("numDevices").split(",");
                for(int i = 0; i < numDevicesArray.length; i++) {
                    numDevices[i] = Integer.parseInt(numDevicesArray[i]);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
                throw new RuntimeException("Missing or error in required config parameter");
            }


            try(final EventLoopGroup eventLoopGroup = new EventLoopGroup(1);
                final HostResolver resolver = new HostResolver(eventLoopGroup);
                final ClientBootstrap clientBootstrap = new ClientBootstrap(eventLoopGroup, resolver);
                final TlsContextOptions tlsCtxOptions = TlsContextOptions.createWithMtlsFromPath(certPath, keyPath)) {

                if(TlsContextOptions.isAlpnSupported()) {
                    tlsCtxOptions.withAlpnList(TLS_EXT_ALPN);
                }

                if(rootCaPath != null) {
                    tlsCtxOptions.overrideDefaultTrustStoreFromPath(null, rootCaPath);
                }

                try(final DiscoveryClientConfig discoveryClientConfig =
                            new DiscoveryClientConfig(clientBootstrap, tlsCtxOptions,
                            new SocketOptions(), region, 1, null);
                        final DiscoveryClient discoveryClient = new DiscoveryClient(discoveryClientConfig);
                        final MqttClientConnection connection = getClientFromDiscovery(discoveryClient, clientBootstrap)) {

                    logger.info("Greengrass Connected");

                    Message m;
                    Connection c = new Connection(host, port, key);
                    c.debug = true;

                    NotificationProcessor notifProcessor = new NotificationProcessor(connection);

                    c.addNotificationListener(notifProcessor);

                    c.addDisconnectListener(new DisconnectListener() {
                        @Override
                        public void notConnectedEvent(Exception e) {
                            e.printStackTrace();
                            System.exit(-1);
                        }
                    });

                    c.enableNotifications();
                    logger.info(c.reqSystemInformation().toString());
                    logger.info(c.reqSystemStatus().toString());
                    logger.info(c.reqSystemTroubles().toString());
                    logger.info(c.reqSystemFormats().toString());
                    logger.info(c.reqSystemFeatures().toString());
                    logger.info("All Done, OmniConnection thread now running");

                    CompletableFuture<Integer> published = connection.publish(new MqttMessage("hai/", "connected".getBytes()), QualityOfService.AT_MOST_ONCE, false);
                    published.get();

                    MessageHandler mHandler = new MessageHandler(c);

                    final CompletableFuture<Integer> subFuture = connection.subscribe("hai/+/+/#", QualityOfService.AT_MOST_ONCE, message -> {
                        mHandler.process(message);
                    });

                    AllStatusRunner allstatus = new AllStatusRunner(c, notifProcessor, numDevices);

                    final CompletableFuture<Integer> subFuture2 = connection.subscribe("hai/allstatus", QualityOfService.AT_MOST_ONCE, message -> {
                        Thread allStatusThread = new Thread(allstatus);
                        allStatusThread.start();
                    });

                    while (true) {}
                    // CompletableFuture<Void> disconnected = connection.disconnect();
                    // disconnected.get();
                }
            } catch (CrtRuntimeException | InterruptedException | ExecutionException ex) {
                System.out.println("Exception thrown: " + ex.toString());
                ex.printStackTrace();
            } catch (OmniInvalidResponseException e) {
                logger.error("Invalid Response", e);
                System.exit(-1);
            } catch (OmniNotConnectedException e) {
                logger.error("Error connecting", e);
                System.exit(-1);
            } catch (Exception e) {
                logger.error("Error", e);
                e.printStackTrace();
                System.exit(-1);
            }


            CrtResource.waitForNoResources();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        System.out.println("Complete!");
    }

    static void onRejectedError(RejectedError error) {
        System.out.println("Request rejected: " + error.code.toString() + ": " + error.message);
    }

    private static Pattern PATTERN_IS_PRIVATE_IP = Pattern.compile("/(^127\\.)|(^192\\.168\\.)|(^10\\.)|(^172\\.1[6-9]\\.)|(^172\\.2[0-9]\\.)|(^172\\.3[0-1]\\.)|(^::1$)|(^[fF][cCdD])/");


    private static MqttClientConnection getClientFromDiscovery(final DiscoveryClient discoveryClient,
                                                               final ClientBootstrap bootstrap) throws ExecutionException, InterruptedException {
        logger.info("discover");
        final CompletableFuture<DiscoverResponse> futureResponse = discoveryClient.discover(thingName);
        final DiscoverResponse response = futureResponse.get();
        logger.info("got response");
        if(response.getGGGroups() != null) {
            final Optional<GGGroup> groupOpt = response.getGGGroups().stream().findFirst();
            if(groupOpt.isPresent()) {
                final GGGroup group = groupOpt.get();
                final GGCore core = group.getCores().stream().findFirst().get();
                final SortedSet<ConnectivityInfo> prioritizedConnectivity = new TreeSet(new Comparator<ConnectivityInfo>() {
                    @Override
                    public int compare(ConnectivityInfo lhs, ConnectivityInfo rhs) {
                        return ordinalValue(lhs) - ordinalValue(rhs);
                    }
                    private int ordinalValue(ConnectivityInfo info) {
                        if(info.getHostAddress().equals("127.0.0.1") || info.getHostAddress().equals("::1")) {
                            return 0;
                        }
                        if(PATTERN_IS_PRIVATE_IP.matcher(info.getHostAddress()).matches()) {
                            return 1;
                        }
                        if(info.getHostAddress().startsWith("AUTOIP_")) {
                            return 10;
                        }
                        return 2;
                    }
                });

                prioritizedConnectivity.addAll(core.getConnectivity());

                Iterator<ConnectivityInfo> it = prioritizedConnectivity.iterator();
                while (it.hasNext()) {
                    ConnectivityInfo selectedConnectivity = it.next();
                    final String dnsOrIp = selectedConnectivity.getHostAddress();
                    final Integer port = selectedConnectivity.getPortNumber();

                    System.out.println(String.format("Connecting to group ID %s, with thing arn %s, using endpoint %s:%d",
                            group.getGGGroupId(), core.getThingArn(), dnsOrIp, port));

                    MqttClientConnectionEvents callbacks = new MqttClientConnectionEvents() {
                        @Override
                        public void onConnectionInterrupted(int errorCode) {
                            if (errorCode != 0) {
                                System.out.println("Connection interrupted: " + errorCode + ": " + CRT.awsErrorString(errorCode));
                            }
                        }

                        @Override
                        public void onConnectionResumed(boolean sessionPresent) {
                            System.out.println("Connection resumed: " + (sessionPresent ? "existing session" : "clean session"));
                        }
                    };

                    final AwsIotMqttConnectionBuilder connectionBuilder = AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(certPath, keyPath)
                            .withClientId(clientId)
                            .withPort(port.shortValue())
                            .withEndpoint(dnsOrIp)
                            .withBootstrap(bootstrap)
                            .withConnectionEventCallbacks(callbacks);


                    if(group.getCAs() != null) {
                        connectionBuilder.withCertificateAuthority(group.getCAs().get(0));
                    }

                    MqttClientConnection connection = connectionBuilder.build();
                    try {
                        connection.connect().get();
                        return connection;
                    } catch (ExecutionException ex) {
                        // probably can't connect, try the next ip
                        System.out.println("Exception thrown: " + ex.toString());
                    }
                }
            }
        }
        throw new RuntimeException("ThingName " + thingName + " does not have a Greengrass group/core configuration");
    }
}
