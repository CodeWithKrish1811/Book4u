# ===================================================================
# Dockerfile for Book4u (Student Peer-to-Peer Book Exchange System)
# Multi-stage production build for Railway / Cloud Deployment
# Base: Eclipse Temurin Java 21 LTS + Apache Tomcat 10.1
# ===================================================================

# Stage 1: Build the WAR artifact using Maven and Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy dependency definition and source code
COPY pom.xml .
COPY src ./src
COPY database ./database

# Build the production WAR archive
RUN mvn clean package -DskipTests

# Stage 2: Deploy and run on Apache Tomcat 10.1 (Jakarta Servlet 6.0 compatible)
FROM tomcat:10.1-jdk21-temurin

# Clean default Tomcat web applications
RUN rm -rf /usr/local/tomcat/webapps/*

# Deploy Book4u as the ROOT application so it serves directly from /
COPY --from=build /app/target/Book4u.war /usr/local/tomcat/webapps/ROOT.war

# Expose standard port (dynamically overridden by Railway via $PORT)
EXPOSE 8080

# Dynamically configure server.xml with Railway's $PORT at runtime, then start Tomcat
CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT:-8080}\\\"/g\" /usr/local/tomcat/conf/server.xml && exec catalina.sh run"]
