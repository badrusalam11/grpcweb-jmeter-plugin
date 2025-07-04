// File: src/main/java/com/badru/jmeter/grpcweb/util/ProtoFileParser.java
package com.badru.jmeter.grpcweb.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtoFileParser {
    private Path protoPath;
    private Descriptors.FileDescriptor fileDescriptor;
    private Map<String, List<String>> serviceToMethods = new HashMap<>();
    private Map<String, Map<String, String>> serviceMethodToRequestType = new HashMap<>();
    private Map<String, Map<String, String>> serviceMethodToResponseType = new HashMap<>();
    private Map<String, Descriptors.ServiceDescriptor> serviceDescriptors = new HashMap<>();
    private String packageName = "";

    public void parseProtoFile(String protoFilePath) throws Exception {
        this.protoPath = Paths.get(protoFilePath);
        if (!Files.exists(protoPath)) {
            throw new FileNotFoundException("Proto file not found: " + protoFilePath);
        }

        String protoContent = new String(Files.readAllBytes(protoPath), StandardCharsets.UTF_8);
        String cleaned = removeComments(protoContent);
        extractPackageName(cleaned);
        parseServices(cleaned);

        try {
            // Pakai absolute path untuk semuanya
            Path protoPathAbs = protoPath.toAbsolutePath();                  // e.g. D:/.../auth.proto
            Path protoDirAbs = protoPathAbs.getParent();                     // e.g. D:/.../protos
            Path descOut = Files.createTempFile("desc", ".pb");              // temporary output file

            System.out.println("protoFilePath: " + protoPathAbs);
            System.out.println("protoDir: " + protoDirAbs);
            System.out.println("descOut: " + descOut.toAbsolutePath());

            // Build the protoc command
            ProcessBuilder pb = new ProcessBuilder(
                "protoc",
                "--descriptor_set_out=" + descOut.toAbsolutePath(),
                "--include_imports",
                "-I", protoDirAbs.toString(),                                // full absolute include path
                protoPathAbs.toString()                                      // full absolute path to proto file
            );

            pb.directory(protoDirAbs.toFile());                              // working dir = proto folder
            Process proc = pb.start();

            // Read stderr from protoc
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            try (InputStream es = proc.getErrorStream()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = es.read(buf)) != -1) {
                    errStream.write(buf, 0, len);
                }
            }

            int exit = proc.waitFor();
            if (exit != 0) {
                String errMsg = new String(errStream.toByteArray(), StandardCharsets.UTF_8);
                throw new IOException("protoc failed (exit " + exit + "): " + errMsg);
            }

            // Parse descriptor set
            DescriptorProtos.FileDescriptorSet set;
            try (InputStream is = Files.newInputStream(descOut)) {
                set = DescriptorProtos.FileDescriptorSet.parseFrom(is);
            }

            Map<String, Descriptors.FileDescriptor> nameToFd = new HashMap<>();
            for (DescriptorProtos.FileDescriptorProto fdp : set.getFileList()) {
                List<Descriptors.FileDescriptor> deps = new ArrayList<>();
                for (String depName : fdp.getDependencyList()) {
                    Descriptors.FileDescriptor depFd = nameToFd.get(depName);
                    if (depFd != null) deps.add(depFd);
                }
                Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                    fdp,
                    deps.toArray(new Descriptors.FileDescriptor[0])
                );
                nameToFd.put(fdp.getName(), fd);
            }

            String fileName = protoPathAbs.getFileName().toString();
            fileDescriptor = nameToFd.get(fileName);
            if (fileDescriptor == null) {
                for (Descriptors.FileDescriptor fd : nameToFd.values()) {
                    if (fd.getName().endsWith(fileName)) {
                        fileDescriptor = fd;
                        break;
                    }
                }
            }

            if (fileDescriptor == null) {
                throw new IOException("Failed to locate descriptor for " + protoPathAbs);
            }

            for (Descriptors.ServiceDescriptor svc : fileDescriptor.getServices()) {
                serviceDescriptors.put(svc.getName(), svc);
            }

        } catch (Exception ex) {
            System.err.println("[ProtoFileParser] protoc descriptor generation failed: " + ex.getMessage());
        }

    }

    private void parseServices(String content) {
        serviceToMethods.clear();
        serviceMethodToRequestType.clear();
        serviceMethodToResponseType.clear();

        Pattern svcPat = Pattern.compile(
            "service\\s+(\\w+)\\s*\\{([^{}]*)\\}",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher svcMat = svcPat.matcher(content);
        while (svcMat.find()) {
            String svcName = svcMat.group(1);
            String body = svcMat.group(2);
            List<String> methods = new ArrayList<>();
            Map<String, String> req = new HashMap<>();
            Map<String, String> res = new HashMap<>();
            Pattern rpcPat = Pattern.compile(
                "rpc\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)\\s*returns\\s*\\(\\s*(\\w+)\\s*\\)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher rpcMat = rpcPat.matcher(body);
            while (rpcMat.find()) {
                String m = rpcMat.group(1);
                methods.add(m);
                req.put(m, rpcMat.group(2));
                res.put(m, rpcMat.group(3));
            }
            if (!methods.isEmpty()) {
                serviceToMethods.put(svcName, methods);
                serviceMethodToRequestType.put(svcName, req);
                serviceMethodToResponseType.put(svcName, res);
            }
        }
    }

    private String removeComments(String s) {
        s = s.replaceAll("//.*?(?=\\n|$)", "");
        s = s.replaceAll("/\\*.*?\\*/", "");
        return s;
    }

    private void extractPackageName(String content) {
        Matcher m = Pattern.compile("package\\s+([\\w.]+);", Pattern.CASE_INSENSITIVE).matcher(content);
        if (m.find()) this.packageName = m.group(1);
    }

    public DynamicMessage createMessageFromJson(
        String serviceName,
        String methodName,
        String jsonInput
    ) throws Exception {
        if (fileDescriptor == null) {
            throw new IllegalStateException("Descriptor not initialized, JSON conversion unavailable");
        }
        Descriptors.ServiceDescriptor svc = fileDescriptor.findServiceByName(serviceName);
        if (svc == null) throw new IllegalArgumentException("Service not found: " + serviceName);
        Descriptors.MethodDescriptor mth = svc.findMethodByName(methodName);
        if (mth == null) throw new IllegalArgumentException("Method not found: " + methodName);

        Descriptors.Descriptor inType = mth.getInputType();
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(inType);
        JsonFormat.parser()
            .ignoringUnknownFields()
            .merge(jsonInput, builder);
        return builder.build();
    }

    public DynamicMessage.Builder getOutputMessageBuilder(String serviceName, String methodName) {
        Descriptors.MethodDescriptor method = getMethodDescriptor(serviceName, methodName);
        return DynamicMessage.newBuilder(method.getOutputType());
    }

    public Descriptors.MethodDescriptor getMethodDescriptor(String serviceName, String methodName) {
        Descriptors.ServiceDescriptor service = serviceDescriptors.get(serviceName);
        if (service == null) throw new IllegalArgumentException("Service not found: " + serviceName);
        Descriptors.MethodDescriptor method = service.findMethodByName(methodName);
        if (method == null) throw new IllegalArgumentException("Method not found: " + methodName);
        return method;
    }

    public Set<String> getServices() { return serviceToMethods.keySet(); }
    public List<String> getMethodsForService(String svc) {
        List<String> list = serviceToMethods.get(svc);
        return list != null ? list : Collections.<String>emptyList();
    }
    public String getRequestType(String svc, String m) {
        Map<String, String> map = serviceMethodToRequestType.get(svc);
        return (map != null) ? map.get(m) : null;
    }
    public String getResponseType(String svc, String m) {
        Map<String, String> map = serviceMethodToResponseType.get(svc);
        return (map != null) ? map.get(m) : null;
    }
    public String getPackageName() { return packageName; }
    public String getDebugInfo() { return fileDescriptor != null ? fileDescriptor.toProto().toString() : ""; }
}
