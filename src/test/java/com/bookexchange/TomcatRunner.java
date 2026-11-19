package com.bookexchange;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

/**
 * ============================================================================
 * TomcatRunner - Embedded Apache Tomcat 10.1 Local Development Server
 * ============================================================================
 * Allows running the entire Book4u application locally on localhost:8080
 * without needing an external Tomcat manual installation.
 * Uses exact Tomcat 10.1 and Servlet 6.0 engine.
 * ============================================================================
 */
public class TomcatRunner {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        File tempDir = new File("target/tomcat-temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(tempDir.getAbsolutePath());
        tomcat.getConnector(); // Initialize default HTTP connector

        // Deploy web application to /Book4u
        String webappDir = new File("src/main/webapp").getAbsolutePath();
        StandardContext ctx = (StandardContext) tomcat.addWebapp("/Book4u", webappDir);
        ctx.setParentClassLoader(TomcatRunner.class.getClassLoader());

        // Bind compiled classes from target/classes
        File classesDir = new File("target/classes");
        if (classesDir.exists()) {
            WebResourceRoot resources = new StandardRoot(ctx);
            resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                    classesDir.getAbsolutePath(), "/"));
            ctx.setResources(resources);
        }

        // Also bind to root context ("/") so user can access directly at http://localhost:8080/
        StandardContext rootCtx = (StandardContext) tomcat.addWebapp("", webappDir);
        rootCtx.setParentClassLoader(TomcatRunner.class.getClassLoader());
        if (classesDir.exists()) {
            WebResourceRoot rootResources = new StandardRoot(rootCtx);
            rootResources.addPreResources(new DirResourceSet(rootResources, "/WEB-INF/classes",
                    classesDir.getAbsolutePath(), "/"));
            rootCtx.setResources(rootResources);
        }

        System.out.println("==================================================================");
        System.out.println("  Student Peer-to-Peer Book Exchange System (Book4u)");
        System.out.println("  Apache Tomcat 10.1 Server Started Successfully!");
        System.out.println("  Access the platform at: http://localhost:" + port + "/Book4u/");
        System.out.println("  Direct root access at:  http://localhost:" + port + "/");
        System.out.println("==================================================================");

        tomcat.start();
        tomcat.getServer().await();
    }
}
