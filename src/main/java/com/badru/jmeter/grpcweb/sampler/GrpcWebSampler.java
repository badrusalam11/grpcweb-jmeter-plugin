// File: src/main/java/com/badru/jmeter/grpcweb/sampler/GrpcWebSampler.java
package com.badru.jmeter.grpcweb.sampler;

import com.badru.jmeter.grpcweb.client.GrpcWebClient;
import com.badru.jmeter.grpcweb.client.GrpcWebClient.GrpcWebRequest;
import com.badru.jmeter.grpcweb.client.GrpcWebClient.GrpcWebResponse;
import com.badru.jmeter.grpcweb.util.ProtoFileParser;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.jmeter.testelement.property.BooleanProperty;


public class GrpcWebSampler extends AbstractSampler implements TestStateListener {
    private static final Logger log = LoggerFactory.getLogger(GrpcWebSampler.class);
    private static final Map<String, ProtoFileParser> PROTO_CACHE = new ConcurrentHashMap<>();

    public static final String PROTO_FILE_PATH = "GrpcWebSampler.protoFilePath";
    public static final String SERVER_URL      = "GrpcWebSampler.serverUrl";
    public static final String SERVICE_NAME    = "GrpcWebSampler.serviceName";
    public static final String METHOD_NAME     = "GrpcWebSampler.methodName";
    public static final String REQUEST_JSON    = "GrpcWebSampler.requestJson";
    public static final String TIMEOUT_SECONDS = "GrpcWebSampler.timeoutSeconds";
    public static final String USE_TEXT_FORMAT = "GrpcWebSampler.useTextFormat";
    public static final String CUSTOM_HEADERS  = "GrpcWebSampler.customHeaders";
    public static final String USE_RELATIVE_PATH    = "GrpcWebSampler.useRelativePath";

    private transient GrpcWebClient grpcClient;

    public GrpcWebSampler() {
        super();
        setName("gRPC-Web Request");
        setProperty(new BooleanProperty(USE_RELATIVE_PATH, false)); 
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        try {
            if (grpcClient == null) {
                grpcClient = new GrpcWebClient(getServerUrl(), getTimeoutSeconds());
            }
            String protoPath = getProtoFilePath();
            if (protoPath == null || protoPath.isEmpty()) {
                throw new IllegalStateException("Proto file path not set");
            }
            ProtoFileParser parser = PROTO_CACHE.computeIfAbsent(protoPath, path -> {
                ProtoFileParser p = new ProtoFileParser();
                try { p.parseProtoFile(path); } catch (Exception ex) { throw new RuntimeException(ex); }
                return p;
            });

            GrpcWebRequest req = new GrpcWebRequest();
            req.setPackageName(parser.getPackageName());
            req.setServiceName(getServiceName());
            req.setMethodName(getMethodName());
            if (getUseTextFormat()) {
                req.setUseTextFormat(true);
                req.setJsonText(getRequestJson());
            } else {
                req.setMessage(parser.createMessageFromJson(
                        getServiceName(), getMethodName(), getRequestJson()));
            }
            req.setHeaders(parseCustomHeaders());

            result.sampleStart();
            GrpcWebResponse resp = grpcClient.executeRequest(req);
            result.sampleEnd();

            result.setSuccessful(resp.isSuccessful());
            result.setResponseCode(String.valueOf(resp.getHttpStatusCode()));
            result.setResponseMessage(resp.getGrpcStatus() == 0 ? "OK" : resp.getGrpcMessage());
            result.setLatency(resp.getResponseTime());

            // Set response headers
            StringBuilder responseHeaders = new StringBuilder();
            responseHeaders.append("HTTP/1.1 ").append(resp.getHttpStatusCode()).append("\n");
            resp.getHeaders().forEach((k, v) -> responseHeaders.append(k).append(": ").append(v).append("\n"));
            result.setResponseHeaders(responseHeaders.toString());

            // Set request headers
            StringBuilder requestHeaders = new StringBuilder();
            resp.getRequestHeaders().forEach((k, v) -> requestHeaders.append(k).append(": ").append(v).append("\n"));
            result.setRequestHeaders(requestHeaders.toString());

            // Set request body (just the JSON)
            result.setSamplerData(getRequestJson());

            // Decode response
            DynamicMessage.Builder builder = parser.getOutputMessageBuilder(
                    getServiceName(), getMethodName());
            builder.mergeFrom(resp.getMessageBytes());
            String responseJson = JsonFormat.printer()
                    .includingDefaultValueFields()
                    .print(builder);
            result.setResponseData(responseJson, StandardCharsets.UTF_8.name());

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("500");
            result.setResponseMessage("Internal Error: " + e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("Error executing gRPC-Web request", e);
        }
        return result;
    }

    private Map<String, String> parseCustomHeaders() {
        String raw = getPropertyAsString(CUSTOM_HEADERS);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(
                java.util.Arrays.stream(raw.split("\\r?\\n"))
                        .filter(l -> l.contains(":"))
                        .map(l -> l.split(":", 2))
                        .collect(Collectors.toMap(a -> a[0].trim(), a -> a[1].trim()))
        );
    }

    public void setProtoFilePath(String path)   { setProperty(PROTO_FILE_PATH, path); }
    public void setServerUrl(String url)        { setProperty(SERVER_URL, url); }
    public void setServiceName(String name)     { setProperty(SERVICE_NAME, name); }
    public void setMethodName(String name)      { setProperty(METHOD_NAME, name); }
    public void setRequestJson(String json)     { setProperty(REQUEST_JSON, json); }
    public void setTimeoutSeconds(int seconds)  { setProperty(TIMEOUT_SECONDS, seconds); }
    public void setUseTextFormat(boolean tf)    { setProperty(USE_TEXT_FORMAT, tf); }
    public void setCustomHeaders(String hdr)    { setProperty(CUSTOM_HEADERS, hdr); }
    public void setUseRelativePath(boolean use) { setProperty(USE_RELATIVE_PATH, use); }

    public static ProtoFileParser getProtoParserForPath(String protoPath) {
        if (protoPath == null || protoPath.isEmpty()) return null;
        return PROTO_CACHE.computeIfAbsent(protoPath, path -> {
            ProtoFileParser p = new ProtoFileParser();
            try { p.parseProtoFile(path); } catch (Exception ex) { throw new RuntimeException(ex); }
            return p;
        });
    }

    @Override public void testStarted(String host) {}
    @Override public void testStarted() { testStarted(""); }
    @Override public void testEnded(String host) { if (grpcClient != null) grpcClient.close(); }
    @Override public void testEnded() { testEnded(""); }

    public String getProtoFilePath()  { return getPropertyAsString(PROTO_FILE_PATH); }
    public String getServerUrl()      { return getPropertyAsString(SERVER_URL); }
    public String getServiceName()    { return getPropertyAsString(SERVICE_NAME); }
    public String getMethodName()     { return getPropertyAsString(METHOD_NAME); }
    public String getRequestJson()    { return getPropertyAsString(REQUEST_JSON); }
    public int    getTimeoutSeconds() { return getPropertyAsInt(TIMEOUT_SECONDS, 30); }
    public boolean getUseTextFormat() { return getPropertyAsBoolean(USE_TEXT_FORMAT, false); }
    public String getCustomHeaders()  { return getPropertyAsString(CUSTOM_HEADERS); }
    public boolean getUseRelativePath(){ return getPropertyAsBoolean(USE_RELATIVE_PATH); }
}
