#!/usr/bin/env groovy
/**
 * TestSmallProject.groovy
 *
 * Test script to find and load a small, simple project successfully
 * to verify the API client code works correctly.
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

// Load credentials
def credsFile = new File("credentials.properties")
def creds = new Properties()
credsFile.withInputStream { creds.load(it) }

String username = creds.getProperty('SYSMLV2_USERNAME')
String password = creds.getProperty('SYSMLV2_PASSWORD')
String baseUrl = creds.getProperty('SYSMLV2_BASE_URL', 'https://sysml2.intercax.com/api')

println """
╔════════════════════════════════════════════════════════════════╗
║          Testing Small Project Data Loading                   ║
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

// Closure to access API
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

List<Map> successfulProjects = []
List<Map> failedProjects = []

try {
    // Step 1: Get all projects and find small ones
    println "\nStep 1: Finding small, testable projects..."
    def projects = apiGet("/projects")

    if (!projects) {
        println "   ✗ Failed to load projects"
        return
    }

    println "   ✓ Found ${projects.size()} total projects"

    // Try to test up to 15 projects to find working ones
    int tested = 0
    int maxToTest = 15

    for (def project : projects) {
        if (tested >= maxToTest) break

        String projectId = project['@id']
        String projectName = project.name ?: projectId.take(12)

        println "\n" + ("=" * 70)
        println "Testing: ${projectName}"
        println "=" * 70

        tested++

        try {
            // Get commits
            def commits = apiGet("/projects/${projectId}/commits")

            if (!commits || commits.size() == 0) {
                println "   ⚠ No commits found"
                failedProjects << [name: projectName, id: projectId, reason: "No commits"]
                continue
            }

            println "   ✓ Found ${commits.size()} commits"

            String latestCommitId = commits[0]['@id']

            // Try to get roots
            def roots = apiGet("/projects/${projectId}/commits/${latestCommitId}/roots")

            if (!roots) {
                println "   ✗ Failed to load root elements"
                failedProjects << [name: projectName, id: projectId, reason: "Root loading failed"]
                continue
            }

            println "   ✓ Found ${roots.size()} root elements"

            if (roots.size() == 0) {
                println "   ⚠ Empty project (no roots)"
                failedProjects << [name: projectName, id: projectId, reason: "No root elements"]
                continue
            }

            // Try to load first root element
            String rootId = roots[0]['@id']
            String rootName = roots[0].name ?: rootId.take(12)

            def element = apiGet("/projects/${projectId}/commits/${latestCommitId}/elements/${rootId}")

            if (!element) {
                println "   ✗ Failed to load element: ${rootName}"
                failedProjects << [name: projectName, id: projectId, reason: "Element loading failed"]
                continue
            }

            println "   ✓ Successfully loaded element: ${rootName}"
            println "      Type: ${element['@type']}"

            // Success!
            successfulProjects << [
                name: projectName,
                id: projectId,
                commits: commits.size(),
                roots: roots.size(),
                sampleElement: rootName
            ]

            println "   ✅ PROJECT FULLY ACCESSIBLE"

        } catch (Exception e) {
            println "   ✗ Exception: ${e.message}"
            failedProjects << [name: projectName, id: projectId, reason: "Exception: ${e.message}"]
        }
    }

    // Summary
    println "\n"
    println "═" * 70
    println "SUMMARY"
    println "═" * 70
    println "Projects Tested: ${tested}"
    println "Successful: ${successfulProjects.size()}"
    println "Failed: ${failedProjects.size()}"

    if (successfulProjects.size() > 0) {
        println "\n✅ WORKING PROJECTS:"
        successfulProjects.each { p ->
            println "  • ${p.name}"
            println "    ID: ${p.id}"
            println "    Commits: ${p.commits}, Roots: ${p.roots}"
            println "    Sample element: ${p.sampleElement}"
        }
    }

    if (failedProjects.size() > 0) {
        println "\n❌ FAILED PROJECTS:"
        failedProjects.each { p ->
            println "  • ${p.name}"
            println "    ID: ${p.id}"
            println "    Reason: ${p.reason}"
        }
    }

    // Write diagnostic report
    def diagnosticDir = new File("../diagnostics")
    if (!diagnosticDir.exists()) diagnosticDir.mkdirs()

    def report = new File("../diagnostics/small_project_test_report.txt")
    report.text = """Small Project Test Report
Generated: ${new Date()}

Projects Tested: ${tested}
Successful: ${successfulProjects.size()}
Failed: ${failedProjects.size()}

Working Projects:
${successfulProjects.collect { p -> "  ${p.name} (${p.commits} commits, ${p.roots} roots)" }.join('\n')}

Failed Projects:
${failedProjects.collect { p -> "  ${p.name}: ${p.reason}" }.join('\n')}

Recommendation:
${successfulProjects.size() > 0 ?
  "Use one of the working projects listed above for testing and development." :
  "No working projects found. This may indicate API access issues or server problems."}
"""

    println "\n✓ Diagnostic report saved to: ../diagnostics/small_project_test_report.txt"

} catch (Exception e) {
    println "\n✗ Exception occurred: ${e.message}"
    e.printStackTrace()
} finally {
    client.close()
}
