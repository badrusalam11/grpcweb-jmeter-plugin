#!/bin/bash

# JMeter gRPC-Web Plugin Build Script

echo "Building JMeter gRPC-Web Plugin..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven first."
    exit 1
fi

# Check if JMETER_HOME is set
if [ -z "$JMETER_HOME" ]; then
    echo "Warning: JMETER_HOME environment variable is not set."
    echo "Please set JMETER_HOME to your JMeter installation directory."
    echo "Example: export JMETER_HOME=/path/to/apache-jmeter-5.6.2"
    echo ""
    echo "You can still build the plugin, but you'll need to manually copy it to JMeter's lib/ext directory."
fi

# Clean and build the project
echo "Cleaning and building project..."
mvn clean package

if [ $? -ne 0 ]; then
    echo "Error: Build failed!"
    exit 1
fi

echo "Build successful!"

# Check if the JAR was created
JAR_FILE="target/jmeter-grpc-web-plugin-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    exit 1
fi

echo "Plugin JAR created: $JAR_FILE"

# Install to JMeter if JMETER_HOME is set
if [ -n "$JMETER_HOME" ]; then
    if [ -d "$JMETER_HOME/lib/ext" ]; then
        echo "Installing plugin to JMeter..."
        cp "$JAR_FILE" "$JMETER_HOME/lib/ext/"
        echo "Plugin installed to $JMETER_HOME/lib/ext/"
        echo ""
        echo "To use the plugin:"
        echo "1. Restart JMeter"
        echo "2. Add a Thread Group to your Test Plan"
        echo "3. Right-click Thread Group -> Add -> Sampler -> gRPC-Web Request"
        echo "4. Configure your proto file, server URL, and request details"
    else
        echo "Error: $JMETER_HOME/lib/ext directory not found"
        echo "Please verify your JMETER_HOME path"
    fi
else
    echo ""
    echo "Manual installation instructions:"
    echo "1. Copy $JAR_FILE to your JMeter's lib/ext/ directory"
    echo "2. Restart JMeter"
    echo "3. The gRPC-Web Request sampler will be available under Samplers"
fi

echo ""
echo "Example usage with the provided example.proto:"
echo "1. Server URL: http://localhost:8080"
echo "2. Service: UserService"
echo "3. Method: GetUser"
echo "4. Request JSON: {\"user_id\": 123}"

echo ""
echo "Build completed successfully!"