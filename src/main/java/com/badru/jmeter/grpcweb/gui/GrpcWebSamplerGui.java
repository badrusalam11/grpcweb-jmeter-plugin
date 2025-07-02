package com.badru.jmeter.grpcweb.gui;

import com.badru.jmeter.grpcweb.sampler.GrpcWebSampler;
import com.badru.jmeter.grpcweb.util.ProtoFileParser;
import org.apache.jmeter.gui.util.HorizontalPanel;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Set;

public class GrpcWebSamplerGui extends AbstractSamplerGui {
    private static final Logger log = LoggerFactory.getLogger(GrpcWebSamplerGui.class);
    
    private JTextField protoFilePathField;
    private JButton browseButton;
    private JTextField serverUrlField;
    private JComboBox<String> serviceComboBox;
    private JComboBox<String> methodComboBox;
    private JTextArea requestJsonArea;
    private JTextField timeoutField;
    private JCheckBox useTextFormatCheckBox;
    private JTextArea customHeadersArea;
    
    private ProtoFileParser protoParser;
    
    public GrpcWebSamplerGui() {
        super();
        init();
    }
    
    @Override
    public String getStaticLabel() {
        return "gRPC-Web Request";
    }
    
    @Override
    public String getLabelResource() {
        return null; // We use getStaticLabel() instead
    }
    
