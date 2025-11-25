/**
 * CookbookUtils.groovy
 *
 * Shared utility functions for all cookbook examples.
 * This provides common patterns for API access, authentication, and data processing.
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
import groovy.json.JsonOutput

class CookbookUtils {
    static final String BASE_URL = "https://sysml2.intercax.com/api"
    static final String CREDS_FILE = "../../credentials.properties"

    /**
     * Load credentials from properties file
     */
    static Map loadCredentials(String path = CREDS_FILE) {
        def creds = new Properties()
        def credsFile = new File(path)

        if (!credsFile.exists()) {
            throw new FileNotFoundException("Credentials file not found: ${credsFile.absolutePath}")
        }

        credsFile.withInputStream { creds.load(it) }

        return [
            username: creds.getProperty('username'),
            password: creds.getProperty('password')
        ]
    }

    /**
     * Create SSL-enabled HTTP client that accepts self-signed certificates
     */
    static CloseableHttpClient createSSLClient() {
        SSLContext sslContext = SSLContexts.custom()
            .loadTrustMaterial(null, new TrustStrategy() {
                boolean isTrusted(X509Certificate[] chain, String authType) {
                    return true  // Trust all certificates
                }
            })
            .build()

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
            sslContext,
            SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        )

        return HttpClients.custom()
            .setSSLSocketFactory(sslsf)
            .build()
    }

    /**
     * Execute HTTP GET request with authentication
     */
    static String apiGet(String endpoint, String username, String password) {
        CloseableHttpClient client = createSSLClient()

        try {
            String url = endpoint.startsWith('http') ? endpoint : BASE_URL + endpoint
            HttpGet request = new HttpGet(url)

            // Setup authentication
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password)
            HttpClientContext context = HttpClientContext.create()

            request.addHeader(new BasicScheme().authenticate(creds, request, context))
            request.addHeader("Accept", "application/json")

            // Execute request
            CloseableHttpResponse response = client.execute(request)

            try {
                int statusCode = response.statusLine.statusCode
                String body = response.entity.content.text

                if (statusCode != 200) {
                    throw new RuntimeException("API returned status ${statusCode}: ${body}")
                }

                return body
            } finally {
                response.close()
            }
        } finally {
            client.close()
        }
    }

    /**
     * Execute HTTP GET and parse JSON response
     */
    static def apiGetJson(String endpoint, String username, String password) {
        String response = apiGet(endpoint, username, password)
        return new JsonSlurper().parseText(response)
    }

    /**
     * List all projects
     */
    static List listProjects(String username, String password) {
        def response = apiGetJson("/projects", username, password)
        return response
    }

    /**
     * Get project details
     */
    static Map getProject(String projectId, String username, String password) {
        def response = apiGetJson("/projects/${projectId}", username, password)
        return response
    }

    /**
     * List commits for a project
     */
    static List listCommits(String projectId, String username, String password) {
        def response = apiGetJson("/projects/${projectId}/commits", username, password)
        return response
    }

    /**
     * Get root elements from a commit
     */
    static List getRootElements(String projectId, String commitId, String username, String password) {
        def response = apiGetJson("/projects/${projectId}/commits/${commitId}/roots", username, password)
        return response
    }

    /**
     * Get element details
     */
    static Map getElement(String projectId, String commitId, String elementId, String username, String password) {
        def response = apiGetJson("/projects/${projectId}/commits/${commitId}/elements/${elementId}", username, password)
        return response
    }

    /**
     * Get latest commit ID for a project
     */
    static String getLatestCommit(String projectId, String username, String password) {
        def commits = listCommits(projectId, username, password)
        return commits[0]['@id']
    }

    /**
     * Print formatted JSON
     */
    static void printJson(def object) {
        println JsonOutput.prettyPrint(JsonOutput.toJson(object))
    }

    /**
     * Print a separator line
     */
    static void printSeparator(String title = "") {
        if (title) {
            println "\n${'=' * 80}"
            println "  ${title}"
            println "${'=' * 80}"
        } else {
            println "${'=' * 80}"
        }
    }

    /**
     * Print formatted table
     */
    static void printTable(List<Map> rows, List<String> columns) {
        if (rows.isEmpty()) {
            println "No data to display"
            return
        }

        // Calculate column widths
        Map<String, Integer> widths = [:]
        columns.each { col ->
            widths[col] = col.length()
            rows.each { row ->
                String value = row[col]?.toString() ?: ""
                widths[col] = Math.max(widths[col], value.length())
            }
        }

        // Print header
        String header = columns.collect { col ->
            col.padRight(widths[col])
        }.join(" | ")
        println header
        println columns.collect { col -> "-" * widths[col] }.join("-+-")

        // Print rows
        rows.each { row ->
            String line = columns.collect { col ->
                (row[col]?.toString() ?: "").padRight(widths[col])
            }.join(" | ")
            println line
        }
    }

    /**
     * Traverse element tree recursively
     */
    static void traverseElements(String projectId, String commitId, String elementId,
                                 String username, String password,
                                 Closure visitor, int depth = 0) {
        def element = getElement(projectId, commitId, elementId, username, password)

        // Call visitor function
        visitor(element, depth)

        // Traverse children
        def ownedMembers = element.ownedMember ?: []
        if (ownedMembers instanceof Map) {
            ownedMembers = [ownedMembers]
        }

        ownedMembers.each { member ->
            String childId = member['@id']
            if (childId) {
                traverseElements(projectId, commitId, childId, username, password, visitor, depth + 1)
            }
        }
    }

    /**
     * Extract element name
     */
    static String getElementName(Map element) {
        return element.name ?: element.declaredName ?: element['@id']?.take(8) ?: 'Unknown'
    }

    /**
     * Extract element type (short form)
     */
    static String getElementType(Map element) {
        String fullType = element['@type'] ?: 'Unknown'
        return fullType.tokenize('.')[-1]  // Get last part after dot
    }

    /**
     * Write output to file
     */
    static void writeToFile(String filename, String content) {
        new File(filename).text = content
        println "Output written to: ${filename}"
    }

    /**
     * Append to file
     */
    static void appendToFile(String filename, String content) {
        new File(filename).append(content)
    }

    /**
     * Measure execution time
     */
    static def timed(String description, Closure code) {
        long start = System.currentTimeMillis()
        def result = code()
        long elapsed = System.currentTimeMillis() - start
        println "[TIMING] ${description}: ${elapsed}ms"
        return result
    }

    /**
     * Safe get with default value
     */
    static def safeGet(Map map, String key, def defaultValue = null) {
        return map?.containsKey(key) ? map[key] : defaultValue
    }

    /**
     * Filter elements by type
     */
    static List filterByType(List elements, String type) {
        return elements.findAll { element ->
            getElementType(element) == type
        }
    }

    /**
     * Find element by name
     */
    static Map findByName(List elements, String name) {
        return elements.find { element ->
            getElementName(element) == name
        }
    }
}
