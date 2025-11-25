#!/usr/bin/env groovy
/**
 * ExplorerRESTServer.groovy
 *
 * Adds a REST API interface to SysMLv2Explorer for automated testing and control.
 * Starts an embedded HTTP server on port 8765 for remote control.
 *
 * REST Endpoints:
 *   GET  /status              - Get current GUI state
 *   GET  /projects            - List all projects in dropdown
 *   POST /selectProject/{id}  - Select a project by ID
 *   GET  /tree                - Get current tree structure
 *   POST /expandNode/{path}   - Expand a tree node
 *   POST /selectNode/{path}   - Select a tree node
 *   GET  /sysmlText           - Get current SysML text
 *   POST /shutdown            - Close GUI
 */

@Grab('org.apache.httpcomponents:httpclient:4.5.14')
@Grab('org.apache.httpcomponents:httpcore:4.4.16')
@Grab('com.google.code.gson:gson:2.10.1')

import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import org.apache.http.auth.*
import org.apache.http.conn.ssl.*
import org.apache.http.ssl.*
import javax.net.ssl.*
import java.security.cert.X509Certificate
import com.google.gson.*

import javax.swing.*
import javax.swing.tree.*
import javax.swing.event.*
import javax.swing.border.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.*
import java.util.List

import com.sun.net.httpserver.*
import java.util.concurrent.*

// Load the main SysMLv2ExplorerFrame class
evaluate(new File("SysMLv2Explorer.groovy"))
evaluate(new File("CredentialsHelper.groovy"))

class ExplorerRESTServer {
    static final int PORT = 8765
    HttpServer server
    SysMLv2ExplorerFrame frame
    Gson gson = new GsonBuilder().setPrettyPrinting().create()

    ExplorerRESTServer(SysMLv2ExplorerFrame frame) {
        this.frame = frame
    }

    void start() {
        server = HttpServer.create(new InetSocketAddress(PORT), 0)

        // Status endpoint
        server.createContext("/status") { exchange ->
            def response = [
                running: true,
                projectsLoaded: frame.projectCombo.itemCount,
                currentProject: frame.currentProjectId ?: 'none',
                treeNodes: countTreeNodes(),
                statusText: getStatusText()
            ]
            sendJSON(exchange, response)
        }

        // List projects
        server.createContext("/projects") { exchange ->
            def projects = []
            for (int i = 0; i < frame.projectCombo.itemCount; i++) {
                def item = frame.projectCombo.getItemAt(i)
                projects << [id: item.id, name: item.name]
            }
            sendJSON(exchange, projects)
        }

        // Select project
        server.createContext("/selectProject") { exchange ->
            if (exchange.requestMethod == "POST") {
                String path = exchange.requestURI.path
                String projectId = path.substring(path.lastIndexOf('/') + 1)

                SwingUtilities.invokeLater {
                    for (int i = 0; i < frame.projectCombo.itemCount; i++) {
                        def item = frame.projectCombo.getItemAt(i)
                        if (item.id == projectId) {
                            frame.projectCombo.setSelectedIndex(i)
                            break
                        }
                    }
                }

                Thread.sleep(2000) // Wait for load
                sendJSON(exchange, [status: 'ok', projectId: projectId])
            } else {
                sendError(exchange, 405, "Method not allowed")
            }
        }

        // Get tree structure
        server.createContext("/tree") { exchange ->
            def tree = buildTreeJSON(frame.elementTree.model.root)
            sendJSON(exchange, tree)
        }

        // Get SysML text
        server.createContext("/sysmlText") { exchange ->
            String text = frame.sysmlTextArea.text ?: ""
            sendJSON(exchange, [text: text, length: text.length()])
        }

        // Shutdown
        server.createContext("/shutdown") { exchange ->
            if (exchange.requestMethod == "POST") {
                sendJSON(exchange, [status: 'shutting down'])
                SwingUtilities.invokeLater {
                    frame.dispose()
                    System.exit(0)
                }
            } else {
                sendError(exchange, 405, "Method not allowed")
            }
        }

        server.executor = Executors.newFixedThreadPool(2)
        server.start()
        println "REST API server started on port ${PORT}"
        println "Test with: curl http://localhost:${PORT}/status"
    }

    void stop() {
        server?.stop(0)
    }

    int countTreeNodes() {
        def root = frame.elementTree.model.root
        return root ? countNodes(root) : 0
    }

    int countNodes(def node) {
        int count = 1
        for (int i = 0; i < node.childCount; i++) {
            count += countNodes(node.getChildAt(i))
        }
        return count
    }

    String getStatusText() {
        return frame.statusLabel?.text ?: ""
    }

    Map buildTreeJSON(def node) {
        def result = [
            label: node.toString(),
            children: []
        ]
        for (int i = 0; i < node.childCount; i++) {
            result.children << buildTreeJSON(node.getChildAt(i))
        }
        return result
    }

    void sendJSON(HttpExchange exchange, Object data) {
        String json = gson.toJson(data)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, json.bytes.length)
        exchange.responseBody.write(json.bytes)
        exchange.responseBody.close()
    }

    void sendError(HttpExchange exchange, int code, String message) {
        String json = gson.toJson([error: message])
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, json.bytes.length)
        exchange.responseBody.write(json.bytes)
        exchange.responseBody.close()
    }
}

// Main execution
println "Starting SysMLv2 Explorer with REST API..."

def creds = CredentialsHelper.getCredentials(args)
def frame = new SysMLv2ExplorerFrame(creds.username, creds.password)

// Start REST server
def restServer = new ExplorerRESTServer(frame)
restServer.start()

println ""
println "GUI is running. REST API available at http://localhost:${ExplorerRESTServer.PORT}"
println ""
println "Available endpoints:"
println "  GET  http://localhost:${ExplorerRESTServer.PORT}/status"
println "  GET  http://localhost:${ExplorerRESTServer.PORT}/projects"
println "  POST http://localhost:${ExplorerRESTServer.PORT}/selectProject/{projectId}"
println "  GET  http://localhost:${ExplorerRESTServer.PORT}/tree"
println "  GET  http://localhost:${ExplorerRESTServer.PORT}/sysmlText"
println "  POST http://localhost:${ExplorerRESTServer.PORT}/shutdown"
println ""

// Keep the script running
frame.addWindowListener(new WindowAdapter() {
    void windowClosing(WindowEvent e) {
        restServer.stop()
    }
})