    @Override
    public TestElement createTestElement() {
        GrpcWebSampler sampler = new GrpcWebSampler();
        modifyTestElement(sampler);
        return sampler;
    }
    
    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
        if (element instanceof GrpcWebSampler) {
            GrpcWebSampler sampler = (GrpcWebSampler) element;
            sampler.setProtoFilePath(protoFilePathField.getText());
            sampler.setServerUrl(serverUrlField.getText());
            sampler.setServiceName((String) serviceComboBox.getSelectedItem());
            sampler.setMethodName((String) methodComboBox.getSelectedItem());
            sampler.setRequestJson(requestJsonArea.getText());
            
            // Safe timeout parsing
            try {
                int timeout = Integer.parseInt(timeoutField.getText().trim());
                sampler.setTimeoutSeconds(timeout);
            } catch (NumberFormatException e) {
                sampler.setTimeoutSeconds(30); // Default value
            }
            
            sampler.setUseTextFormat(useTextFormatCheckBox.isSelected());
            sampler.setCustomHeaders(customHeadersArea.getText());
        }
    }
    
    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof GrpcWebSampler) {
            GrpcWebSampler sampler = (GrpcWebSampler) element;
            
            // Restore basic fields
            protoFilePathField.setText(sampler.getProtoFilePath());
            serverUrlField.setText(sampler.getServerUrl());
            requestJsonArea.setText(sampler.getRequestJson());
            timeoutField.setText(String.valueOf(sampler.getTimeoutSeconds()));
            useTextFormatCheckBox.setSelected(sampler.getUseTextFormat());
            customHeadersArea.setText(sampler.getCustomHeaders());
            
            // Restore parsed services if available - FIXES PERSISTENCE!
            String protoPath = sampler.getProtoFilePath();
            if (protoPath != null && !protoPath.trim().isEmpty()) {
                ProtoFileParser parser = GrpcWebSampler.getProtoParserForPath(protoPath);
                if (parser != null) {
                    // Restore service dropdown
                    serviceComboBox.removeAllItems();
                    for (String service : parser.getServices()) {
                        serviceComboBox.addItem(service);
                    }
                    
                    // Set selected service
                    serviceComboBox.setSelectedItem(sampler.getServiceName());
                    
                    // Restore method dropdown
                    String selectedService = sampler.getServiceName();
                    if (selectedService != null) {
                        methodComboBox.removeAllItems();
                        for (String method : parser.getMethodsForService(selectedService)) {
                            methodComboBox.addItem(method);
                        }
                        methodComboBox.setSelectedItem(sampler.getMethodName());
                    }
                    
                    this.protoParser = parser;
                }
            }
        }
    }
    
    @Override
    public void clearGui() {
        super.clearGui();
        protoFilePathField.setText("");
        serverUrlField.setText("");
        serviceComboBox.removeAllItems();
        methodComboBox.removeAllItems();
        requestJsonArea.setText("");
        timeoutField.setText("30");
        useTextFormatCheckBox.setSelected(false);
        customHeadersArea.setText("");
    }
    
    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
    }
    
    private JPanel createMainPanel() {
        VerticalPanel mainPanel = new VerticalPanel();
        
        // Proto file section
        mainPanel.add(createProtoFilePanel());
        
        // Server configuration section
        mainPanel.add(createServerPanel());
        
        // Service and method selection
        mainPanel.add(createServicePanel());
        
        // Request configuration
        mainPanel.add(createRequestPanel());
        
        // Advanced options
        mainPanel.add(createAdvancedPanel());
        
        return mainPanel;
    }
    
    private JPanel createProtoFilePanel() {
        JPanel panel = new HorizontalPanel();
        panel.setBorder(createTitledBorder("Proto File Configuration"));
        
        panel.add(new JLabel("Proto File Path:"));
        protoFilePathField = new JTextField(30);
        panel.add(protoFilePathField);
        
        browseButton = new JButton("Browse...");
        browseButton.addActionListener(new BrowseAction());
        panel.add(browseButton);
        
        JButton parseButton = new JButton("Parse");
        parseButton.addActionListener(new ParseAction());
        panel.add(parseButton);
        
        return panel;
    }
    
    private JPanel createServerPanel() {
        JPanel panel = new HorizontalPanel();
        panel.setBorder(createTitledBorder("Server Configuration"));
        
        panel.add(new JLabel("Server URL:"));
        serverUrlField = new JTextField("http://localhost:8080", 25);
        panel.add(serverUrlField);
        
        panel.add(new JLabel("Timeout (seconds):"));
        timeoutField = new JTextField("30", 5);
        panel.add(timeoutField);
        
        return panel;
    }
    
    private JPanel createServicePanel() {
        JPanel panel = new HorizontalPanel();
        panel.setBorder(createTitledBorder("Service and Method"));
        
        panel.add(new JLabel("Service:"));
        serviceComboBox = new JComboBox<>();
        serviceComboBox.addActionListener(new ServiceSelectionAction());
        panel.add(serviceComboBox);
        
        panel.add(new JLabel("Method:"));
        methodComboBox = new JComboBox<>();
        panel.add(methodComboBox);
        
        return panel;
    }
    
    private JPanel createRequestPanel() {
        VerticalPanel panel = new VerticalPanel();
        panel.setBorder(createTitledBorder("Request Configuration"));
        
        panel.add(new JLabel("Request JSON:"));
        requestJsonArea = new JTextArea(8, 50);
        requestJsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestJsonArea.setText("{\n  \n}");
        JScrollPane scrollPane = new JScrollPane(requestJsonArea);
        panel.add(scrollPane);
        
        return panel;
    }
    
    private JPanel createAdvancedPanel() {
        VerticalPanel panel = new VerticalPanel();
        panel.setBorder(createTitledBorder("Advanced Options"));
        
        // Use text format checkbox
        useTextFormatCheckBox = new JCheckBox("Use gRPC-Web Text Format (Base64 encoded)");
        panel.add(useTextFormatCheckBox);
        
        // Custom headers
        panel.add(new JLabel("Custom Headers (one per line, format: Name: Value):"));
        customHeadersArea = new JTextArea(4, 50);
        customHeadersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane headersScrollPane = new JScrollPane(customHeadersArea);
        panel.add(headersScrollPane);
        
        return panel;
    }
    
    private Border createTitledBorder(String title) {
        Border margin = BorderFactory.createEmptyBorder(10, 10, 5, 10);
        Border titled = BorderFactory.createTitledBorder(title);
        return BorderFactory.createCompoundBorder(titled, margin);
    }
    
    private class BrowseAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".proto");
                }
                
                @Override
                public String getDescription() {
                    return "Protocol Buffer Files (*.proto)";
                }
            });
            
            int result = fileChooser.showOpenDialog(GrpcWebSamplerGui.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                protoFilePathField.setText(selectedFile.getAbsolutePath());
            }
        }
    }
    
    private class ParseAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String protoPath = protoFilePathField.getText().trim();
            if (protoPath.isEmpty()) {
                JOptionPane.showMessageDialog(GrpcWebSamplerGui.this,
                    "Please select a proto file first.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            try {
                // Use the static method to get/create parser - SHARED WITH SAMPLER!
                ProtoFileParser parser = GrpcWebSampler.getProtoParserForPath(protoPath);
                
                if (parser == null) {
                    throw new Exception("Failed to parse proto file");
                }
                
                // Update service combo box
                serviceComboBox.removeAllItems();
                Set<String> services = parser.getServices();
                
                for (String service : services) {
                    serviceComboBox.addItem(service);
                }
                
                // Store reference for method loading
                protoParser = parser;
                
                if (!services.isEmpty()) {
                    // Show debug info
                    String debugInfo = parser.getDebugInfo();
                    System.out.println(debugInfo);
                    
                    JOptionPane.showMessageDialog(GrpcWebSamplerGui.this,
                        "Proto file parsed successfully!\n\nFound " + services.size() + " service(s):\n" + 
                        String.join(", ", services) + "\n\nServices and methods are now cached for test execution.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(GrpcWebSamplerGui.this,
                        "No services found in the proto file.\n\nPlease check:\n" +
                        "1. File contains service definitions\n" +
                        "2. Service blocks are properly formatted\n" +
                        "3. File is not corrupted", 
                        "Warning", JOptionPane.WARNING_MESSAGE);
                }
                
            } catch (Exception ex) {
                log.error("Error parsing proto file", ex);
                JOptionPane.showMessageDialog(GrpcWebSamplerGui.this,
                    "Error parsing proto file:\n\n" + ex.getMessage() + 
                    "\n\nPlease check:\n" +
                    "1. File path is correct\n" +
                    "2. File is readable\n" +
                    "3. Proto syntax is valid", 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private class ServiceSelectionAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String selectedService = (String) serviceComboBox.getSelectedItem();
            if (selectedService != null && protoParser != null) {
                methodComboBox.removeAllItems();
                try {
                    List<String> methods = protoParser.getMethodsForService(selectedService);
                    for (String method : methods) {
                        methodComboBox.addItem(method);
                    }
                } catch (Exception ex) {
                    log.error("Error loading methods for service: " + selectedService, ex);
                }
            }
        }
    }
}