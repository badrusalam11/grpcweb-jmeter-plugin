package com.badru.jmeter.grpcweb.sampler;

import com.google.protobuf.DynamicMessage;
import com.badru.jmeter.grpcweb.client.GrpcWebClient;
import com.badru.jmeter.grpcweb.util.ProtoFileParser;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GrpcWebSampler extends AbstractSampler implements TestStateListener {
    private static final Logger log = LoggerFactory.getLogger(GrpcWebSampler.class);
    
    // Static cache to persist across test runs and threads - THIS FIXES YOUR ISSUE!
    private static final Map<String, ProtoFileParser> PROTO_CACHE = new ConcurrentHashMap<>();
    
    // Property names for JMeter
    public static final String PROTO_FILE_PATH = "GrpcWebSampler.protoFilePath";
    public static final String SERVER_URL = "GrpcWebSampler.serverUrl";
    public static final String SERVICE_NAME = "GrpcWebSampler.serviceName";
    public static final String METHOD_NAME = "GrpcWebSampler.methodName";
    public static final String REQUEST_JSON = "GrpcWebSampler.requestJson";
    public static final String TIMEOUT_SECONDS = "GrpcWebSampler.timeoutSeconds";
    public static final String USE_TEXT_FORMAT = "GrpcWebSampler.useTextFormat";
    public static final String CUSTOM_HEADERS = "GrpcWebSampler.customHeaders";
    
    // Store parsed proto info as properties (will be saved with test plan)
    public static final String PARSED_SERVICES = "GrpcWebSampler.parsedServices";
    public static final String PARSED_PACKAGE = "GrpcWebSampler.parsedPackage";
    
    private transient GrpcWebClient grpcClient;
    
    public GrpcWebSampler() {
        super();
        setName("gRPC-Web Request");
    }
    
    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);
        
        try {
            // Initialize if needed
            initializeIfNeeded();
            
            // Get or create proto parser - THIS FIXES THE EMPTY STATE ISSUE!
            ProtoFileParser parser = getOrCreateProtoParser();
            
            if (parser == null) {
                throw new RuntimeException("Proto file not parsed. Please parse the proto file first in the GUI.");
            }
            
            // Prepare request
            GrpcWebClient.GrpcWebRequest request = prepareRequest(parser);
            
            // Start timing
            result.sampleStart();
            
            // Execute request
            GrpcWebClient.GrpcWebResponse response = grpcClient.executeRequest(request);
            
            // Stop timing
            result.sampleEnd();
            
            // Process response
            processResponse(result, response);
            
        } catch (Exception e) {
            log.error("Error executing gRPC-Web request", e);
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("500");
            result.setResponseMessage("Internal Error: " + e.getMessage());
            result.setResponseData(e.getMessage(), "UTF-8");
        }
        
        return result;
    }
    
    /**
     * Get or create proto parser from cache - THIS IS THE KEY FIX!
     */
    private ProtoFileParser getOrCreateProtoParser() {
        String protoPath = getProtoFilePath();
        if (protoPath == null || protoPath.trim().isEmpty()) {
            return null;
        }
        
        // Check cache first - this prevents the "empty state" issue
        ProtoFileParser parser = PROTO_CACHE.get(protoPath);
        if (parser != null) {
            log.debug("Using cached proto parser for: " + protoPath);
            return parser;
        }
        
        // Try to parse if not in cache
        try {
            parser = new ProtoFileParser();
            parser.parseProtoFile(protoPath);
            
            // Cache for future use - this is why it stays persistent!
            PROTO_CACHE.put(protoPath, parser);
            
            // Store parsed info in properties for persistence
            storeParsedProtoInfo(parser);
            
            log.info("Proto file parsed and cached: " + protoPath);
            return parser;
            
        } catch (Exception e) {
            log.error("Failed to parse proto file: " + protoPath, e);
            return null;
        }
    }
    
    /**
     * Store parsed proto information in JMeter properties for persistence
     */
    private void storeParsedProtoInfo(ProtoFileParser parser) {
        try {
            // Store services as comma-separated string
            String services = String.join(",", parser.getServices());
            setProperty(PARSED_SERVICES, services);
            
            // Store package name
            setProperty(PARSED_PACKAGE, parser.getPackageName());
            
            log.debug("Stored parsed proto info - Services: " + services + ", Package: " + parser.getPackageName());
            
        } catch (Exception e) {
            log.warn("Failed to store parsed proto info", e);
        }
    }
    
    /**
     * Static method to get proto parser for GUI usage - SHARED WITH GUI!
     */
    public static ProtoFileParser getProtoParserForPath(String protoPath) {
        if (protoPath == null || protoPath.trim().isEmpty()) {
            return null;
        }
        
        ProtoFileParser parser = PROTO_CACHE.get(protoPath);
        if (parser != null) {
            return parser;
        }
        
        // Parse and cache if not found
        try {
            parser = new ProtoFileParser();
            parser.parseProtoFile(protoPath);
            PROTO_CACHE.put(protoPath, parser);
            return parser;
        } catch (Exception e) {
            log.error("Failed to parse proto file: " + protoPath, e);
            return null;
        }
    }
    
    /**
     * Clear proto cache (useful for testing)
     */
    public static void clearProtoCache() {
        PROTO_CACHE.clear();
    }
    
    private void initializeIfNeeded() throws Exception {        
        if (grpcClient == null) {
            String serverUrl = getServerUrl();
            int timeout = getTimeoutSeconds();
            grpcClient = new GrpcWebClient(serverUrl, timeout);
        }
    }
    
    private GrpcWebClient.GrpcWebRequest prepareRequest(ProtoFileParser parser) throws Exception {
        GrpcWebClient.GrpcWebRequest request = new GrpcWebClient.GrpcWebRequest();
        
        // Set package name if available
        if (parser != null) {
            request.setPackageName(parser.getPackageName());
        }
        
        request.setServiceName(getServiceName());
        request.setMethodName(getMethodName());
        request.setUseTextFormat(getUseTextFormat());
        
        // Use JSON request
        String requestJson = getRequestJson();
        if (requestJson != null && !requestJson.trim().isEmpty()) {
            request.setJsonRequest(requestJson);
        }
        
        // Parse custom headers
        Map<String, String> headers = parseCustomHeaders();
        if (!headers.isEmpty()) {
            request.setHeaders(headers);
        }
        
        return request;
    }
    
    private void processResponse(SampleResult result, GrpcWebClient.GrpcWebResponse response) {
        result.setSuccessful(response.isSuccessful());
        result.setResponseCode(String.valueOf(response.getHttpStatusCode()));
        
        if (response.getGrpcStatus() != 0) {
            result.setSuccessful(false);
            result.setResponseMessage("gRPC Error: " + response.getGrpcMessage());
        } else {
            result.setResponseMessage("OK");
        }
        
        // Set response data
        if (response.getMessageBytes() != null) {
            try {
                // Try to convert bytes to readable format
                String responseText = new String(response.getMessageBytes(), "UTF-8");
                result.setResponseData(responseText, "UTF-8");
            } catch (Exception e) {
                result.setResponseData("Response received (" + response.getMessageBytes().length + " bytes)", "UTF-8");
            }
        } else {
            result.setResponseData("No response data", "UTF-8");
        }
        
        // Set timing information
        result.setLatency(response.getResponseTime());
    }
    
    private Map<String, String> parseCustomHeaders() {
        Map<String, String> headers = new HashMap<>();
        String customHeaders = getCustomHeaders();
        
        if (customHeaders != null && !customHeaders.trim().isEmpty()) {
            String[] headerLines = customHeaders.split("\n");
            for (String line : headerLines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        headers.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }
        
        return headers;
    }
    
    @Override
    public void testStarted() {
        testStarted("");
    }
    
    @Override
    public void testStarted(String host) {
        log.info("Test started. Proto cache size: " + PROTO_CACHE.size());
    }
    
    @Override
    public void testEnded() {
        testEnded("");
    }
    
    @Override
    public void testEnded(String host) {
        // Clean up resources but keep proto cache for next run
        if (grpcClient != null) {
            grpcClient.close();
            grpcClient = null;
        }
        log.info("Test ended. Proto cache preserved for next run.");
    }
    
    // Property getters and setters
    public String getProtoFilePath() {
        return getPropertyAsString(PROTO_FILE_PATH);
    }
    
    public void setProtoFilePath(String protoFilePath) {
        setProperty(PROTO_FILE_PATH, protoFilePath);
    }
    
    public String getServerUrl() {
        return getPropertyAsString(SERVER_URL);
    }
    
    public void setServerUrl(String serverUrl) {
        setProperty(SERVER_URL, serverUrl);
    }
    
    public String getServiceName() {
        return getPropertyAsString(SERVICE_NAME);
    }
    
    public void setServiceName(String serviceName) {
        setProperty(SERVICE_NAME, serviceName);
    }
    
    public String getMethodName() {
        return getPropertyAsString(METHOD_NAME);
    }
    
    public void setMethodName(String methodName) {
        setProperty(METHOD_NAME, methodName);
    }
    
    public String getRequestJson() {
        return getPropertyAsString(REQUEST_JSON);
    }
    
    public void setRequestJson(String requestJson) {
        setProperty(REQUEST_JSON, requestJson);
    }
    
    public int getTimeoutSeconds() {
        return getPropertyAsInt(TIMEOUT_SECONDS, 30);
    }
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        setProperty(TIMEOUT_SECONDS, timeoutSeconds);
    }
    
    public boolean getUseTextFormat() {
        return getPropertyAsBoolean(USE_TEXT_FORMAT, false);
    }
    
    public void setUseTextFormat(boolean useTextFormat) {
        setProperty(USE_TEXT_FORMAT, useTextFormat);
    }
    
    public String getCustomHeaders() {
        return getPropertyAsString(CUSTOM_HEADERS);
    }
    
    public void setCustomHeaders(String customHeaders) {
        setProperty(CUSTOM_HEADERS, customHeaders);
    }
    
    // Methods to access parsed proto info for persistence
    public String getParsedServices() {
        return getPropertyAsString(PARSED_SERVICES);
    }
    
    public String getParsedPackage() {
        return getPropertyAsString(PARSED_PACKAGE);
    }
}