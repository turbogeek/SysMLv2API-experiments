#!/usr/bin/env groovy
/**
 * TestPegAndHole.groovy
 *
 * Dedicated test script to load and analyze the PegAndHole project
 * with detailed diagnostic output.
 */

@Grab('org.apache.httpcomponents:httpclient:4.5.14')
@Grab('org.apache.httpcomponents:httpcore:4.4.16')

import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import org.apache.http.auth.*
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.conn.ssl.*
import org.apache.http.ssl.*
import javax.net.ssl.*
import java.security.cert.X509Certificate
import groovy.json.JsonSlurper
import groovy.transform.Field

// Load credentials
def credsFile = new File("credentials.properties")
def creds = new Properties()
credsFile.withInputStream { creds.load(it) }

String username = creds.getProperty('SYSMLV2_USERNAME')
String password = creds.getProperty('SYSMLV2_PASSWORD')
String baseUrl = creds.getProperty('SYSMLV2_BASE_URL', 'https://sysml2.intercax.com/api')

println """
╔════════════════════════════════════════════════════════════════╗
║          Testing PegAndHole Project Data Loading              ║
╚════════════════════════════════════════════════════════════════╝
"""

// Setup SSL client
SSLContext sslContext = SSLContexts.custom()
    .loadTrustMaterial(null, new TrustStrategy() {
        boolean isTrusted(X509Certificate[] chain, String authType) {
            return true
        }
    })
    .build()

SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
    sslContext,
    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
)

CloseableHttpClient client = HttpClients.custom()
    .setSSLSocketFactory(sslsf)
    .build()

// Closure to access API - captures username/password/client/baseUrl from binding
def apiGet = { String endpoint ->
    String url = baseUrl + endpoint
    HttpGet request = new HttpGet(url)

    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password)
    HttpClientContext context = HttpClientContext.create()
    request.addHeader(new BasicScheme().authenticate(credentials, request, context))
    request.addHeader("Accept", "application/json")

    CloseableHttpResponse response = client.execute(request)
    try {
        int statusCode = response.statusLine.statusCode
        String body = response.entity.content.text

        println "[API] ${endpoint}"
        println "      Status: ${statusCode}"
        println "      Size: ${body.length()} chars"

        if (statusCode != 200) {
            println "      ERROR: ${body.take(200)}"
            return null
        }

        return new JsonSlurper().parseText(body)
    } finally {
        response.close()
    }
}

try {
    // Step 1: Find PegAndHole project
    println "\nStep 1: Searching for PegAndHole project..."
    def projects = apiGet("/projects")

    def pegAndHole = projects.find { it.name?.contains("Peg") || it.name?.contains("Hole") }

    if (!pegAndHole) {
        println "   Trying alternative search..."
        projects.eachWithIndex { project, idx ->
            println "   ${idx + 1}. ${project.name ?: '(unnamed)'} - ${project['@id']?.take(12)}..."
        }
        println "\n   Please select a project number to test:"
        return
    }

    String projectId = pegAndHole['@id']
    String projectName = pegAndHole.name

    println "   ✓ Found: ${projectName}"
    println "     ID: ${projectId}"

    // Step 2: Get commits
    println "\nStep 2: Loading commits..."
    def commits = apiGet("/projects/${projectId}/commits")

    if (!commits) {
        println "   ✗ Failed to load commits"
        return
    }

    println "   ✓ Found ${commits.size()} commits"

    if (commits.size() == 0) {
        println "   ✗ No commits available"
        return
    }

    String latestCommitId = commits[0]['@id']
    String commitTimestamp = commits[0]['timestamp'] ?: 'No timestamp'

    println "   Latest commit:"
    println "     ID: ${latestCommitId.take(12)}..."
    println "     Timestamp: ${commitTimestamp}"

    // Step 3: Get root elements
    println "\nStep 3: Loading root elements..."
    def roots = apiGet("/projects/${projectId}/commits/${latestCommitId}/roots")

    if (!roots) {
        println "   ✗ Failed to load roots"
        return
    }

    println "   ✓ Found ${roots.size()} root elements"

    // Step 4: Load first few elements
    println "\nStep 4: Loading element details..."

    int successCount = 0
    int failCount = 0
    Map elementCache = [:]

    roots.take(5).each { root ->
        String elementId = root['@id']
        String elementName = root.name ?: elementId.take(12)

        println "\n   Loading: ${elementName}..."

        def element = apiGet("/projects/${projectId}/commits/${latestCommitId}/elements/${elementId}")

        if (element) {
            successCount++
            elementCache[elementId] = element

            println "     ✓ Loaded successfully"
            println "       Type: ${element['@type']}"
            println "       Properties: ${element.keySet().size()}"

            // Check for children
            def ownedMembers = element.ownedMember ?: []
            if (ownedMembers instanceof Map) ownedMembers = [ownedMembers]

            println "       Children: ${ownedMembers.size()}"

            // Try loading first child
            if (ownedMembers.size() > 0) {
                String childId = ownedMembers[0]['@id']
                def child = apiGet("/projects/${projectId}/commits/${latestCommitId}/elements/${childId}")

                if (child) {
                    elementCache[childId] = child
                    println "       ✓ Child loaded: ${child.name ?: childId.take(12)}"
                } else {
                    println "       ✗ Child load failed"
                }
            }
        } else {
            failCount++
            println "     ✗ Failed to load"
        }
    }

    // Step 5: Summary
    println "\n"
    println "═" * 70
    println "SUMMARY"
    println "═" * 70
    println "Project: ${projectName}"
    println "Commits: ${commits.size()}"
    println "Root Elements: ${roots.size()}"
    println "Elements Loaded: ${elementCache.size()}"
    println "Successful Loads: ${successCount}"
    println "Failed Loads: ${failCount}"

    // Step 6: Generate statistics
    if (elementCache.size() > 0) {
        println "\nElement Type Distribution:"
        Map typeCounts = [:]
        elementCache.each { id, element ->
            String type = element['@type']?.tokenize('.')[-1] ?: 'Unknown'
            typeCounts[type] = (typeCounts[type] ?: 0) + 1
        }

        typeCounts.sort { -it.value }.each { type, count ->
            println "  ${type.padRight(30)} ${count}"
        }
    }

    // Write diagnostic report
    def report = new File("../diagnostics/pegandhole_test_report.txt")
    report.text = """PegAndHole Project Test Report
Generated: ${new Date()}

Project Details:
  Name: ${projectName}
  ID: ${projectId}
  Commits: ${commits.size()}
  Root Elements: ${roots.size()}

Test Results:
  Elements Successfully Loaded: ${successCount}
  Elements Failed to Load: ${failCount}
  Total in Cache: ${elementCache.size()}

Element Types:
${typeCounts.collect { type, count -> "  ${type}: ${count}" }.join('\n')}

Root Elements:
${roots.take(10).collect { root -> "  - ${root.name ?: root['@id']?.take(12)} (${root['@id']})" }.join('\n')}
"""

    println "\n✓ Diagnostic report saved to: ../diagnostics/pegandhole_test_report.txt"

} catch (Exception e) {
    println "\n✗ Exception occurred: ${e.message}"
    e.printStackTrace()
} finally {
    client.close()
}
