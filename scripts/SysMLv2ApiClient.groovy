#!/usr/bin/env groovy
/**
 * SysMLv2 API Client - Groovy Script
 *
 * This script connects to the Dassault SysMLv2 API to:
 * 1. List all available projects
 * 2. Allow user to select a project
 * 3. Retrieve all requirements from the selected project
 *
 * API Base: https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api
 */

@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
@Grab('org.apache.httpcomponents:httpclient:4.5.13')

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import javax.net.ssl.*
import java.security.cert.X509Certificate

class SysMLv2ApiClient {

    // Configuration
    String baseUrl = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    String username
    String password
    JsonSlurper jsonSlurper = new JsonSlurper()

    // Diagnostic file for logging
    File diagnosticFile

    SysMLv2ApiClient(String username, String password) {
        this.username = username
        this.password = password

        // Setup diagnostic logging
        def timestamp = new Date().format("yyyyMMdd_HHmmss")
        diagnosticFile = new File("diagnostics/sysmlv2_api_${timestamp}.log")
        diagnosticFile.parentFile?.mkdirs()

        // Trust all certificates (for self-signed certs)
        trustAllCertificates()

        log("SysMLv2 API Client initialized")
        log("Base URL: ${baseUrl}")
    }

    /**
     * Configure SSL to trust all certificates (needed for self-signed certs)
     */
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

    /**
     * Log message to diagnostic file and console
     */
    void log(String message) {
        def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
        def logLine = "[${timestamp}] ${message}"
        println logLine
        diagnosticFile?.append(logLine + "\n")
    }

