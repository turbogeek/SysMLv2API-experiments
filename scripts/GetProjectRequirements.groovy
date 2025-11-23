#!/usr/bin/env groovy
/**
 * SysMLv2 API - Get Project Requirements Script
 *
 * Retrieves all requirements from a specified project.
 *
 * Usage: groovy GetProjectRequirements.groovy <username> <password> <project-id>
 *
 * Example:
 *   groovy GetProjectRequirements.groovy DBR2 'password' a0be499b-3c33-45d2-96eb-d383a8900393
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import javax.net.ssl.*
import java.security.cert.X509Certificate

class SysMLv2RequirementsFetcher {

    String baseUrl = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    String username
    String password
    String projectId
    JsonSlurper jsonSlurper = new JsonSlurper()
    File diagnosticFile

    // Requirement-related SysML v2 types
    static final List REQUIREMENT_TYPES = [
        'RequirementUsage',
        'RequirementDefinition',
        'ConcernUsage',
        'ConcernDefinition',
        'ConstraintUsage',
        'ConstraintDefinition',
        'ObjectiveMembership',
        'StakeholderMembership',
        'SubjectMembership',
        'RequirementConstraintMembership',
        'RequirementVerificationMembership',
        'SatisfyRequirementUsage',
        'AssertConstraintUsage'
    ]

    SysMLv2RequirementsFetcher(String username, String password, String projectId) {
        this.username = username
        this.password = password
        this.projectId = projectId

        def timestamp = new Date().format("yyyyMMdd_HHmmss")
        new File("diagnostics").mkdirs()
        diagnosticFile = new File("diagnostics/requirements_${projectId}_${timestamp}.log")

        trustAllCertificates()
        log("Initialized Requirements Fetcher for project: ${projectId}")
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

    def httpGet(String path, Map params = [:]) {
        def queryString = ""
        if (params) {
            queryString = "?" + params.collect { k, v ->
                "${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v.toString(), 'UTF-8')}"
            }.join('&')
        }

        def url = new URL("${baseUrl}${path}${queryString}")
        log("GET: ${url}")

        def connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")

        def auth = "${username}:${password}".bytes.encodeBase64().toString()
        connection.setRequestProperty("Authorization", "Basic ${auth}")
        connection.connectTimeout = 30000
        connection.readTimeout = 120000

        try {
            if (connection.responseCode == 200) {
                def text = connection.inputStream.text
                return jsonSlurper.parseText(text)
            } else {
                def errorText = connection.errorStream?.text ?: "Unknown error"
                log("ERROR: HTTP ${connection.responseCode} - ${errorText}")
                return [error: errorText, status: connection.responseCode]
            }
        } catch (Exception e) {
            log("ERROR: ${e.message}")
            return [error: e.message]
        }
    }

    Map getProject() {
        log("Fetching project details...")
        def projects = httpGet("/projects")
        if (projects instanceof List) {
            return projects.find { it.'@id' == projectId }
        }
        return null
    }

    List getCommits() {
        log("Fetching commits...")
        def result = httpGet("/projects/${projectId}/commits")
        if (result instanceof List) {
            log("Found ${result.size()} commits")
            return result
        }
        log("Error getting commits: ${result?.error}")
        return null
    }

    List getElements(String commitId, int pageSize = 500) {
        log("Fetching elements from commit ${commitId}...")
        def result = httpGet("/projects/${projectId}/commits/${commitId}/elements", ["page[size]": pageSize])

        if (result instanceof List) {
            log("Found ${result.size()} elements")
            return result
        }
        log("Error getting elements: ${result?.error}")
        return null
    }

    List filterRequirements(List elements) {
        def requirements = elements.findAll { element ->
            REQUIREMENT_TYPES.contains(element.'@type')
        }
        log("Found ${requirements.size()} requirement-related elements")
        return requirements
    }

    void displayElementTypes(List elements) {
        println "\n" + "=" * 60
        println "ELEMENT TYPES SUMMARY"
        println "=" * 60

        def typeCounts = elements.groupBy { it.'@type' }
            .collectEntries { [(it.key): it.value.size()] }
            .sort { -it.value }

        println String.format("%-40s | %s", "Type", "Count")
        println "-" * 60
        typeCounts.each { type, count ->
            println String.format("%-40s | %d", type, count)
        }
        println "=" * 60
    }

    void displayRequirements(List requirements) {
        println "\n" + "=" * 80
        println "REQUIREMENTS FOUND"
        println "=" * 80

        if (requirements.isEmpty()) {
            println "No requirement-type elements found in this project."
            println "\nRequirement types searched for:"
            REQUIREMENT_TYPES.each { println "  - ${it}" }
            return
        }

        requirements.eachWithIndex { req, idx ->
            println "\n--- Requirement ${idx + 1} ---"
            println "Type:           ${req.'@type'}"
            println "ID:             ${req.'@id'}"
            println "Name:           ${req.name ?: req.declaredName ?: 'N/A'}"
            println "Short Name:     ${req.shortName ?: req.declaredShortName ?: 'N/A'}"
            println "Qualified Name: ${req.qualifiedName ?: 'N/A'}"

            if (req.documentation) {
                println "Documentation:"
                req.documentation.each { doc ->
                    println "  ${doc}"
                }
            }

            if (req.ownedElement) {
                println "Owned Elements: ${req.ownedElement.size()}"
            }
        }
        println "\n" + "=" * 80
    }

    void run() {
        log("Starting Requirements Fetch for project: ${projectId}")

        // Get project info
        def project = getProject()
        if (!project) {
            log("Project not found: ${projectId}")
            return
        }
        log("Project Name: ${project.name}")

        // Get commits
        def commits = getCommits()
        if (!commits) {
            log("Could not get commits - check project access permissions")
            return
        }

        // Get latest commit
        def latestCommit = commits.max { it.created }
        log("Latest commit: ${latestCommit.name} (${latestCommit.description})")
        log("Commit ID: ${latestCommit.'@id'}")

        // Get elements
        def elements = getElements(latestCommit.'@id')
        if (!elements) {
            log("Could not get elements - check resource access permissions")
            return
        }

        // Display element types
        displayElementTypes(elements)

        // Filter and display requirements
        def requirements = filterRequirements(elements)
        displayRequirements(requirements)

        // Save results
        new File("output").mkdirs()
        def safeName = project.name?.replaceAll(/[^a-zA-Z0-9]/, '_') ?: projectId
        def outputFile = new File("output/requirements_${safeName}.json")
        outputFile.text = JsonOutput.prettyPrint(JsonOutput.toJson([
            project: [
                id: project.'@id',
                name: project.name,
                created: project.created
            ],
            commit: [
                id: latestCommit.'@id',
                name: latestCommit.name,
                description: latestCommit.description,
                created: latestCommit.created
            ],
            elementsSummary: elements.groupBy { it.'@type' }.collectEntries { [(it.key): it.value.size()] },
            requirements: requirements.collect { req ->
                [
                    type: req.'@type',
                    id: req.'@id',
                    name: req.name ?: req.declaredName,
                    qualifiedName: req.qualifiedName,
                    shortName: req.shortName ?: req.declaredShortName
                ]
            }
        ]))

        log("\nResults saved to: ${outputFile.absolutePath}")
        log("Diagnostic log: ${diagnosticFile.absolutePath}")
        log("Done!")
    }
}

// Main
if (args.length < 3) {
    println """
Usage: groovy GetProjectRequirements.groovy <username> <password> <project-id>

Example:
  groovy GetProjectRequirements.groovy DBR2 'password' a0be499b-3c33-45d2-96eb-d383a8900393

To find project IDs, run ListProjects.groovy first.
"""
    System.exit(1)
}

new SysMLv2RequirementsFetcher(args[0], args[1], args[2]).run()
