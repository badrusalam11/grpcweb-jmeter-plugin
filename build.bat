@echo off
REM JMeter gRPC-Web Plugin Build Script for Windows

echo Building JMeter gRPC-Web Plugin...

REM Check if Maven is installed
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Maven is not installed or not in PATH.
    echo Please install Maven and ensure it's in your PATH.
    echo Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Check if JMETER_HOME is set
if "%JMETER_HOME%"=="" (
    echo Warning: JMETER_HOME environment variable is not set.
    echo Please set JMETER_HOME to your JMeter installation directory.
    echo Example: set JMETER_HOME=C:\apache-jmeter-5.6.2
    echo.
    echo You can still build the plugin, but you'll need to manually copy it to JMeter's lib\ext directory.
    echo.
)

REM Clean and build the project
echo Cleaning and building project...
call mvn clean package

if %errorlevel% neq 0 (
    echo Error: Build failed!
    pause
    exit /b 1
)

echo Build successful!

REM Check if the JAR was created
set JAR_FILE=target\jmeter-grpc-web-plugin-1.0.0.jar
if not exist "%JAR_FILE%" (
    echo Error: JAR file not found at %JAR_FILE%
    pause
    exit /b 1
)

echo Plugin JAR created: %JAR_FILE%

REM Install to JMeter if JMETER_HOME is set
if not "%JMETER_HOME%"=="" (
    if exist "%JMETER_HOME%\lib\ext\" (
        echo Installing plugin to JMeter...
        copy "%JAR_FILE%" "%JMETER_HOME%\lib\ext\" >nul
        if %errorlevel% equ 0 (
            echo Plugin installed to %JMETER_HOME%\lib\ext\
            echo.
            echo To use the plugin:
            echo 1. Restart JMeter
            echo 2. Add a Thread Group to your Test Plan
            echo 3. Right-click Thread Group -^> Add -^> Sampler -^> gRPC-Web Request
            echo 4. Configure your proto file, server URL, and request details
        ) else (
            echo Error: Failed to copy plugin to JMeter directory.
            echo Please check permissions and try running as administrator.
        )
    ) else (
        echo Error: %JMETER_HOME%\lib\ext directory not found
        echo Please verify your JMETER_HOME path
    )
) else (
    echo.
    echo Manual installation instructions:
    echo 1. Copy %JAR_FILE% to your JMeter's lib\ext\ directory
    echo 2. Restart JMeter
    echo 3. The gRPC-Web Request sampler will be available under Samplers
)

echo.
echo Example usage with the provided example.proto:
echo 1. Server URL: http://localhost:8080
echo 2. Service: UserService
echo 3. Method: GetUser
echo 4. Request JSON: {"user_id": 123}

echo.
echo Build completed successfully!
echo.
pause