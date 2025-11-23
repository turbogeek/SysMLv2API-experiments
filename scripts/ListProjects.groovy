#!/usr/bin/env groovy
/**
 * SysMLv2 API - List Projects Script
 *
 * Simple script to list all available projects from the SysMLv2 API.
 * This is a non-interactive version for testing and automation.
 *
 * Usage: groovy ListProjects.groovy <username> <password>
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import javax.net.ssl.*
import java.security.cert.X509Certificate

class SysMLv2ProjectLister {

    String baseUrl = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    String username
    String password
    JsonSlurper jsonSlurper = new JsonSlurper()
    File diagnosticFile

    SysMLv2ProjectLister(String username, String password) {
        this.username = username
        this.password = password

        // Setup diagnostic logging
        def timestamp = new Date().format("yyyyMMdd_HHmmss")
        new File("diagnostics").mkdirs()
        diagnosticFile = new File("diagnostics/list_projects_${timestamp}.log")

        trustAllCertificates()
        log("Initialized SysMLv2 Project Lister")
    }

    void trustAllCertificates() {
        def trustAllCerts = [
            checkClientTrusted: { chain, authType -> },
            checkServerTrusted: { chain, authType -> },
            getAcceptedIssuers: { null }
        ] as X509TrustManager

        def sc = SSLContext.getInstance("TLS")
        sc.init(null, [trustAllCerts] as TrustManager[], new java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
        HttpsURLConnection.setDefaultHostnameVerifier({ hostname, session -> true } as HostnameVerifier)
    }

    void log(String message) {
        def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
        def logLine = "[${timestamp}] ${message}"
        println logLine
        diagnosticFile?.append(logLine + "\n")
    }

    def httpGet(String path) {
        def url = new URL("${baseUrl}${path}")
        log("GET: ${url}")

        def connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")

        def auth = "${username}:${password}".bytes.encodeBase64().toString()
        connection.setRequestProperty("Authorization", "Basic ${auth}")
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        try {
            if (connection.responseCode == 200) {
                return jsonSlurper.parseText(connection.inputStream.text)
            } else {
                log("ERROR: HTTP ${connection.responseCode}")
                return null
            }
        } catch (Exception e) {
            log("ERROR: ${e.message}")
            return null
        }
    }

    void run() {
        log("Fetching projects from SysMLv2 API...")

        def projects = httpGet("/projects")

        if (!projects) {
            log("Failed to fetch projects")
            return
        }

        log("Found ${projects.size()} projects\n")

        println "=" * 100
        println String.format("%-4s | %-50s | %-36s | %s", "#", "Name", "ID", "Created")
        println "-" * 100

        projects.sort { it.name }.eachWithIndex { project, idx ->
            def name = (project.name ?: "Unnamed").take(50)
            def id = project.'@id' ?: "N/A"
            def created = project.created?.take(10) ?: "Unknown"
            println String.format("%-4d | %-50s | %-36s | %s", idx + 1, name, id, created)
        }

        println "=" * 100

        // Save to JSON file
        new File("output").mkdirs()
        def outputFile = new File("output/projects.json")
        outputFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(projects))
        log("\nProjects saved to: ${outputFile.absolutePath}")
    }
}

// Main
if (args.length < 2) {
    println "Usage: groovy ListProjects.groovy <username> <password>"
    System.exit(1)
}

new SysMLv2ProjectLister(args[0], args[1]).run()
