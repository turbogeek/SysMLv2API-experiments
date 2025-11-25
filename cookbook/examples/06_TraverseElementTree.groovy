#!/usr/bin/env groovy
/**
 * Example 06: Traverse Element Tree
 *
 * Purpose: Recursively traverse and display the element hierarchy
 *
 * Learning Objectives:
 * - Implement recursive tree traversal
 * - Handle parent-child relationships
 * - Display hierarchical data
 * - Manage API calls efficiently
 *
 * API Endpoints Used:
 * - GET /projects/{id}/commits/{commitId}/roots
 * - GET /projects/{id}/commits/{commitId}/elements/{elementId}
 *
 * Expected Output:
 * Tree view of model elements showing hierarchy
 */

evaluate(new File("CookbookUtils.groovy"))

def utils = new CookbookUtils()

println """
╔════════════════════════════════════════════════════════════════╗
║              Example 06: Traverse Element Tree                 ║
╚════════════════════════════════════════════════════════════════╝
"""

try {
    def creds = utils.loadCredentials()
    def projects = utils.listProjects(creds.username, creds.password)

    // Find a project with elements
    def project = projects.find { it.name == "Standard Libraries1" } ?: projects[0]
    String projectId = project['@id']
    String projectName = project.name ?: projectId.take(12)

    println "Selected Project: ${projectName}"
    println "Project ID: ${projectId}"

    // Get latest commit
    String commitId = utils.getLatestCommit(projectId, creds.username, creds.password)
    println "Latest Commit: ${commitId.take(12)}..."

    // Get root elements
    println "\nFetching root elements..."
    def roots = utils.getRootElements(projectId, commitId, creds.username, creds.password)
    println "Found ${roots.size()} root elements"

    // Traverse tree
    utils.printSeparator("Element Tree")

    int elementCount = 0
    def maxDepth = 3  // Limit depth to avoid too many API calls

    roots.take(3).each { root ->  // Limit to first 3 roots
        String rootId = root['@id']

        utils.traverseElements(projectId, commitId, rootId,
            creds.username, creds.password,
            { element, depth ->
                if (depth > maxDepth) return

                String indent = "  " * depth
                String name = utils.getElementName(element)
                String type = utils.getElementType(element)

                println "${indent}├─ [${type}] ${name}"
                elementCount++
            })
    }

    println "\n"
    utils.printSeparator("Statistics")
    println "Total elements traversed: ${elementCount}"
    println "Maximum depth: ${maxDepth}"

    println "\n✓ Example completed successfully!"

} catch (Exception e) {
    println "\n✗ Error: ${e.message}"
    e.printStackTrace()
}