    /**
     * Make authenticated HTTP GET request
     */
    def httpGet(String path, Map params = [:]) {
        def url = new URL("${baseUrl}${path}")
        if (params) {
            def query = params.collect { k, v -> "${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join('&')
            url = new URL("${baseUrl}${path}?${query}")
        }

        log("GET: ${url}")

        def connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")

        // Basic auth
        def auth = "${username}:${password}".bytes.encodeBase64().toString()
        connection.setRequestProperty("Authorization", "Basic ${auth}")

        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        try {
            def responseCode = connection.responseCode
            log("Response Code: ${responseCode}")

            if (responseCode == 200) {
                def response = connection.inputStream.text
                return jsonSlurper.parseText(response)
            } else {
                def errorResponse = connection.errorStream?.text ?: "No error details"
                log("ERROR: ${errorResponse}")
                return null
            }
        } catch (Exception e) {
            log("ERROR: ${e.message}")
            return null
        }
    }

    /**
     * Get all projects
     */
    List getProjects() {
        log("Fetching all projects...")
        def projects = httpGet("/projects")
        if (projects) {
            log("Found ${projects.size()} projects")
        }
        return projects ?: []
    }

    /**
     * Get commits for a project
     */
    List getCommits(String projectId) {
        log("Fetching commits for project: ${projectId}")
        def commits = httpGet("/projects/${projectId}/commits")
        if (commits instanceof List) {
            log("Found ${commits.size()} commits")
            return commits
        } else if (commits?.error) {
            log("Not authorized for this project: ${commits.error}")
            return null
        }
        return []
    }

    /**
     * Get branches for a project
     */
    List getBranches(String projectId) {
        log("Fetching branches for project: ${projectId}")
        def branches = httpGet("/projects/${projectId}/branches")
        if (branches instanceof List) {
            log("Found ${branches.size()} branches")
            return branches
        }
        return []
    }

    /**
     * Get elements from a commit
     */
    List getElements(String projectId, String commitId, int pageSize = 500) {
        log("Fetching elements for commit: ${commitId}")
        def elements = httpGet("/projects/${projectId}/commits/${commitId}/elements", ["page[size]": pageSize])
        if (elements instanceof List) {
            log("Found ${elements.size()} elements")
            return elements
        } else if (elements?.error) {
            log("Error fetching elements: ${elements.error}")
            return null
        }
        return []
    }

    /**
     * Get all elements with pagination
     */
    List getAllElements(String projectId, String commitId) {
        def allElements = []
        def pageSize = 500
        def offset = 0
        def hasMore = true

        while (hasMore) {
            def elements = httpGet("/projects/${projectId}/commits/${commitId}/elements",
                ["page[size]": pageSize, "page[after]": offset])

            if (elements instanceof List && elements.size() > 0) {
                allElements.addAll(elements)
                offset += elements.size()
                hasMore = elements.size() == pageSize
                log("Fetched ${allElements.size()} elements so far...")
            } else {
                hasMore = false
            }
        }

        log("Total elements fetched: ${allElements.size()}")
        return allElements
    }

    /**
     * Filter elements by type
     */
    List filterByType(List elements, String... types) {
        def typeSet = types.toList().toSet()
        return elements.findAll { element ->
            typeSet.contains(element.'@type')
        }
    }

    /**
     * Get requirements from elements (RequirementUsage, RequirementDefinition)
     */
    List getRequirements(List elements) {
        def requirementTypes = [
            'RequirementUsage',
            'RequirementDefinition',
            'ConcernUsage',
            'ConcernDefinition',
            'StakeholderMembership'
        ]
        return filterByType(elements, *requirementTypes)
    }

    /**
     * Display projects in a formatted table
     */
    void displayProjects(List projects) {
        println "\n" + "=" * 80
        println "AVAILABLE PROJECTS"
        println "=" * 80
        println String.format("%-4s | %-40s | %-30s", "#", "Name", "Created")
        println "-" * 80

        projects.eachWithIndex { project, idx ->
            def name = project.name?.take(40) ?: "Unnamed"
            def created = project.created?.take(10) ?: "Unknown"
            println String.format("%-4d | %-40s | %-30s", idx + 1, name, created)
        }
        println "=" * 80
    }

    /**
     * Display elements summary
     */
    void displayElementsSummary(List elements) {
        println "\n" + "=" * 80
        println "ELEMENTS SUMMARY"
        println "=" * 80

        def typeCounts = elements.groupBy { it.'@type' }
            .collectEntries { type, items -> [(type): items.size()] }
            .sort { -it.value }

        println String.format("%-40s | %s", "Element Type", "Count")
        println "-" * 60
        typeCounts.each { type, count ->
            println String.format("%-40s | %d", type, count)
        }
        println "=" * 80
    }

    /**
     * Display requirements
     */
    void displayRequirements(List requirements) {
        println "\n" + "=" * 80
        println "REQUIREMENTS"
        println "=" * 80

        if (requirements.isEmpty()) {
            println "No requirements found in this project."
            return
        }

        requirements.each { req ->
            println "-" * 80
            println "Type: ${req.'@type'}"
            println "ID: ${req.'@id'}"
            println "Name: ${req.name ?: req.declaredName ?: 'Unnamed'}"
            println "Qualified Name: ${req.qualifiedName ?: 'N/A'}"

            // Look for documentation
            if (req.documentation) {
                println "Documentation: ${req.documentation}"
            }
        }
        println "=" * 80
    }

    /**
     * Interactive project selection
     */
    Map selectProject(List projects) {
        displayProjects(projects)

        print "\nEnter project number (or 'q' to quit): "
        def reader = new BufferedReader(new InputStreamReader(System.in))
        def input = reader.readLine()?.trim()

        if (input?.toLowerCase() == 'q') {
            return null
        }

        try {
            def idx = Integer.parseInt(input) - 1
            if (idx >= 0 && idx < projects.size()) {
                return projects[idx]
            }
        } catch (NumberFormatException e) {
            // Invalid input
        }

        println "Invalid selection. Please try again."
        return selectProject(projects)
    }

    /**
     * Find projects with accessible commits
     */
    List findAccessibleProjects(List projects) {
        log("Checking project access...")
        def accessible = []

        projects.each { project ->
            def commits = getCommits(project.'@id')
            if (commits != null && commits.size() > 0) {
                project.latestCommit = commits.max { it.created }
                project.commitCount = commits.size()
                accessible << project
            }
        }

        log("Found ${accessible.size()} accessible projects")
        return accessible
    }

    /**
     * Main workflow
     */
    void run() {
        log("Starting SysMLv2 API Client")

        // Get all projects
        def projects = getProjects()
        if (!projects) {
            log("Failed to fetch projects. Check credentials and network.")
            return
        }

        // Filter to accessible projects (optional - can be slow)
        println "\nWould you like to filter to only accessible projects? (y/n): "
        def reader = new BufferedReader(new InputStreamReader(System.in))
        def filterChoice = reader.readLine()?.trim()?.toLowerCase()

        if (filterChoice == 'y') {
            println "Checking access to ${projects.size()} projects (this may take a while)..."
            projects = findAccessibleProjects(projects)
        }

        // Select project
        def selectedProject = selectProject(projects)
        if (!selectedProject) {
            log("No project selected. Exiting.")
            return
        }

        log("Selected project: ${selectedProject.name} (${selectedProject.'@id'})")

        // Get commits
        def commits = getCommits(selectedProject.'@id')
        if (!commits || commits.isEmpty()) {
            log("No commits found or access denied for this project.")
            return
        }

        // Use latest commit
        def latestCommit = commits.max { it.created }
        log("Using latest commit: ${latestCommit.name} (${latestCommit.'@id'})")

        // Get elements
        def elements = getElements(selectedProject.'@id', latestCommit.'@id')
        if (elements == null) {
            log("Failed to fetch elements. Access may be denied.")
            return
        }

        // Display summary
        displayElementsSummary(elements)

        // Get and display requirements
        def requirements = getRequirements(elements)
        displayRequirements(requirements)

        // Save results to file
        def outputFile = new File("output/requirements_${selectedProject.name?.replaceAll(/[^a-zA-Z0-9]/, '_')}.json")
        outputFile.parentFile?.mkdirs()
        outputFile.text = JsonOutput.prettyPrint(JsonOutput.toJson([
            project: selectedProject,
            commit: latestCommit,
            requirements: requirements,
            elementsSummary: elements.groupBy { it.'@type' }.collectEntries { [(it.key): it.value.size()] }
        ]))
        log("Results saved to: ${outputFile.absolutePath}")

        log("Done!")
    }
}

// Main execution
if (args.length < 2) {
    println "Usage: groovy SysMLv2ApiClient.groovy <username> <password>"
    println "Example: groovy SysMLv2ApiClient.groovy DBR2 'your-password'"
    System.exit(1)
}

def client = new SysMLv2ApiClient(args[0], args[1])
client.run()
