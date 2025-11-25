#!/usr/bin/env groovy
/**
 * Example 12: Generate Model Statistics
 *
 * Purpose: Analyze a model and generate comprehensive statistics
 *
 * Learning Objectives:
 * - Collect and aggregate element data
 * - Perform statistical analysis
 * - Generate formatted reports
 * - Visualize data with text-based charts
 *
 * API Endpoints Used:
 * - GET /projects/{id}/commits/{commitId}/roots
 * - GET /projects/{id}/commits/{commitId}/elements/{elementId}
 *
 * Expected Output:
 * Comprehensive statistical report of model composition
 */

evaluate(new File("CookbookUtils.groovy"))

def utils = new CookbookUtils()

println """
╔═══════════════════════════════════════════════════════════════╗
║            Example 12: Generate Model Statistics              ║
╚════════════════════════════════════════════════════════════════╝
"""

try {
    def creds = utils.loadCredentials()
    def projects = utils.listProjects(creds.username, creds.password)

    // Select project
    def project = projects.find { it.name == "Standard Libraries1" } ?: projects[0]
    String projectId = project['@id']
    String projectName = project.name ?: projectId.take(12)

    println "Analyzing Project: ${projectName}"

    // Get latest commit
    String commitId = utils.getLatestCommit(projectId, creds.username, creds.password)

    // Collect statistics
    println "\nCollecting model statistics..."

    Map<String, Integer> typeCounts = [:]
    Map<String, Integer> propertyUsage = [:]
    List<Map> allElements = []
    int totalElements = 0
    int maxDepth = 0

    // Get root elements
    def roots = utils.getRootElements(projectId, commitId, creds.username, creds.password)

    // Traverse and collect stats
    roots.take(5).each { root ->  // Limit for performance
        utils.traverseElements(projectId, commitId, root['@id'],
            creds.username, creds.password,
            { element, depth ->
                totalElements++
                maxDepth = Math.max(maxDepth, depth)
                allElements << element

                // Count element types
                String type = utils.getElementType(element)
                typeCounts[type] = (typeCounts[type] ?: 0) + 1

                // Track property usage
                element.keySet().each { key ->
                    if (!key.startsWith('@')) {
                        propertyUsage[key] = (propertyUsage[key] ?: 0) + 1
                    }
                }
            })
    }

    // Generate report
    utils.printSeparator("Model Statistics Report")

    println "\n📊 Overview:"
    println "  Total Elements:    ${totalElements}"
    println "  Root Elements:     ${roots.size()}"
    println "  Unique Types:      ${typeCounts.size()}"
    println "  Maximum Depth:     ${maxDepth}"
    println "  Properties Used:   ${propertyUsage.size()}"

    // Element type distribution
    println "\n📈 Element Type Distribution:"
    def sortedTypes = typeCounts.sort { -it.value }
    sortedTypes.take(10).eachWithIndex { entry, index ->
        String type = entry.key
        int count = entry.value
        double percentage = (count / totalElements) * 100
        String bar = "█" * (int)(percentage / 2)  // Scale to fit
        println "  ${(index + 1).toString().padLeft(2)}. ${type.padRight(25)} ${count.toString().padLeft(4)} (${String.format('%.1f', percentage)}%) ${bar}"
    }

    // Most common properties
    println "\n📋 Most Common Properties:"
    def sortedProps = propertyUsage.sort { -it.value }
    sortedProps.take(10).each { prop, count ->
        double percentage = (count / totalElements) * 100
        println "  ${prop.padRight(25)} ${count.toString().padLeft(4)} elements (${String.format('%.1f', percentage)}%)"
    }

    // Complexity metrics
    println "\n📐 Complexity Metrics:"
    double avgPropertiesPerElement = allElements.sum { it.size() } / allElements.size()
    println "  Avg Properties/Element: ${String.format('%.2f', avgPropertiesPerElement)}"
    println "  Depth/Element Ratio:    ${String.format('%.2f', maxDepth / totalElements)}"

    // Generate detailed report file
    def report = new StringBuilder()
    report.append("Model Statistics Report\n")
    report.append("=" * 80 + "\n\n")
    report.append("Project: ${projectName}\n")
    report.append("Project ID: ${projectId}\n")
    report.append("Commit ID: ${commitId}\n")
    report.append("Analysis Date: ${new Date()}\n\n")

    report.append("OVERVIEW\n")
    report.append("-" * 80 + "\n")
    report.append("Total Elements: ${totalElements}\n")
    report.append("Root Elements: ${roots.size()}\n")
    report.append("Unique Element Types: ${typeCounts.size()}\n")
    report.append("Maximum Depth: ${maxDepth}\n\n")

    report.append("ELEMENT TYPES (Complete List)\n")
    report.append("-" * 80 + "\n")
    sortedTypes.each { type, count ->
        report.append("${type.padRight(40)} ${count}\n")
    }

    utils.writeToFile("output/12_model_statistics.txt", report.toString())

    println "\n✓ Statistics generated successfully!"
    println "  Report saved to: output/12_model_statistics.txt"

} catch (Exception e) {
    println "\n✗ Error: ${e.message}"
    e.printStackTrace()
}
