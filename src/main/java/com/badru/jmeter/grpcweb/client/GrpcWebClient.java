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
        this.baseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Execute gRPC-Web request with JSON or DynamicMessage input
     */
    public GrpcWebResponse executeRequest(GrpcWebRequest request) throws IOException {
        // Build URL using package name
        String url = buildUrl(
            request.getPackageName(),
            request.getServiceName(),
            request.getMethodName()
        );

        // Handle JSON to protobuf conversion
        byte[] messageBytes;
        if (request.getMessage() != null) {
            messageBytes = request.getMessage().toByteArray();
        } else if (request.getJsonRequest() != null) {
            messageBytes = jsonToProtobufBytes(request.getJsonRequest());
        } else {
            throw new IOException("No request data provided");
        }

        // Create gRPC-Web frame
        byte[] grpcWebFrame = createGrpcWebFrame(messageBytes);

        // Choose binary or text format
        byte[] requestBody;
        String contentType;
        if (request.isUseTextFormat()) {
            requestBody = Base64.getEncoder().encode(grpcWebFrame);
            contentType = CONTENT_TYPE_GRPC_WEB_TEXT;
        } else {
            requestBody = grpcWebFrame;
            contentType = CONTENT_TYPE_GRPC_WEB;
        }

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(RequestBody.create(requestBody, MediaType.parse(contentType)))
            .addHeader("Content-Type", contentType)
            .addHeader("Accept", contentType)
            .addHeader("X-Grpc-Web", "1");

        // Add any custom headers
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        Request httpRequest = requestBuilder.build();
        long startTime = System.currentTimeMillis();
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return processResponse(response, request.isUseTextFormat(), elapsed);
        }
    }

    /**
     * Build a URL like: https://host/{package}.{Service}/{Method}
     */
    private String buildUrl(String packageName, String serviceName, String methodName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName must be provided");
        }
        return String.format(
            "%s/%s.%s/%s",
            baseUrl,
            packageName,
            serviceName,
            methodName
        );
    }

    private byte[] createGrpcWebFrame(byte[] messageBytes) {
        byte[] frame = new byte[5 + messageBytes.length];
        frame[0] = 0; // no compression
        int len = messageBytes.length;
        frame[1] = (byte) ((len >> 24) & 0xFF);
        frame[2] = (byte) ((len >> 16) & 0xFF);
        frame[3] = (byte) ((len >> 8) & 0xFF);
        frame[4] = (byte) (len & 0xFF);
        System.arraycopy(messageBytes, 0, frame, 5, messageBytes.length);
        return frame;
    }

    private byte[] jsonToProtobufBytes(String jsonRequest) throws IOException {
        // TODO: integrate proper protobuf descriptor-based encoding
        return jsonRequest.getBytes(StandardCharsets.UTF_8);
    }

    private GrpcWebResponse processResponse(Response response, boolean isText, long responseTime) throws IOException {
        GrpcWebResponse grpcResponse = new GrpcWebResponse();
        grpcResponse.setHttpStatusCode(response.code());
        grpcResponse.setResponseTime(responseTime);
        grpcResponse.setSuccessful(response.isSuccessful());

        byte[] bodyBytes = response.body() != null ? response.body().bytes() : new byte[0];
        if (isText) {
            bodyBytes = Base64.getDecoder().decode(bodyBytes);
        }

        if (bodyBytes.length >= 5) {
            int msgLen = ((bodyBytes[1] & 0xFF) << 24)
                       | ((bodyBytes[2] & 0xFF) << 16)
                       | ((bodyBytes[3] & 0xFF) << 8)
                       | (bodyBytes[4] & 0xFF);
            if (bodyBytes.length >= 5 + msgLen) {
                byte[] msg = new byte[msgLen];
                System.arraycopy(bodyBytes, 5, msg, 0, msgLen);
                grpcResponse.setMessageBytes(msg);
            }
        }

        String status = response.header("grpc-status");
        if (status != null) grpcResponse.setGrpcStatus(Integer.parseInt(status));

        String message = response.header("grpc-message");
        if (message != null) grpcResponse.setGrpcMessage(message);

        return grpcResponse;
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public static class GrpcWebRequest {
        private String packageName;
        private String serviceName;
        private String methodName;
        private DynamicMessage message;
        private String jsonRequest;
        private Map<String, String> headers;
        private boolean useTextFormat;

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

    public static class GrpcWebResponse {
        private int httpStatusCode;
        private int grpcStatus;
        private String grpcMessage;
        private byte[] messageBytes;
        private long responseTime;
        private boolean successful;

        public int getHttpStatusCode() { return httpStatusCode; }
        public void setHttpStatusCode(int code) { this.httpStatusCode = code; }
        public int getGrpcStatus() { return grpcStatus; }
        public void setGrpcStatus(int status) { this.grpcStatus = status; }
        public String getGrpcMessage() { return grpcMessage; }
        public void setGrpcMessage(String msg) { this.grpcMessage = msg; }
        public byte[] getMessageBytes() { return messageBytes; }
        public void setMessageBytes(byte[] bytes) { this.messageBytes = bytes; }
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long time) { this.responseTime = time; }
        public boolean isSuccessful() { return successful && grpcStatus == 0; }
        public void setSuccessful(boolean ok) { this.successful = ok; }
    }
}
