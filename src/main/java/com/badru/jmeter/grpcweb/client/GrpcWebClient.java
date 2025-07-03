// File: src/main/java/com/badru/jmeter/grpcweb/client/GrpcWebClient.java
package com.badru.jmeter.grpcweb.client;

import com.google.protobuf.DynamicMessage;
import okhttp3.*;
import okio.Buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GrpcWebClient {
    private static final Logger log = LoggerFactory.getLogger(GrpcWebClient.class);

    private static final String CONTENT_TYPE_GRPC_WEB = "application/grpc-web+proto";
    private static final String USER_AGENT = "grpc-web-jmeter-plugin/1.0";

    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final int timeout;

    public GrpcWebClient(String serverUrl, int timeoutSeconds) {
        this.serverUrl = serverUrl;
        this.timeout = timeoutSeconds;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public GrpcWebResponse executeRequest(GrpcWebRequest request) throws IOException {
        String fullUrl = (request.getPackageName() != null && !request.getPackageName().isEmpty())
                ? String.format("%s/%s.%s/%s", serverUrl, request.getPackageName(), request.getServiceName(), request.getMethodName())
                : String.format("%s/%s/%s", serverUrl, request.getServiceName(), request.getMethodName());
        log.info("[Request URL] {}", fullUrl);

        byte[] bodyBytes;
        if (request.isUseTextFormat()) {
            String json = request.getJsonText();
            log.debug("[Request JSON] {}", json);
            bodyBytes = json.getBytes(StandardCharsets.UTF_8);
        } else {
            DynamicMessage msg = request.getMessage();
            bodyBytes = msg.toByteArray();
            log.debug("[Request Protobuf bytes size] {}", bodyBytes.length);
        }

        Buffer buf = new Buffer();
        buf.writeByte(0x0);
        buf.writeInt(bodyBytes.length);
        buf.write(bodyBytes);
        byte[] grpcWebPayload = buf.readByteArray();
        log.debug("[gRPC-Web payload (base64)] {}", Base64.getEncoder().encodeToString(grpcWebPayload));

        RequestBody reqBody = RequestBody.create(grpcWebPayload, MediaType.parse(CONTENT_TYPE_GRPC_WEB));
        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .post(reqBody)
                .addHeader("Content-Type", CONTENT_TYPE_GRPC_WEB)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-Grpc-Web", "1")
                .addHeader("Accept", "application/grpc-web+proto");

        if (request.getHeaders() != null) {
            request.getHeaders().forEach(builder::addHeader);
        }

        Request okReq = builder.build();
        log.info("[Request Headers] {}", okReq.headers());

        Response resp = httpClient.newCall(okReq).execute();

        Map<String, String> headerMap = new HashMap<>();
        for (String name : resp.headers().names()) {
            headerMap.put(name, resp.header(name));
        }

        log.info("[Response Code] {}", resp.code());
        log.info("[Response Headers] {}", resp.headers());

        byte[] respBytes = resp.body().bytes();
        log.debug("[Raw response (base64)] {}", Base64.getEncoder().encodeToString(respBytes));

        byte[] msgBytes = new byte[0];
        String jsonString = null;
        if (respBytes.length >= 5) {
            int len = ((respBytes[1] & 0xFF) << 24) | ((respBytes[2] & 0xFF) << 16)
                    | ((respBytes[3] & 0xFF) << 8) | (respBytes[4] & 0xFF);
            msgBytes = new byte[Math.min(len, respBytes.length - 5)];
            System.arraycopy(respBytes, 5, msgBytes, 0, msgBytes.length);
            try {
                jsonString = new String(msgBytes, StandardCharsets.UTF_8);
                log.debug("[Decoded JSON] {}", jsonString);
            } catch (Exception ex) {
                log.warn("Failed to decode message bytes as UTF-8 JSON", ex);
            }
        }

        return new GrpcWebResponse(
                resp.code(),
                0,
                "OK",
                msgBytes,
                TimeUnit.SECONDS.toMillis(timeout),
                jsonString,
                headerMap
        );
    }

    public void close() {}

    public static class GrpcWebRequest {
        private String packageName;
        private String serviceName;
        private String methodName;
        private DynamicMessage message;
        private boolean useTextFormat;
        private String jsonText;
        private Map<String, String> headers;

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }

        public DynamicMessage getMessage() { return message; }
        public void setMessage(DynamicMessage message) { this.message = message; }

        public boolean isUseTextFormat() { return useTextFormat; }
        public void setUseTextFormat(boolean useTextFormat) { this.useTextFormat = useTextFormat; }

        public String getJsonText() { return jsonText; }
        public void setJsonText(String jsonText) { this.jsonText = jsonText; }

        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    }

    public static class GrpcWebResponse {
        private final int httpStatusCode;
        private final int grpcStatus;
        private final String grpcMessage;
        private final byte[] messageBytes;
        private final long responseTime;
        private final String jsonString;
        private final Map<String, String> headers;

        public GrpcWebResponse(int httpStatusCode, int grpcStatus, String grpcMessage,
                               byte[] messageBytes, long responseTime, String jsonString,
                               Map<String, String> headers) {
            this.httpStatusCode = httpStatusCode;
            this.grpcStatus = grpcStatus;
            this.grpcMessage = grpcMessage;
            this.messageBytes = messageBytes;
            this.responseTime = responseTime;
            this.jsonString = jsonString;
            this.headers = headers;
        }

        public int getHttpStatusCode() { return httpStatusCode; }
        public int getGrpcStatus() { return grpcStatus; }
        public String getGrpcMessage() { return grpcMessage; }
        public byte[] getMessageBytes() { return messageBytes; }
        public long getResponseTime() { return responseTime; }
        public String getJsonString() { return jsonString; }
        public Map<String, String> getHeaders() { return headers; }
        public boolean isSuccessful() { return grpcStatus == 0 && httpStatusCode == 200; }
    }
}