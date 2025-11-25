#!/usr/bin/env groovy
/**
 * Example 01: List Projects
 *
 * Purpose: Retrieve and display all accessible projects from the SysML v2 API
 *
 * Learning Objectives:
 * - Connect to the SysML v2 API with authentication
 * - Make a simple GET request
 * - Parse JSON responses
 * - Display results in a formatted table
 *
 * API Endpoints Used:
 * - GET /projects
 *
 * Expected Output:
 * A formatted table showing project IDs, names, and descriptions
 */

// Load utilities
evaluate(new File("CookbookUtils.groovy"))

def utils = new CookbookUtils()

println """
╔════════════════════════════════════════════════════════════════╗
║                   Example 01: List Projects                    ║
╚════════════════════════════════════════════════════════════════╝
"""

try {
    // Step 1: Load credentials
    println "Step 1: Loading credentials..."
    def creds = utils.loadCredentials()
    println "✓ Credentials loaded for user: ${creds.username}"

    // Step 2: Fetch projects
    println "\nStep 2: Fetching projects from API..."
    def projects = utils.timed("List projects") {
        utils.listProjects(creds.username, creds.password)
    }
    println "✓ Retrieved ${projects.size()} projects"

    // Step 3: Display results
    println "\nStep 3: Displaying results..."
    utils.printSeparator("All Projects")

    // Prepare table data
    def tableData = projects.collect { project ->
        [
            'ID': project['@id']?.take(12) + '...',
            'Name': project.name ?: '(unnamed)',
            'Description': (project.description ?: '(no description)').take(40)
        ]
    }

    utils.printTable(tableData, ['ID', 'Name', 'Description'])

    // Step 4: Additional statistics
    println "\n"
    utils.printSeparator("Statistics")
    println "Total Projects: ${projects.size()}"
    println "Named Projects: ${projects.count { it.name }}"
    println "Projects with Descriptions: ${projects.count { it.description }}"

    // Step 5: Save to file (optional)
    println "\n"
    def output = new StringBuilder()
    output.append("SysML v2 Projects\n")
    output.append("=" * 80 + "\n\n")
    projects.each { project ->
        output.append("ID: ${project['@id']}\n")
        output.append("Name: ${project.name ?: '(unnamed)'}\n")
        output.append("Description: ${project.description ?: '(no description)'}\n")
        output.append("-" * 80 + "\n")
    }
    utils.writeToFile("output/01_projects_list.txt", output.toString())

    println "\n✓ Example completed successfully!"

} catch (Exception e) {
    println "\n✗ Error occurred: ${e.message}"
    e.printStackTrace()
    System.exit(1)
}
