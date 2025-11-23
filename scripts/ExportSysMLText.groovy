#!/usr/bin/env groovy
/**
 * ExportSysMLText.groovy
 *
 * Exports SysML v2 textual notation from a project via the SysMLv2 API.
 * Since the API doesn't provide a native text export, this script reconstructs
 * the textual representation from the JSON element data.
 *
 * Usage: groovy ExportSysMLText.groovy <username> <password> [projectId] [elementId]
 *   - If no projectId: lists available projects
 *   - If projectId but no elementId: exports entire project
 *   - If both: exports specific element and its children
 *
 * Output: Saves to output/sysml_export_<timestamp>.sysml
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

class SysMLv2TextExporter {

    static final String BASE_URL = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    static final String DIAGNOSTIC_DIR = "../diagnostics"
    static final String OUTPUT_DIR = "../output"

    String username
    String password
    CloseableHttpClient httpClient
    Gson gson = new GsonBuilder().setPrettyPrinting().create()

    // Cache for elements to avoid repeated fetches
    Map<String, Map> elementCache = [:]

    // Indentation tracking
    int indentLevel = 0
    String indentString = "    "

    SysMLv2TextExporter(String username, String password) {
        this.username = username
        this.password = password
        this.httpClient = createTrustingHttpClient()
    }

    CloseableHttpClient createTrustingHttpClient() {
        def trustStrategy = { X509Certificate[] chain, String authType -> true } as TrustStrategy
        def sslContext = SSLContexts.custom()
            .loadTrustMaterial(null, trustStrategy)
            .build()
        def sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)

        def credentialsProvider = new BasicCredentialsProvider()
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

        return HttpClients.custom()
            .setSSLSocketFactory(sslSocketFactory)
            .setDefaultCredentialsProvider(credentialsProvider)
            .build()
    }

    Map apiGet(String endpoint) {
        def request = new HttpGet("${BASE_URL}${endpoint}")
        request.setHeader("Accept", "application/json")

        def response = httpClient.execute(request)
        def content = response.entity.content.text
        response.close()

        return gson.fromJson(content, Map)
    }

    List apiGetList(String endpoint) {
        def request = new HttpGet("${BASE_URL}${endpoint}")
        request.setHeader("Accept", "application/json")

        def response = httpClient.execute(request)
        def content = response.entity.content.text
        response.close()

        return gson.fromJson(content, List)
    }

    /**
     * Fetch element by ID with caching
     */
    Map getElement(String projectId, String commitId, String elementId) {
        String cacheKey = "${projectId}/${commitId}/${elementId}"
        if (elementCache.containsKey(cacheKey)) {
            return elementCache[cacheKey]
        }

        def element = apiGet("/projects/${projectId}/commits/${commitId}/elements/${elementId}")
        elementCache[cacheKey] = element
        return element
    }

    /**
     * Fetch all elements for a commit (paginated)
     */
    List getAllElements(String projectId, String commitId) {
        List allElements = []
        int pageSize = 100
        int offset = 0

        while (true) {
            def elements = apiGetList("/projects/${projectId}/commits/${commitId}/elements?page[size]=${pageSize}&page[after]=${offset}")
            if (elements.isEmpty()) break

            allElements.addAll(elements)
            elements.each { el ->
                String cacheKey = "${projectId}/${commitId}/${el['@id']}"
                elementCache[cacheKey] = el
            }

            if (elements.size() < pageSize) break
            offset += pageSize

            print "."  // Progress indicator
        }
        println ""

        return allElements
    }

    /**
     * Get indent string for current level
     */
    String indent() {
        return indentString * indentLevel
    }

    /**
     * Escape name for SysML v2 syntax (quote if needed)
     */
    String escapeName(String name) {
        if (name == null) return null
        // Quote names with spaces or special characters
        if (name.contains(' ') || name.contains("'") || name.contains('(') || name.contains(')')) {
            return "'${name.replace("'", "\\'")}'"
        }
        return name
    }

    /**
     * Get short name from qualified name
     */
    String getShortName(Map element) {
        String name = element['name'] ?: element['declaredName']
        if (name == null) {
            // Try to extract from qualifiedName
            String qn = element['qualifiedName']
            if (qn) {
                def parts = qn.split("::")
                name = parts[-1]?.replaceAll("^'|'\$", "")
            }
        }
        return name
    }

    /**
     * Convert element type to SysML v2 keyword
     */
    String typeToKeyword(String type) {
        switch (type) {
            case 'Package': return 'package'
            case 'PartDefinition': return 'part def'
            case 'PartUsage': return 'part'
            case 'AttributeDefinition': return 'attribute def'
            case 'AttributeUsage': return 'attribute'
            case 'ItemDefinition': return 'item def'
            case 'ItemUsage': return 'item'
            case 'PortDefinition': return 'port def'
            case 'PortUsage': return 'port'
            case 'InterfaceDefinition': return 'interface def'
            case 'InterfaceUsage': return 'interface'
            case 'ConnectionDefinition': return 'connection def'
            case 'ConnectionUsage': return 'connection'
            case 'ActionDefinition': return 'action def'
            case 'ActionUsage': return 'action'
            case 'StateDefinition': return 'state def'
            case 'StateUsage': return 'state'
            case 'RequirementDefinition': return 'requirement def'
            case 'RequirementUsage': return 'requirement'
            case 'ConstraintDefinition': return 'constraint def'
            case 'ConstraintUsage': return 'constraint'
            case 'ViewDefinition': return 'view def'
            case 'ViewUsage': return 'view'
            case 'ViewpointDefinition': return 'viewpoint def'
            case 'ViewpointUsage': return 'viewpoint'
            case 'RenderingDefinition': return 'rendering def'
            case 'RenderingUsage': return 'rendering'
            case 'Comment': return 'comment'
            case 'Documentation': return 'doc'
            case 'Namespace': return 'namespace'
            default: return "/* ${type} */"
        }
    }

    /**
     * Check if element type should be exported
     */
    boolean isExportableType(String type) {
        def exportable = [
            'Package', 'Namespace',
            'PartDefinition', 'PartUsage',
            'AttributeDefinition', 'AttributeUsage',
            'ItemDefinition', 'ItemUsage',
            'PortDefinition', 'PortUsage',
            'InterfaceDefinition', 'InterfaceUsage',
            'ConnectionDefinition', 'ConnectionUsage',
            'ActionDefinition', 'ActionUsage',
            'StateDefinition', 'StateUsage',
            'RequirementDefinition', 'RequirementUsage',
            'ConstraintDefinition', 'ConstraintUsage',
            'ViewDefinition', 'ViewUsage',
            'ViewpointDefinition', 'ViewpointUsage',
            'RenderingDefinition', 'RenderingUsage',
            'Comment', 'Documentation'
        ]
        return type in exportable
    }

    /**
     * Generate SysML v2 text for a single element
     */
    String generateElementText(Map element, String projectId, String commitId) {
        StringBuilder sb = new StringBuilder()

        String type = element['@type']
        String name = getShortName(element)
        String keyword = typeToKeyword(type)

        // Skip non-exportable types
        if (!isExportableType(type)) {
            return ""
        }

        // Handle comments specially
        if (type == 'Comment') {
            String body = element['body'] ?: ''
            if (body) {
                sb.append(indent())
                sb.append("/* ${body} */\n")
            }
            return sb.toString()
        }

        // Build the declaration line
        sb.append(indent())
        sb.append(keyword)

        if (name) {
            sb.append(" ${escapeName(name)}")
        }

        // Check for specializations (inheritance)
        List ownedSpecialization = element['ownedSpecialization'] ?: []
        if (ownedSpecialization) {
            // Would need to resolve the specialization target
            // For now, just note there are specializations
        }

        // Get owned features/members
        List ownedMember = element['ownedMember'] ?: []
        List ownedFeature = element['ownedFeature'] ?: []
        List ownedElement = element['ownedElement'] ?: []

        // Determine if we need a body
        boolean hasContent = !ownedMember.isEmpty() || !ownedFeature.isEmpty()

        if (hasContent) {
            sb.append(" {\n")
            indentLevel++

            // Process owned members (for Packages/Namespaces)
            ownedMember.each { memberRef ->
                String memberId = memberRef['@id']
                if (memberId) {
                    try {
                        Map member = getElement(projectId, commitId, memberId)
                        String memberText = generateElementText(member, projectId, commitId)
                        if (memberText) {
                            sb.append(memberText)
                        }
                    } catch (Exception e) {
                        // Skip elements we can't fetch
                    }
                }
            }

            // Process owned features (for Definitions/Usages)
            ownedFeature.each { featureRef ->
                String featureId = featureRef['@id']
                if (featureId && !ownedMember.any { it['@id'] == featureId }) {
                    try {
                        Map feature = getElement(projectId, commitId, featureId)
                        String featureText = generateElementText(feature, projectId, commitId)
                        if (featureText) {
                            sb.append(featureText)
                        }
                    } catch (Exception e) {
                        // Skip elements we can't fetch
                    }
                }
            }

            indentLevel--
            sb.append(indent())
            sb.append("}\n")
        } else {
            sb.append(";\n")
        }

        sb.append("\n")
        return sb.toString()
    }

    /**
     * Export a project or element to SysML v2 text
     */
    String exportToText(String projectId, String elementId = null) {
        StringBuilder sb = new StringBuilder()

        // Get project info
        Map project = apiGet("/projects/${projectId}")
        String projectName = project['name'] ?: projectId

        sb.append("// SysML v2 Export\n")
        sb.append("// Project: ${projectName}\n")
        sb.append("// Exported: ${new Date()}\n")
        sb.append("// Generated by ExportSysMLText.groovy\n")
        sb.append("\n")

        // Get latest commit
        List commits = apiGetList("/projects/${projectId}/commits")
        if (commits.isEmpty()) {
            throw new RuntimeException("No commits found in project")
        }
        String commitId = commits[-1]['@id']  // Latest commit
        sb.append("// Commit: ${commits[-1]['name']} (${commitId})\n\n")

        println "Fetching elements from commit ${commits[-1]['name']}..."

        if (elementId) {
            // Export specific element
            Map element = getElement(projectId, commitId, elementId)
            sb.append(generateElementText(element, projectId, commitId))
        } else {
            // Export root elements
            List roots = apiGetList("/projects/${projectId}/commits/${commitId}/roots")
            println "Found ${roots.size()} root element(s)"

            roots.each { root ->
                // Process each root's owned members
                List ownedMember = root['ownedMember'] ?: []
                ownedMember.each { memberRef ->
                    String memberId = memberRef['@id']
                    if (memberId) {
                        try {
                            Map member = getElement(projectId, commitId, memberId)
                            sb.append(generateElementText(member, projectId, commitId))
                        } catch (Exception e) {
                            println "Warning: Could not process element ${memberId}: ${e.message}"
                        }
                    }
                }
            }
        }

        return sb.toString()
    }

    /**
     * Save export to file
     */
    void saveExport(String content, String projectName) {
        new File(OUTPUT_DIR).mkdirs()

        String timestamp = new Date().format("yyyyMMdd_HHmmss")
        String safeName = projectName.replaceAll("[^a-zA-Z0-9]", "_")
        String filename = "${OUTPUT_DIR}/sysml_export_${safeName}_${timestamp}.sysml"

        new File(filename).text = content
        println "\nExport saved to: ${filename}"
    }

    void close() {
        httpClient.close()
    }
}

