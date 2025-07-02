package com.badru.jmeter.grpcweb.client;

import com.google.protobuf.DynamicMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GrpcWebClient {
    private static final Logger log = LoggerFactory.getLogger(GrpcWebClient.class);
    
    private static final String CONTENT_TYPE_GRPC_WEB = "application/grpc-web+proto";
    private static final String CONTENT_TYPE_GRPC_WEB_TEXT = "application/grpc-web-text";
    
    private final OkHttpClient httpClient;
    private final String baseUrl;
    
    public GrpcWebClient(String baseUrl, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Execute gRPC-Web request with JSON input
     */
    public GrpcWebResponse executeRequest(GrpcWebRequest request) throws IOException {
        String url = buildUrl(request.getServiceName(), request.getMethodName());
        
        // Handle JSON to protobuf conversion
        byte[] messageBytes;
        if (request.getMessage() != null) {
            // If we have a DynamicMessage, use it
            messageBytes = request.getMessage().toByteArray();
        } else if (request.getJsonRequest() != null) {
            // Convert JSON to protobuf bytes (simplified approach)
            messageBytes = jsonToProtobufBytes(request.getJsonRequest());
        } else {
            throw new IOException("No request data provided");
        }
        
        // Create gRPC-Web frame
        byte[] grpcWebFrame = createGrpcWebFrame(messageBytes);
        
        // Encode for transport (base64 for grpc-web-text)
        byte[] requestBody;
        String contentType;
        
        if (request.isUseTextFormat()) {
            requestBody = Base64.getEncoder().encode(grpcWebFrame);
            contentType = CONTENT_TYPE_GRPC_WEB_TEXT;
        } else {
            requestBody = grpcWebFrame;
            contentType = CONTENT_TYPE_GRPC_WEB;
        }
        
        // Build HTTP request
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody, MediaType.parse(contentType)))
                .addHeader("Content-Type", contentType)
                .addHeader("Accept", contentType)
                .addHeader("X-Grpc-Web", "1");
        
        // Add custom headers
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }
        
        Request httpRequest = requestBuilder.build();
        
        // Execute request
        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            long endTime = System.currentTimeMillis();
            
            return processResponse(response, request.isUseTextFormat(), endTime - startTime);
        }
    }
    
    /**
     * Simple JSON to protobuf bytes conversion
     * This is a simplified approach - in production you'd want proper protobuf compilation
     */
    private byte[] jsonToProtobufBytes(String jsonRequest) throws IOException {
        // For now, we'll create a simple protobuf-like structure
        // This is where you'd normally use protobuf descriptors to properly encode
        
        try {
            // Simple approach: create a basic protobuf message from JSON
            // This is very simplified and would need proper implementation
            return jsonRequest.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException("Failed to convert JSON to protobuf: " + e.getMessage(), e);
        }
    }
    
    private String buildUrl(String serviceName, String methodName) {
        // Build gRPC-Web URL: /package.ServiceName/MethodName
        return String.format("%s/example.%s/%s", baseUrl, serviceName, methodName);
    }
    
    /**
     * Create gRPC-Web frame format:
     * [Compressed-Flag][Length][Message][Trailer-Flag][Trailer-Length][Trailers]
     */
    private byte[] createGrpcWebFrame(byte[] messageBytes) {
        // Simple implementation - no compression, no trailers for now
        byte[] frame = new byte[5 + messageBytes.length];
        
        // Compression flag (1 byte) - 0 for no compression
        frame[0] = 0;
        
        // Message length (4 bytes, big-endian)
        int length = messageBytes.length;
        frame[1] = (byte) ((length >> 24) & 0xFF);
        frame[2] = (byte) ((length >> 16) & 0xFF);
        frame[3] = (byte) ((length >> 8) & 0xFF);
        frame[4] = (byte) (length & 0xFF);
        
        // Message bytes
        System.arraycopy(messageBytes, 0, frame, 5, messageBytes.length);
        
        return frame;
    }
    
    private GrpcWebResponse processResponse(Response response, boolean isTextFormat, long responseTime) throws IOException {
        GrpcWebResponse grpcResponse = new GrpcWebResponse();
        grpcResponse.setHttpStatusCode(response.code());
        grpcResponse.setResponseTime(responseTime);
        grpcResponse.setSuccessful(response.isSuccessful());
        
        if (response.body() != null) {
            byte[] responseBytes = response.body().bytes();
            
            if (isTextFormat) {
                // Decode base64
                responseBytes = Base64.getDecoder().decode(responseBytes);
            }
            
            // Parse gRPC-Web frame
            if (responseBytes.length >= 5) {
                // Skip compression flag (1 byte)
                // Extract message length (4 bytes)
                int messageLength = ((responseBytes[1] & 0xFF) << 24) |
                                  ((responseBytes[2] & 0xFF) << 16) |
                                  ((responseBytes[3] & 0xFF) << 8) |
                                  (responseBytes[4] & 0xFF);
                
                if (responseBytes.length >= 5 + messageLength) {
                    byte[] messageBytes = new byte[messageLength];
                    System.arraycopy(responseBytes, 5, messageBytes, 0, messageLength);
                    grpcResponse.setMessageBytes(messageBytes);
                }
            }
        }
        
        // Extract gRPC status from headers
        String grpcStatus = response.header("grpc-status");
        if (grpcStatus != null) {
            grpcResponse.setGrpcStatus(Integer.parseInt(grpcStatus));
        }
        
        String grpcMessage = response.header("grpc-message");
        if (grpcMessage != null) {
            grpcResponse.setGrpcMessage(grpcMessage);
        }
        
        return grpcResponse;
    }
    
    public void close() {
        // Close HTTP client resources
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
    
    /**
     * Request wrapper class
     */
    public static class GrpcWebRequest {
        private String packageName;
        private String serviceName;
        private String methodName;
        private DynamicMessage message;
        private String jsonRequest;  // Add JSON support
        private Map<String, String> headers;
        private boolean useTextFormat = false;
        
        // Getters and setters
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        
        public DynamicMessage getMessage() { return message; }
        public void setMessage(DynamicMessage message) { this.message = message; }
        
        public String getJsonRequest() { return jsonRequest; }
        public void setJsonRequest(String jsonRequest) { this.jsonRequest = jsonRequest; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public boolean isUseTextFormat() { return useTextFormat; }
        public void setUseTextFormat(boolean useTextFormat) { this.useTextFormat = useTextFormat; }
    }
    
    /**
     * Response wrapper class
     */
    public static class GrpcWebResponse {
        private int httpStatusCode;
        private int grpcStatus = 0;
        private String grpcMessage;
        private byte[] messageBytes;
        private long responseTime;
        private boolean successful;
        
        // Getters and setters
        public int getHttpStatusCode() { return httpStatusCode; }
        public void setHttpStatusCode(int httpStatusCode) { this.httpStatusCode = httpStatusCode; }
        
        public int getGrpcStatus() { return grpcStatus; }
        public void setGrpcStatus(int grpcStatus) { this.grpcStatus = grpcStatus; }
        
        public String getGrpcMessage() { return grpcMessage; }
        public void setGrpcMessage(String grpcMessage) { this.grpcMessage = grpcMessage; }
        
        public byte[] getMessageBytes() { return messageBytes; }
        public void setMessageBytes(byte[] messageBytes) { this.messageBytes = messageBytes; }
        
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
        
        public boolean isSuccessful() { return successful && grpcStatus == 0; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
    }
}