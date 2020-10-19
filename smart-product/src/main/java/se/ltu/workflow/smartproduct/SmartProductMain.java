package se.ltu.workflow.smartproduct;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.ArServiceCache;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.HttpJsonCloudPlugin;
import se.arkalix.core.plugin.or.OrchestrationStrategy;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.security.identity.OwnedIdentity;
import se.arkalix.security.identity.TrustStore;
import se.ltu.workflow.smartproduct.arrowhead.AFCoreSystems;
import se.ltu.workflow.smartproduct.properties.TypeSafeProperties;

public class SmartProductMain {
    
 //------------------------------------------------------------------------
    
    private static final Logger logger = LoggerFactory.getLogger(SmartProductMain.class);
    
    private static final TypeSafeProperties props = TypeSafeProperties.getProp();
    
    static {
        final var logLevelRoot = Level.INFO;
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
        final var root = java.util.logging.Logger.getLogger("");
        root.setLevel(logLevelRoot);
        
        // Logger not working yet
        final var logLevelKalix = Level.ALL;
        final var kalix = java.util.logging.Logger.getLogger("se.arkalix");
        kalix.setLevel(logLevelKalix);
        
        for (final var handler : root.getHandlers()) {
            handler.setLevel(Level.ALL);
        }
    }
    
    public static void main(String[] args) {
        logger.info("Productive 4.0 Manufacturing Workflow Demonstrator - Smart Product System");
        
        // Working directory should always contain a properties file and certificates!
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        try {
            // Retrieve properties to set up keystore and truststore
            // The paths must start at the working directory
            final char[] pKeyPassword = props.getProperty("server.ssl.key-password", "123456")
                    .toCharArray();
            final char[] kStorePassword = props.getProperty("server.ssl.key-store-password", "123456").
                    toCharArray();
            final String kStorePath = props.getProperty("server.ssl.key-store", "certificates/smart_product.p12");
            final char[] tStorePassword = props.getProperty("server.ssl.trust-store-password", "123456")
                    .toCharArray();
            final String tStorePath = props.getProperty("server.ssl.trust-store", "certificates/truststore.p12");
            
            // Load properties for system identity and truststore
            final var identity = new OwnedIdentity.Loader()
                    .keyStorePath(kStorePath)
                    .keyStorePassword(kStorePassword)
                    .keyPassword(pKeyPassword)
                    .load();
            final var trustStore = TrustStore.read(tStorePath, tStorePassword);
            
            /* Remove variables storing passwords, as they are final they can not be unreferenced and 
             * will not be garbage collected
             */
            Arrays.fill(pKeyPassword, 'x');
            Arrays.fill(kStorePassword, 'x');
            Arrays.fill(tStorePassword, 'x');
            
            /* Create client to send HTTPRequest, but only to non Arrowhead services! Is recommended to
             * use HttpConsumer when dealing with Arrowhead services
             */
            final var client = new HttpClient.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .build();
            
            /* Check that the core systems are available - This call is synchronous, as 
             * initialization should not continue if they are not succesfull
             */
            AFCoreSystems.checkCoreSystems(client,2);
            
            // Retrieve Smart product properties to create Arrowhead system
            final String systemAddress = props.getProperty("server.address", "127.0.0.1");
            final int systemPort = props.getIntProperty("server.port", 8503);
            final var systemSocketAddress = new InetSocketAddress(systemAddress, systemPort);
            
            // Retrieve Service Registry properties to register Arrowhead system
            final String serviceRegistryAddres = props.getProperty("sr_address","127.0.0.1");
            final int serviceRegistryPort = props.getIntProperty("sr_port", 8443);
            // TODO: In demo we can use "service-registry.uni" as hostname of Service Registry?
            final var srSocketAddress = new InetSocketAddress(serviceRegistryAddres, serviceRegistryPort);
            
            // Create Arrowhead system
            final var system = new ArSystem.Builder()
                    .identity(identity)
                    .trustStore(trustStore)
                    .localSocketAddress(systemSocketAddress)
                    .plugins(new HttpJsonCloudPlugin.Builder()
                            .serviceRegistrySocketAddress(srSocketAddress)
                            .orchestrationStrategy(OrchestrationStrategy.STORED_THEN_DYNAMIC)
                            .serviceRegistrationPredicate(service -> service.interfaces()
                                    .stream()
                                    .allMatch(i -> i.encoding().isDtoEncoding()))
                            .build())
                    .serviceCache(ArServiceCache.withEntryLifetimeLimit(Duration.ofHours(1)))
                    .build();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
