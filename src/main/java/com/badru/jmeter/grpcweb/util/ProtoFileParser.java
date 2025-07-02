package com.badru.jmeter.grpcweb.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtoFileParser {
    private Map<String, List<String>> serviceToMethods;
    private Map<String, Map<String, String>> serviceMethodToRequestType;
    private Map<String, Map<String, String>> serviceMethodToResponseType;
    private String packageName = "";
    
    public ProtoFileParser() {
        this.serviceToMethods = new HashMap<>();
        this.serviceMethodToRequestType = new HashMap<>();
        this.serviceMethodToResponseType = new HashMap<>();
    }
    
    /**
     * Parse proto file and extract service definitions
     */
    public void parseProtoFile(String protoFilePath) throws Exception {
        File protoFile = new File(protoFilePath);
        if (!protoFile.exists()) {
            throw new IOException("Proto file not found: " + protoFilePath);
        }
        
        String protoContent = readFileContent(protoFile);
        parseProtoContent(protoContent);
    }
    
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    private void parseProtoContent(String content) throws Exception {
        // Clear previous data
        serviceToMethods.clear();
        serviceMethodToRequestType.clear();
        serviceMethodToResponseType.clear();
        packageName = "";
        
        // Remove comments first
        content = removeComments(content);
        
        // Extract package name
        extractPackageName(content);
        
        // Parse all services
        Pattern servicePattern = Pattern.compile(
            "service\\s+(\\w+)\\s*\\{([^{}]*(?:\\{[^{}]*\\}[^{}]*)*)\\}",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        
        Matcher serviceMatcher = servicePattern.matcher(content);
        
        while (serviceMatcher.find()) {
            String serviceName = serviceMatcher.group(1);
            String serviceBody = serviceMatcher.group(2);
            
            System.out.println("Parsing service: " + serviceName);
            
            parseServiceMethods(serviceName, serviceBody);
        }
        
        System.out.println("Package: " + packageName);
        System.out.println("Total services found: " + serviceToMethods.size());
        for (String service : serviceToMethods.keySet()) {
            System.out.println("Service: " + service + " with " + serviceToMethods.get(service).size() + " methods");
        }
    }
    
    private void extractPackageName(String content) {
        Pattern packagePattern = Pattern.compile("package\\s+(\\w+(?:\\.\\w+)*);", Pattern.CASE_INSENSITIVE);
        Matcher packageMatcher = packagePattern.matcher(content);
        
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
            System.out.println("Found package: " + packageName);
        }
    }
    
    private void parseServiceMethods(String serviceName, String serviceBody) {
        List<String> methods = new ArrayList<>();
        Map<String, String> requestTypes = new HashMap<>();
        Map<String, String> responseTypes = new HashMap<>();
        
        // Pattern to match RPC definitions
        Pattern rpcPattern = Pattern.compile(
            "rpc\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)\\s*returns\\s*\\(\\s*(\\w+)\\s*\\)\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        
        Matcher rpcMatcher = rpcPattern.matcher(serviceBody);
        
        while (rpcMatcher.find()) {
            String methodName = rpcMatcher.group(1);
            String requestType = rpcMatcher.group(2);
            String responseType = rpcMatcher.group(3);
            
            methods.add(methodName);
            requestTypes.put(methodName, requestType);
            responseTypes.put(methodName, responseType);
            
            System.out.println("  Method: " + methodName + "(" + requestType + ") -> " + responseType);
        }
        
        if (!methods.isEmpty()) {
            serviceToMethods.put(serviceName, methods);
            serviceMethodToRequestType.put(serviceName, requestTypes);
            serviceMethodToResponseType.put(serviceName, responseTypes);
        }
    }
    
    private String removeComments(String content) {
        // Remove single-line comments
        content = content.replaceAll("//.*?(?=\\n|$)", "");
        
        // Remove multi-line comments
        content = content.replaceAll("/\\*.*?\\*/", "");
        
        return content;
    }
    
    /**
     * Get all available services
     */
    public Set<String> getServices() {
        return serviceToMethods.keySet();
    }
    
    /**
     * Get methods for a specific service
     */
    public List<String> getMethodsForService(String serviceName) {
        return serviceToMethods.getOrDefault(serviceName, new ArrayList<>());
    }
    
    /**
     * Get request type for a specific service and method
     */
    public String getRequestType(String serviceName, String methodName) {
        Map<String, String> methods = serviceMethodToRequestType.get(serviceName);
        return methods != null ? methods.get(methodName) : null;
    }
    
    /**
     * Get response type for a specific service and method
     */
    public String getResponseType(String serviceName, String methodName) {
        Map<String, String> methods = serviceMethodToResponseType.get(serviceName);
        return methods != null ? methods.get(methodName) : null;
    }
    
    /**
     * Create a DynamicMessage from JSON input
     * Note: This is a simplified implementation for demo purposes
     * In production, you'd want proper protobuf compilation
     */
    public DynamicMessage createMessageFromJson(String serviceName, String methodName, String jsonInput) throws Exception {
        // For now, return null - this would need proper protobuf descriptor support
        // This is where you'd integrate with protoc-generated descriptors
        throw new UnsupportedOperationException("Dynamic message creation requires protobuf descriptors. " +
            "For now, the plugin handles JSON-to-protobuf conversion in the client layer.");
    }
    
    /**
     * Convert DynamicMessage response to JSON
     */
    public String messageToJson(DynamicMessage message) throws Exception {
        return JsonFormat.printer()
                .includingDefaultValueFields()
                .preservingProtoFieldNames()
                .print(message);
    }
    
    /**
     * Validate JSON structure for a given service method
     */
    public boolean validateJsonStructure(String serviceName, String methodName, String jsonInput) {
        try {
            if (jsonInput == null || jsonInput.trim().isEmpty()) {
                return false;
            }
            
            // Basic JSON validation
            String trimmed = jsonInput.trim();
            return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                   (trimmed.startsWith("[") && trimmed.endsWith("]"));
                   
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get package name
     */
    public String getPackageName() {
        return packageName;
    }
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Proto Parser Debug Info ===\n");
        
        for (Map.Entry<String, List<String>> entry : serviceToMethods.entrySet()) {
            String serviceName = entry.getKey();
            List<String> methods = entry.getValue();
            
            sb.append("Service: ").append(serviceName).append("\n");
            
            for (String method : methods) {
                String requestType = getRequestType(serviceName, method);
                String responseType = getResponseType(serviceName, method);
                sb.append("  - ").append(method)
                  .append("(").append(requestType).append(")")
                  .append(" returns (").append(responseType).append(")\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}