# Basic API Usage

This guide covers the fundamental patterns for working with the SysML v2 API.

## Table of Contents
- [Authentication](#authentication)
- [Making API Calls](#making-api-calls)
- [Parsing Responses](#parsing-responses)
- [Common Patterns](#common-patterns)
- [Error Handling](#error-handling)

## Authentication

The SysML v2 API uses HTTP Basic Authentication.

### Setting Up Credentials

1. Create a `credentials.properties` file:
```properties
username=your_username
password=your_password
```

2. Load credentials in your script:
```groovy
def creds = new Properties()
new File("credentials.properties").withInputStream {
    creds.load(it)
}
```

### Security Best Practices

- **Never commit credentials** to version control
- Add `credentials.properties` to `.gitignore`
- Use environment variables for CI/CD
- Rotate passwords regularly

## Making API Calls

### Base URL

```groovy
static final String BASE_URL = "https://sysml2.intercax.com/api"
```

### SSL Configuration

The API uses HTTPS with self-signed certificates. Configure SSL trust:

```groovy
SSLContext sslContext = SSLContexts.custom()
    .loadTrustMaterial(null, new TrustStrategy() {
        boolean isTrusted(X509Certificate[] chain, String authType) {
            return true
        }
    })
    .build()
```

### HTTP Client Setup

```groovy
CloseableHttpClient client = HttpClients.custom()
    .setSSLSocketFactory(sslsf)
    .build()
```

### Making GET Requests

```groovy
HttpGet request = new HttpGet(BASE_URL + "/projects")

// Add authentication header
UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password)
request.addHeader(new BasicScheme().authenticate(creds, request, context))

// Add accept header
request.addHeader("Accept", "application/json")

// Execute
CloseableHttpResponse response = client.execute(request)
String body = response.entity.content.text
```

## Parsing Responses

### JSON Parsing

Use Groovy's built-in `JsonSlurper`:

```groovy
import groovy.json.JsonSlurper

String response = // ... API call
def data = new JsonSlurper().parseText(response)
```

### Accessing Properties

```groovy
// Access by key
String id = project['@id']
String name = project.name

// Safe access with default
String desc = project.description ?: '(no description)'

// Check if property exists
if (project.containsKey('timestamp')) {
    // ...
}
```

### Handling Lists

```groovy
// Projects list
def projects = new JsonSlurper().parseText(response)
projects.each { project ->
    println project.name
}

// Filter
def namedProjects = projects.findAll { it.name }

// Find first match
def myProject = projects.find { it.name == "My Project" }
```

## Common Patterns

### Pattern 1: List All Resources

```groovy
// 1. Load credentials
def creds = loadCredentials()

// 2. Make API call
def response = apiGet("/projects", creds.username, creds.password)

// 3. Parse JSON
def projects = new JsonSlurper().parseText(response)

// 4. Process results
projects.each { project ->
    println "${project.name}: ${project['@id']}"
}
```

### Pattern 2: Get Resource by ID

```groovy
// 1. Get ID (from list or user input)
String projectId = "abc123..."

// 2. Fetch details
def response = apiGet("/projects/${projectId}", username, password)
def project = new JsonSlurper().parseText(response)

// 3. Use data
println "Project: ${project.name}"
```

### Pattern 3: Navigate Hierarchy

```groovy
// 1. Get parent resource
def project = getProject(projectId)

// 2. Get child resources
def commits = listCommits(projectId)

// 3. Get grandchild resources
String commitId = commits[0]['@id']
def roots = getRootElements(projectId, commitId)
```

### Pattern 4: Recursive Traversal

```groovy
void traverse(String elementId, int depth = 0) {
    // 1. Get element
    def element = getElement(projectId, commitId, elementId)

    // 2. Process element
    println "  " * depth + element.name

    // 3. Recurse on children
    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    children.each { child ->
        traverse(child['@id'], depth + 1)
    }
}
```

## Error Handling

### Check Status Codes

```groovy
int statusCode = response.statusLine.statusCode

if (statusCode != 200) {
    String body = response.entity.content.text
    throw new RuntimeException("API error ${statusCode}: ${body}")
}
```

### Common HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 401 | Unauthorized | Check credentials |
| 403 | Forbidden | Check permissions |
| 404 | Not Found | Verify resource ID |
| 500 | Server Error | Check request, retry |

### Try-Catch Pattern

```groovy
try {
    def projects = listProjects(username, password)
    // ... process ...
} catch (RuntimeException e) {
    if (e.message.contains("401")) {
        println "Authentication failed - check credentials"
    } else if (e.message.contains("500")) {
        println "Server error - may need to retry"
    } else {
        println "Unexpected error: ${e.message}"
    }
}
```

### Retry Logic

```groovy
int maxRetries = 3
int attempt = 0

while (attempt < maxRetries) {
    try {
        def result = apiGet(endpoint, username, password)
        return result  // Success
    } catch (Exception e) {
        attempt++
        if (attempt >= maxRetries) {
            throw e  // Give up
        }
        Thread.sleep(1000 * attempt)  // Exponential backoff
    }
}
```

## Best Practices

### 1. Use Helper Functions

Create reusable functions for common operations:

```groovy
class APIHelper {
    static def listProjects(username, password) {
        return apiGetJson("/projects", username, password)
    }

    static def getProject(id, username, password) {
        return apiGetJson("/projects/${id}", username, password)
    }
}
```

### 2. Cache Responses

Avoid redundant API calls:

```groovy
Map cache = [:]

def getElementCached(elementId) {
    if (!cache.containsKey(elementId)) {
        cache[elementId] = getElement(elementId)
    }
    return cache[elementId]
}
```

### 3. Batch Operations

When possible, fetch multiple items in one request:

```groovy
// Bad: Multiple calls
roots.each { root ->
    def element = getElement(root['@id'])
}

// Better: Batch fetch
def elements = roots.collectEntries { root ->
    [(root['@id']): getElement(root['@id'])]
}
```

### 4. Measure Performance

Track API call timing:

```groovy
long start = System.currentTimeMillis()
def projects = listProjects(username, password)
long elapsed = System.currentTimeMillis() - start
println "Fetched ${projects.size()} projects in ${elapsed}ms"
```

## Next Steps

- [Authentication & Security](02_Authentication.md)
- [Element Hierarchy](03_ElementHierarchy.md)
- [Example: List Projects](../examples/01_ListProjects.groovy)
- [Example: Get Project Details](../examples/02_GetProjectDetails.groovy)
