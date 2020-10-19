package se.ltu.workflow.smartproduct;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.PatternSyntaxException;

import javax.management.ServiceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.arkalix.ArServiceCache;
import se.arkalix.ArSystem;
import se.arkalix.core.plugin.HttpJsonCloudPlugin;
import se.arkalix.core.plugin.or.OrchestrationStrategy;
import se.arkalix.descriptor.EncodingDescriptor;
import se.arkalix.descriptor.TransportDescriptor;
import se.arkalix.dto.DtoReadException;
import se.arkalix.net.http.HttpMethod;
import se.arkalix.net.http.client.HttpClient;
import se.arkalix.net.http.consumer.HttpConsumer;
import se.arkalix.net.http.consumer.HttpConsumerRequest;
import se.arkalix.net.http.consumer.HttpConsumerResponse;
import se.arkalix.security.identity.OwnedIdentity;
import se.arkalix.security.identity.TrustStore;
import se.arkalix.util.concurrent.Future;
import se.ltu.workflow.smartproduct.arrowhead.AFCoreSystems;
import se.ltu.workflow.smartproduct.dto.DataOrderDto;
import se.ltu.workflow.smartproduct.dto.WorkflowBuilder;
import se.ltu.workflow.smartproduct.properties.TypeSafeProperties;

public class SmartProductMain {
    
    public static Long serialID;
    public static Boolean systemON = true;
    
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
            
            // Command line interface
            printIntro();
            while (systemON) {
                
                serialID = null;
                while(serialID == null) {
                    var userInput = requestUserSerialID();
                    serialID = parseSerialID(userInput);
                }
                // Consume Middleware data
                System.out.println("**     Sending request to Middleware with above serialID     ** ");
                System.out.println("**                                                           ** ");
                System.out.println("****************************************************************");
                System.out.println("**                                                           ** ");
                system.consume()
                    .name(SmartProductsConstant.MIDDLEWARE_SERVICE_DEFINTION)
                    .encodings(EncodingDescriptor.JSON)
                    .transports(TransportDescriptor.HTTP)
                    .using(HttpConsumer.factory())
                    .flatMap(consumer -> consumer.send(
                            new HttpConsumerRequest()
                                .method(HttpMethod.GET)
                                .uri(consumer.service().uri() + SmartProductsConstant.MIDDLEWARE_ORDERS_URI
                                        + serialID)))
                    .flatMap(response -> identifyOperations(response, system))
                    .flatMapCatch(ServiceNotFoundException.class,
                            exception -> {
                                logger.info("Service " + SmartProductsConstant.MIDDLEWARE_SERVICE_DEFINTION 
                                        + " not found in this local cloud");
                                return Future.done();})
                    .onFailure(throwable -> throwable.printStackTrace());
            }
            
            
        } catch (Exception e) {
            logger.error("Smart Product system could not startup, exiting application");
            e.printStackTrace();
        }
    }
    
    public static void printIntro() {
        System.out.println("****************************************************************");
        System.out.println("**                                                           ** ");
        System.out.println("**         Welcome to the Smart Product interface            ** ");
        System.out.println("**                                                           ** ");
        System.out.println("****************************************************************");

        
    }
    
    public static String requestUserSerialID() {
        System.out.println("**                                                           ** ");
        System.out.println("**     Introduce the SerialID of the product that wants      ** ");
        System.out.println("**           to be manufactured in this workstation          ** ");
        System.out.print("** SerialID -> ");
        Scanner in= new Scanner(System.in);
        return in.next();
    }
    
    private static Long parseSerialID(String serialID) {
        try {
            return Long.parseUnsignedLong(serialID.trim());
        } catch (NumberFormatException e) {
            System.out.println("** The data introduced is not a valid number, try again ");
            return null;
        }
    }

    /**
     * Retrieves the operations needed to be executed by this smart product from the 
     * ArticleID field of a DataOrderDto in the response. This operations will then be
     * send as request to a Workflow Manager that offers them.
     * 
     * @param response  The response of a Middleware system that has the DataOrder
     * @return  An empty future
     */
    private static Future<?> identifyOperations(HttpConsumerResponse response, ArSystem system){
        System.out.println("**                Parsing message received...                ** ");
        return response
            .bodyAs(DataOrderDto.class)
            .ifSuccess(dataOrder -> {
                var operationsInitials = dataOrder.articleId().strip().split("-")[2];
                if(operationsInitials.length() != 2) 
                    throw new PatternSyntaxException(
                            "The ArticleID: " + operationsInitials + "could not be parsed into an operation",
                            "-", 1);
                if (operationsInitials.contains("D")) {
                    if (operationsInitials.contains("M")) {
                        System.out.println("**             Product requires Drilling & Milling           ** ");
                        System.out.println("**                                                           ** ");
                        System.out.println("****************************************************************");
                        System.out.println("**                                                           ** ");
                        requestOperation(SmartProductsConstant.WORKFLOW_NAME_MILL_AND_DRILL, system);
                        return;
                    }
                    System.out.println("**               Product requires only Drilling              ** ");
                    System.out.println("**                                                           ** ");
                    System.out.println("****************************************************************");
                    System.out.println("**                                                           ** ");
                    requestOperation(SmartProductsConstant.WORKFLOW_NAME_DRILL, system);
                }else if (operationsInitials.contains("M")) {
                    System.out.println("**               Product requires only Milling               ** ");
                    System.out.println("**                                                           ** ");
                    System.out.println("****************************************************************");
                    System.out.println("**                                                           ** ");
                    requestOperation(SmartProductsConstant.WORKFLOW_NAME_MILL, system);
                }else {
                    System.out.println("**          Product does not require manufacturing           ** ");
                    System.out.println("**                                                           ** ");
                    System.out.println("****************************************************************");
                    System.out.println("**                                                           ** ");
                    
                }
            })
            .flatMapCatch(DtoReadException.class, exception -> {
                logger.error("Response from Middleware with wrong format,"
                        + " can not be parsed");
                return Future.done();});
    }
    
    private static void requestOperation(String operationRequired, ArSystem system) {
        system.consume()
            .name(SmartProductsConstant.WORKSTATION_OPERATIONS_SERVICE_DEFINITION)
            .encodings(EncodingDescriptor.JSON)
            .transports(TransportDescriptor.HTTP)
            .using(HttpConsumer.factory())
            .flatMap(consumer -> consumer.send(
                    new HttpConsumerRequest()
                        .method(HttpMethod.POST)
                        .uri(consumer.service().uri())
                        .body(new WorkflowBuilder()
                                .workflowName(operationRequired)
                                .build())));
    }

}