// Main execution
if (args.length < 2) {
    println "Usage: groovy ExportSysMLText.groovy <username> <password> [projectId] [elementId]"
    println ""
    println "Examples:"
    println "  groovy ExportSysMLText.groovy user pass                    # List projects"
    println "  groovy ExportSysMLText.groovy user pass <projectId>        # Export project"
    println "  groovy ExportSysMLText.groovy user pass <projectId> <elemId> # Export element"
    System.exit(1)
}

def username = args[0]
def password = args[1]
def projectId = args.length > 2 ? args[2] : null
def elementId = args.length > 3 ? args[3] : null

def exporter = new SysMLv2TextExporter(username, password)

try {
    if (!projectId) {
        // List projects
        println "Fetching available projects...\n"
        def projects = exporter.apiGetList("/projects")

        println "Available Projects:"
        println "-" * 80
        projects.each { p ->
            println "ID:   ${p['@id']}"
            println "Name: ${p['name']}"
            println "-" * 80
        }
        println "\nTo export a project, run:"
        println "  groovy ExportSysMLText.groovy ${username} <password> <projectId>"
    } else {
        // Export project or element
        println "Exporting SysML v2 text..."

        // Get project name for filename
        def project = exporter.apiGet("/projects/${projectId}")
        String projectName = project['name'] ?: 'unknown'

        String content = exporter.exportToText(projectId, elementId)

        // Print preview
        println "\n" + "=" * 80
        println "PREVIEW (first 2000 chars):"
        println "=" * 80
        println content.take(2000)
        if (content.length() > 2000) {
            println "\n... [truncated, ${content.length()} total chars]"
        }
        println "=" * 80

        // Save to file
        exporter.saveExport(content, projectName)
    }
} catch (Exception e) {
    println "Error: ${e.message}"
    e.printStackTrace()

    // Save diagnostic
    new File("${SysMLv2TextExporter.DIAGNOSTIC_DIR}").mkdirs()
    new File("${SysMLv2TextExporter.DIAGNOSTIC_DIR}/export_error_${new Date().format('yyyyMMdd_HHmmss')}.log").text =
        "Error: ${e.message}\n\nStack trace:\n${e.stackTrace.join('\n')}"

    System.exit(1)
} finally {
    exporter.close()
}
