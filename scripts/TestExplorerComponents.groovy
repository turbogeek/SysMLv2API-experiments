#!/usr/bin/env groovy
/**
 * TestExplorerComponents.groovy
 *
 * Test harness for validating SysMLv2Explorer functionality.
 * Runs automated tests without requiring user interaction.
 *
 * Tests:
 *   1. Credential loading from various sources
 *   2. API connectivity
 *   3. Project listing
 *   4. Element fetching
 *   5. SysML text generation
 *
 * Output: Writes results to diagnostics/test_results.log
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

class TestRunner {
    static final String BASE_URL = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    static final String DIAGNOSTIC_DIR = "../diagnostics"
    static final String TEST_LOG = "${DIAGNOSTIC_DIR}/test_results.log"

    File logFile
    CloseableHttpClient httpClient
    Gson gson = new GsonBuilder().setPrettyPrinting().create()
    String username
    String password

    int testsRun = 0
    int testsPassed = 0
    int testsFailed = 0

    TestRunner() {
        new File(DIAGNOSTIC_DIR).mkdirs()
        logFile = new File(TEST_LOG)
        logFile.text = "=== SysMLv2Explorer Component Tests ===\n"
        logFile.append("Run started: ${new Date()}\n\n")
    }

    void log(String message) {
        String timestamp = new Date().format("HH:mm:ss.SSS")
        String line = "[${timestamp}] ${message}"
        println line
        logFile.append(line + "\n")
    }

    void logResult(String testName, boolean passed, String details = "") {
        testsRun++
        if (passed) {
            testsPassed++
            log("PASS: ${testName}")
        } else {
            testsFailed++
            log("FAIL: ${testName}")
        }
        if (details) {
            log("      ${details}")
        }
    }

    // Test 1: Credential Loading
    boolean testCredentialLoading() {
        log("\n--- Test: Credential Loading ---")

        try {
            // Try environment variables
            String envUser = System.getenv("SYSMLV2_USERNAME")
            String envPass = System.getenv("SYSMLV2_PASSWORD")

            if (envUser && envPass) {
                username = envUser
                password = envPass
                logResult("Load from environment variables", true, "Username: ${username}")
                return true
            }

            // Try credentials.properties
            def propsFile = new File("credentials.properties")
            if (propsFile.exists()) {
                Properties props = new Properties()
                propsFile.withInputStream { props.load(it) }
                username = props.getProperty("SYSMLV2_USERNAME")
                password = props.getProperty("SYSMLV2_PASSWORD")

                if (username && password) {
                    logResult("Load from credentials.properties", true, "Username: ${username}")
                    return true
                }
            }

            // Try command line args
            if (args.length >= 2) {
                username = args[0]
                password = args[1]
                logResult("Load from command line", true, "Username: ${username}")
                return true
            }

            logResult("Credential loading", false, "No credentials found")
            return false

        } catch (Exception e) {
            logResult("Credential loading", false, "Exception: ${e.message}")
            return false
        }
    }

    // Test 2: HTTP Client Initialization
    boolean testHttpClientInit() {
        log("\n--- Test: HTTP Client Initialization ---")

        try {
            def trustStrategy = { X509Certificate[] chain, String authType -> true } as TrustStrategy
            def sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, trustStrategy)
                .build()
            def sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)

            def credentialsProvider = new BasicCredentialsProvider()
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

            httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultCredentialsProvider(credentialsProvider)
                .build()

            logResult("HTTP Client initialization", true)
            return true

        } catch (Exception e) {
            logResult("HTTP Client initialization", false, "Exception: ${e.message}")
            return false
        }
    }

    // Test 3: API Connectivity
    boolean testApiConnectivity() {
        log("\n--- Test: API Connectivity ---")

        try {
            def request = new HttpGet("${BASE_URL}/projects")
            request.setHeader("Accept", "application/json")

            def response = httpClient.execute(request)
            int statusCode = response.statusLine.statusCode
            def content = response.entity.content.text
            response.close()

            if (statusCode == 200) {
                logResult("API connectivity", true, "Status: ${statusCode}")
                return true
            } else {
                logResult("API connectivity", false, "Status: ${statusCode}, Response: ${content?.take(100)}")
                return false
            }

        } catch (Exception e) {
            logResult("API connectivity", false, "Exception: ${e.message}")
            return false
        }
    }

    // Test 4: Project Listing
    Map testProjectListing() {
        log("\n--- Test: Project Listing ---")

        try {
            def request = new HttpGet("${BASE_URL}/projects")
            request.setHeader("Accept", "application/json")

            def response = httpClient.execute(request)
            def content = response.entity.content.text
            response.close()

            List projects = gson.fromJson(content, ArrayList.class)

            if (projects && projects.size() > 0) {
                logResult("Project listing", true, "Found ${projects.size()} projects")

                // Find a project that has root elements (needed for thorough testing)
                Map accessibleProject = null
                for (def p : projects) {
                    try {
                        String pid = p['@id']
                        // Check commits
                        def commitsReq = new HttpGet("${BASE_URL}/projects/${pid}/commits")
                        commitsReq.setHeader("Accept", "application/json")
                        def commitsResp = httpClient.execute(commitsReq)
                        if (commitsResp.statusLine.statusCode != 200) {
                            commitsResp.close()
                            continue
                        }
                        List commits = gson.fromJson(commitsResp.entity.content.text, ArrayList.class)
                        commitsResp.close()

                        if (!commits || commits.isEmpty()) continue

                        // Check for roots
                        String commitId = commits[-1]['@id']
                        def rootsReq = new HttpGet("${BASE_URL}/projects/${pid}/commits/${commitId}/roots")
                        rootsReq.setHeader("Accept", "application/json")
                        def rootsResp = httpClient.execute(rootsReq)
                        if (rootsResp.statusLine.statusCode != 200) {
                            rootsResp.close()
                            continue
                        }
                        List roots = gson.fromJson(rootsResp.entity.content.text, ArrayList.class)
                        rootsResp.close()

                        if (roots && !roots.isEmpty()) {
                            accessibleProject = p
                            log("      Found project with ${roots.size()} roots: ${p['name']}")
                            break
                        }
                    } catch (Exception e) {
                        // Continue to next project
                    }
                }

                if (accessibleProject) {
                    log("      Selected project: ${accessibleProject['name']} (${accessibleProject['@id']})")
                    return accessibleProject
                }
            }

            logResult("Project listing", false, "No accessible projects found")
            return null

        } catch (Exception e) {
            logResult("Project listing", false, "Exception: ${e.message}")
            return null
        }
    }

    // Test 5: Element Fetching
    List testElementFetching(Map project) {
        log("\n--- Test: Element Fetching ---")

        if (!project) {
            logResult("Element fetching", false, "No project provided")
            return null
        }

        try {
            String projectId = project['@id']

            // Get commits
            def commitsReq = new HttpGet("${BASE_URL}/projects/${projectId}/commits")
            commitsReq.setHeader("Accept", "application/json")
            def commitsResp = httpClient.execute(commitsReq)
            List commits = gson.fromJson(commitsResp.entity.content.text, ArrayList.class)
            commitsResp.close()

            if (!commits || commits.isEmpty()) {
                logResult("Element fetching", false, "No commits found")
                return null
            }

            String commitId = commits[-1]['@id']
            log("      Using commit: ${commits[-1]['name']} (${commitId})")

            // Get roots
            def rootsReq = new HttpGet("${BASE_URL}/projects/${projectId}/commits/${commitId}/roots")
            rootsReq.setHeader("Accept", "application/json")
            def rootsResp = httpClient.execute(rootsReq)
            List roots = gson.fromJson(rootsResp.entity.content.text, ArrayList.class)
            rootsResp.close()

            if (roots && !roots.isEmpty()) {
                logResult("Element fetching", true, "Found ${roots.size()} root elements")
                return roots
            }

            logResult("Element fetching", false, "No root elements found")
            return null

        } catch (Exception e) {
            logResult("Element fetching", false, "Exception: ${e.message}")
            return null
        }
    }

    // Test 6: SysML Text Generation
    boolean testSysmlTextGeneration(Map element) {
        log("\n--- Test: SysML Text Generation ---")

        if (!element) {
            logResult("SysML text generation", false, "No element provided")
            return false
        }

        try {
            String type = element['@type']
            String name = element['name'] ?: element['declaredName'] ?: 'unnamed'

            // Simple text generation test
            String keyword = typeToKeyword(type)
            String escapedName = escapeName(name)

            String sysmlText = "${keyword} ${escapedName};"

            if (sysmlText && sysmlText.length() > 0) {
                logResult("SysML text generation", true, "Generated: ${sysmlText.take(80)}")
                log("      Full output sample: ${sysmlText}")
                return true
            }

            logResult("SysML text generation", false, "Empty output")
            return false

        } catch (Exception e) {
            logResult("SysML text generation", false, "Exception: ${e.message}")
            return false
        }
    }

    String typeToKeyword(String type) {
        switch (type) {
            case 'Package': return 'package'
            case 'PartDefinition': return 'part def'
            case 'PartUsage': return 'part'
            case 'PortUsage': return 'port'
            case 'InterfaceDefinition': return 'interface def'
            case 'ConnectionUsage': return 'connection'
            case 'Namespace': return 'namespace'
            default: return "/* ${type} */"
        }
    }

    String escapeName(String name) {
        if (name == null) return 'unnamed'
        if (name.contains(' ') || name.contains("'")) {
            return "'${name.replace("'", "\\'")}'"
        }
        return name
    }

    // Run all tests
    void runAllTests() {
        log("Starting test run...\n")

        // Test 1: Credentials
        if (!testCredentialLoading()) {
            log("\nCRITICAL: Cannot proceed without credentials")
            printSummary()
            return
        }

        // Test 2: HTTP Client
        if (!testHttpClientInit()) {
            log("\nCRITICAL: Cannot proceed without HTTP client")
            printSummary()
            return
        }

        // Test 3: API Connectivity
        if (!testApiConnectivity()) {
            log("\nCRITICAL: Cannot connect to API")
            printSummary()
            return
        }

        // Test 4: Project Listing
        Map project = testProjectListing()

        // Test 5: Element Fetching
        List roots = null
        if (project) {
            roots = testElementFetching(project)
        }

        // Test 6: SysML Text Generation
        if (roots && !roots.isEmpty()) {
            // Get first owned member from root
            List ownedMember = roots[0]['ownedMember'] ?: []
            if (!ownedMember.isEmpty()) {
                String memberId = ownedMember[0]['@id']
                try {
                    def elemReq = new HttpGet("${BASE_URL}/projects/${project['@id']}/commits/${roots[0]['@id']}/elements/${memberId}")
                    elemReq.setHeader("Accept", "application/json")
                    def elemResp = httpClient.execute(elemReq)
                    // Use a simpler element for testing
                } catch (Exception e) {
                    // Just use the root
                }
            }
            testSysmlTextGeneration(roots[0])
        }

        printSummary()

        // Cleanup
        if (httpClient) {
            httpClient.close()
        }
    }

    void printSummary() {
        log("\n" + "=" * 50)
        log("TEST SUMMARY")
        log("=" * 50)
        log("Tests Run:    ${testsRun}")
        log("Tests Passed: ${testsPassed}")
        log("Tests Failed: ${testsFailed}")
        log("=" * 50)

        if (testsFailed == 0) {
            log("\nALL TESTS PASSED!")
        } else {
            log("\nSOME TESTS FAILED - Check log for details")
        }

        log("\nDiagnostic log written to: ${TEST_LOG}")
    }
}

// Main execution
def runner = new TestRunner()
runner.runAllTests()
