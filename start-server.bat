@echo off
title Book4u - Local Development Server (Apache Tomcat 10.1)
echo ==================================================================
echo   Student Peer-to-Peer Book Exchange System (Book4u)
echo   Starting local server using Apache Tomcat 10.1 on port 8080...
echo ==================================================================
echo.
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
    )
)
call .\mvnw.cmd test-compile exec:java
pause
