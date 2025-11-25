#!/usr/bin/env groovy
/**
 * LaunchWithREST.groovy
 *
 * Launches the SysMLv2Explorer with an embedded REST API for testing
 */

import com.sun.net.httpserver.*
import java.util.concurrent.*
import com.google.gson.*

// Start the GUI
def output = "groovy SysMLv2Explorer.groovy".execute(null, new File("."))

// Wait for GUI to initialize
Thread.sleep(5000)

// Start REST server
def server = HttpServer.create(new InetSocketAddress(8765), 0)
def gson = new GsonBuilder().setPrettyPrinting().create()

// Simple ping endpoint
server.createContext("/ping") { exchange ->
    def response = [status: "ok", message: "REST API is running"]
    String json = gson.toJson(response)
    exchange.responseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(200, json.bytes.length)
    exchange.responseBody.write(json.bytes)
    exchange.responseBody.close()
}

// Check diagnostic log
server.createContext("/diagnostics") { exchange ->
    def logFile = new File("../diagnostics/explorer_diagnostic.log")
    def response = [
        exists: logFile.exists(),
        lastModified: logFile.lastModified(),
        lastLines: logFile.exists() ? logFile.readLines().takeRight(20) : []
    ]
    String json = gson.toJson(response)
    exchange.responseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(200, json.bytes.length)
    exchange.responseBody.write(json.bytes)
    exchange.responseBody.close()
}

server.executor = Executors.newFixedThreadPool(2)
server.start()

println ""
println "GUI launched. REST API running on http://localhost:8765"
println "Test with: curl http://localhost:8765/ping"
println "View logs: curl http://localhost:8765/diagnostics"
println ""

// Forward output
output.consumeProcessOutput(System.out, System.err)
output.waitFor()

server.stop(0)
