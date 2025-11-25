# SysML v2 API Cookbook

A comprehensive collection of practical examples demonstrating how to use the Dassault SysMLv2 API effectively.

## 📚 Table of Contents

### Basic Operations
1. [**01_ListProjects**](examples/01_ListProjects.groovy) - List all accessible projects
2. [**02_GetProjectDetails**](examples/02_GetProjectDetails.groovy) - Get detailed information about a project
3. [**03_ListCommits**](examples/03_ListCommits.groovy) - List all commits in a project
4. [**04_GetRootElements**](examples/04_GetRootElements.groovy) - Get root elements from a commit

### Element Operations
5. [**05_GetElementDetails**](examples/05_GetElementDetails.groovy) - Get detailed element information
6. [**06_TraverseElementTree**](examples/06_TraverseElementTree.groovy) - Recursively traverse element hierarchy
7. [**07_FindElementsByType**](examples/07_FindElementsByType.groovy) - Filter elements by type
8. [**08_SearchElementsByName**](examples/08_SearchElementsByName.groovy) - Search for elements by name

### Relationship Analysis
9. [**09_AnalyzeRelationships**](examples/09_AnalyzeRelationships.groovy) - Analyze element relationships
10. [**10_BuildDependencyGraph**](examples/10_BuildDependencyGraph.groovy) - Build dependency graphs
11. [**11_TraceRequirements**](examples/11_TraceRequirements.groovy) - Trace requirements to implementations

### Model Analysis
12. [**12_GenerateStatistics**](examples/12_GenerateStatistics.groovy) - Generate model statistics
13. [**13_CompareCommits**](examples/13_CompareCommits.groovy) - Compare two commits
14. [**14_ValidateModel**](examples/14_ValidateModel.groovy) - Validate model consistency

### Export & Reporting
15. [**15_ExportToCSV**](examples/15_ExportToCSV.groovy) - Export elements to CSV
16. [**16_GenerateReport**](examples/16_GenerateReport.groovy) - Generate HTML report
17. [**17_ExportDiagram**](examples/17_ExportDiagram.groovy) - Export diagram data

### Advanced Patterns
18. [**18_CachingStrategy**](examples/18_CachingStrategy.groovy) - Implement efficient caching
19. [**19_BatchProcessing**](examples/19_BatchProcessing.groovy) - Process multiple projects
20. [**20_AsyncOperations**](examples/20_AsyncOperations.groovy) - Asynchronous API calls

## 🚀 Quick Start

### Prerequisites

1. Groovy 3.0+ installed
2. Credentials configured in `credentials.properties`:
```properties
username=your_username
password=your_password
```

### Running Examples

Each example can be run independently:

```bash
cd cookbook/examples
groovy 01_ListProjects.groovy
```

### Common API Patterns

All examples follow these patterns:

```groovy
// 1. Load credentials
def creds = new Properties()
new File("../../credentials.properties").withInputStream {
    creds.load(it)
}

// 2. Setup HTTP client
def client = createSSLClient()

// 3. Make API call
def response = apiGet("/projects", creds.username, creds.password)

// 4. Process response
def projects = new JsonSlurper().parseText(response)
```

## 📖 Documentation

Detailed documentation for each use case:

- [**Basic API Usage**](docs/01_BasicAPIUsage.md)
- [**Authentication & Security**](docs/02_Authentication.md)
- [**Element Hierarchy**](docs/03_ElementHierarchy.md)
- [**Relationships**](docs/04_Relationships.md)
- [**Commit Management**](docs/05_CommitManagement.md)
- [**Performance Optimization**](docs/06_Performance.md)
- [**Error Handling**](docs/07_ErrorHandling.md)
- [**Best Practices**](docs/08_BestPractices.md)

## 🎯 Use Cases

### For Model Engineers
- Navigate project structure
- Analyze element relationships
- Validate model consistency
- Generate documentation

### For Systems Architects
- Compare model versions
- Trace requirements
- Analyze dependencies
- Generate reports

### For Tool Developers
- Build custom viewers
- Implement model transformations
- Create automation scripts
- Integrate with other tools

## 🔧 API Reference

Base URL: `https://sysml2.intercax.com/api`

### Key Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/projects` | GET | List all projects |
| `/projects/{id}` | GET | Get project details |
| `/projects/{id}/commits` | GET | List commits |
| `/projects/{id}/commits/{commitId}/roots` | GET | Get root elements |
| `/projects/{id}/commits/{commitId}/elements/{elementId}` | GET | Get element details |

## 📝 Examples Index

### By Difficulty

**Beginner:**
- 01_ListProjects
- 02_GetProjectDetails
- 03_ListCommits
- 04_GetRootElements
- 05_GetElementDetails

**Intermediate:**
- 06_TraverseElementTree
- 07_FindElementsByType
- 08_SearchElementsByName
- 09_AnalyzeRelationships
- 12_GenerateStatistics
- 15_ExportToCSV

**Advanced:**
- 10_BuildDependencyGraph
- 11_TraceRequirements
- 13_CompareCommits
- 14_ValidateModel
- 16_GenerateReport
- 18_CachingStrategy
- 19_BatchProcessing
- 20_AsyncOperations

### By Topic

**Project Management:**
- 01_ListProjects
- 02_GetProjectDetails
- 03_ListCommits
- 19_BatchProcessing

**Element Navigation:**
- 04_GetRootElements
- 05_GetElementDetails
- 06_TraverseElementTree
- 07_FindElementsByType
- 08_SearchElementsByName

**Analysis:**
- 09_AnalyzeRelationships
- 10_BuildDependencyGraph
- 11_TraceRequirements
- 12_GenerateStatistics
- 13_CompareCommits
- 14_ValidateModel

**Export & Integration:**
- 15_ExportToCSV
- 16_GenerateReport
- 17_ExportDiagram

## 🤝 Contributing

Add new examples following this template:

```groovy
#!/usr/bin/env groovy
/**
 * Example: <Name>
 *
 * Purpose: <Brief description>
 *
 * Learning Objectives:
 * - <Objective 1>
 * - <Objective 2>
 *
 * API Endpoints Used:
 * - GET /endpoint1
 * - GET /endpoint2
 */

// Your code here
```

## 📄 License

MIT License - See LICENSE file for details

## 🔗 Resources

- [SysML v2 Specification](https://www.omg.org/spec/SysML/)
- [API Documentation](https://sysml2.intercax.com/docs)
- [Main Project Repository](https://github.com/turbogeek/SysMLv2API-experiments)
