#!/usr/bin/env groovy
/**
 * Example 02: Get Project Details
 *
 * Purpose: Retrieve detailed information about a specific project
 *
 * Learning Objectives:
 * - Make parametrized API requests
 * - Navigate JSON structure
 * - Extract specific properties
 * - Display formatted output
 *
 * API Endpoints Used:
 * - GET /projects
 * - GET /projects/{id}
 *
 * Expected Output:
 * Detailed information about a selected project
 */

evaluate(new File("CookbookUtils.groovy"))

def utils = new CookbookUtils()

println """
╔════════════════════════════════════════════════════════════════╗
║                Example 02: Get Project Details                 ║
╚════════════════════════════════════════════════════════════════╝
"""

try {
    // Load credentials
    def creds = utils.loadCredentials()

    // Get all projects
    def projects = utils.listProjects(creds.username, creds.password)
    println "Found ${projects.size()} projects"

    // Select first project or one by name
    def targetProject = projects.find { it.name == "Standard Libraries1" } ?: projects[0]
    String projectId = targetProject['@id']

    println "\nSelected Project:"
    println "  Name: ${targetProject.name}"
    println "  ID:   ${projectId}"

    // Get detailed project information
    println "\nFetching detailed information..."
    def projectDetails = utils.getProject(projectId, creds.username, creds.password)

    // Display project details
    utils.printSeparator("Project Details")

    println "Basic Information:"
    println "  @id:          ${projectDetails['@id']}"
    println "  @type:        ${projectDetails['@type']}"
    println "  name:         ${projectDetails.name ?: '(none)'}"
    println "  description:  ${projectDetails.description ?: '(none)'}"

    println "\nMetadata:"
    projectDetails.each { key, value ->
        if (key.startsWith('@') || key in ['name', 'description']) return
        println "  ${key.padRight(15)}: ${value.toString().take(60)}"
    }

    // Get commits for this project
    println "\n"
    utils.printSeparator("Commits")
    def commits = utils.listCommits(projectId, creds.username, creds.password)
    println "Total Commits: ${commits.size()}"

    if (commits.size() > 0) {
        println "\nMost Recent Commits:"
        commits.take(5).each { commit ->
            println "  ${commit['@id'].take(12)}... - ${commit.timestamp ?: 'No timestamp'}"
        }
    }

    // Save detailed report
    def report = new StringBuilder()
    report.append("Project Detailed Report\n")
    report.append("=" * 80 + "\n\n")
    report.append("Project ID: ${projectDetails['@id']}\n")
    report.append("Name: ${projectDetails.name ?: '(unnamed)'}\n")
    report.append("Description: ${projectDetails.description ?: '(no description)'}\n\n")
    report.append("Commits (${commits.size()}):\n")
    commits.each { commit ->
        report.append("  - ${commit['@id']} (${commit.timestamp})\n")
    }

    utils.writeToFile("output/02_project_details.txt", report.toString())

    println "\n✓ Example completed successfully!"

} catch (Exception e) {
    println "\n✗ Error: ${e.message}"
    e.printStackTrace()
}
