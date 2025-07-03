# JMeter gRPC-Web Plugin

A JMeter plugin that enables testing gRPC-Web services with proto file support, similar to Kreya's user experience.

## Features

- **Proto File Support**: Load `.proto` files and automatically populate services and methods
- **JSON Request Format**: Write requests in JSON format (no need to deal with protobuf directly)
- **gRPC-Web Protocol**: Full support for gRPC-Web binary and text formats
- **Custom Headers**: Add custom headers for authentication, etc.
- **Response Validation**: Built-in response parsing and validation
- **JMeter Integration**: Seamless integration with JMeter's test plans and reporting

## Installation

### Prerequisites

- Java 8 or higher
- Apache Maven 3.6+
- Apache JMeter 5.4+
- Protoc: [download](https://github.com/protocolbuffers/protobuf/releases)

### Building from Source

1. Clone or download the project
2. Set your `JMETER_HOME` environment variable:
   ```bash
   export JMETER_HOME=/path/to/apache-jmeter-5.6.2
   ```

3. Run the build script:
   ```bash
   chmod +x build.sh
   ./build.sh
   ```

4. The plugin will be automatically installed to your JMeter installation

### Manual Installation

1. Build the project:
   ```bash
   mvn clean package
   ```

2. Copy the generated JAR to JMeter:
   ```bash
   cp target/jmeter-grpc-web-plugin-1.0.0.jar $JMETER_HOME/lib/ext/
   ```

3. Restart JMeter

## Usage

### 1. Add gRPC-Web Request Sampler

1. Open JMeter
2. Create a Test Plan and Thread Group
3. Right-click Thread Group → Add → Sampler → **gRPC-Web Request**

### 2. Configure Proto File

1. Click "Browse..." to select your `.proto` file
2. Click "Parse" to load services and methods
3. Select your service and method from the dropdowns

### 3. Configure Server

1. Enter your gRPC-Web server URL (e.g., `http://localhost:8080`)
2. Set timeout if needed (default: 30 seconds)

### 4. Write Request

Enter your request in JSON format in the "Request JSON" field:

```json
{
  "user_id": 123,
  "name": "John Doe",
  "email": "john@example.com"
}
```

### 5. Advanced Options

- **Use gRPC-Web Text Format**: Enable for base64-encoded transport
- **Custom Headers**: Add headers like authentication tokens:
  ```
  Authorization: Bearer your-token-here
  X-Custom-Header: custom-value
  ```

## Example Usage

Using the provided `example.proto` file:

### GetUser Request
- **Service**: UserService
- **Method**: GetUser
- **Request JSON**:
  ```json
  {
    "user_id": 123
  }
  ```

### CreateUser Request
- **Service**: UserService
- **Method**: CreateUser
- **Request JSON**:
  ```json
  {
    "user": {
      "name": "Alice Smith",
      "email": "alice@example.com",
      "age": 30,
      "tags": ["developer", "team-lead"],
      "metadata": {
        "department": "engineering",
        "location": "remote"
      }
    }
  }
  ```

### ListUsers Request
- **Service**: UserService
- **Method**: ListUsers
- **Request JSON**:
  ```json
  {
    "page": 1,
    "page_size": 10,
    "search": "john"
  }
  ```

## Server Setup for Testing

To test the plugin, you'll need a gRPC-Web server. Here's a simple example using Node.js:

```javascript
// server.js - Simple gRPC-Web server for testing
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');

const packageDefinition = protoLoader.loadSync('example.proto');
const userService = grpc.loadPackageDefinition(packageDefinition).example;

const server = new grpc.Server();

server.addService(userService.UserService.service, {
  GetUser: (call, callback) => {
    const userId = call.request.user_id;
    callback(null, {
      user: {
        id: userId,
        name: "Test User",
        email: "test@example.com",
        age: 25
      },
      found: true
    });
  },
  // ... implement other methods
});

server.bindAsync('0.0.0.0:50051', grpc.ServerCredentials.createInsecure(), () => {
  server.start();
  console.log('gRPC server running on port 50051');
});
```

## Troubleshooting

### Common Issues

1. **"Proto file not found"**: Ensure the proto file path is correct and accessible
2. **"Service not found"**: Check that the proto file was parsed successfully
3. **"Connection refused"**: Verify your gRPC-Web server is running and accessible
4. **"Invalid JSON"**: Validate your request JSON format

### Debug Tips

1. Check JMeter logs for detailed error messages
2. Use JMeter's View Results Tree to see request/response details
3. Verify your gRPC-Web server supports the expected content types
4. Test with simple requests first before complex nested messages

## Limitations

- Currently supports unary RPCs (streaming support planned for future versions)
- Proto file parsing is simplified (complex imports may not work)
- Response display shows raw bytes (JSON formatting planned)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review JMeter logs for error details
3. Create an issue in the project repository    