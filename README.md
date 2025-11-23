# SysMLv2 API Experiments

This repository contains experiments and examples for working with the SysMLv2 API implemented by Dassault Systèmes, based on the OMG Systems Modeling API and Services specification.

## Overview

The SysMLv2 API provides a RESTful interface for interacting with SysML v2 models. This project explores the API capabilities and provides example scripts for common operations.

## API References

### Dassault Implementation
- **Swagger UI**: https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/swagger-ui/index.html#/

### OMG Specification & Reference Implementation
- **OMG Systems Modeling API Spec**: https://www.omg.org/spec/SystemsModelingAPI/1.0/Beta1/About-SystemsModelingAPI
- **SysML v2 API Services (GitHub)**: https://github.com/Systems-Modeling/SysML-v2-API-Services
- **SysML v2 Pilot Implementation**: https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation
- **Live API Documentation**: http://sysml2.intercax.com:9000/docs/

### Related Tools
- **SysON (Open Source SysML v2)**: https://mbse-syson.org/

## Project Structure

```
V2APIExperiments/
├── README.md
├── scripts/           # API interaction scripts (Groovy)
│   ├── ListProjects.groovy           # List all available projects
│   ├── GetProjectRequirements.groovy # Get requirements from a project
│   └── SysMLv2ApiClient.groovy       # Interactive API client
├── examples/          # Example requests and responses
├── docs/              # Additional documentation
├── output/            # Script output (JSON files)
├── diagnostics/       # Diagnostic logs
└── tests/             # Test scripts and validation
```

## Getting Started

### Prerequisites
- Groovy 3.0+ installed
- Access to the SysMLv2 API endpoint (may require VPN)
- Valid username and password for the API

### Running the Scripts

**List all projects:**
```bash
cd scripts
groovy ListProjects.groovy <username> <password>
```

**Get requirements from a specific project:**
```bash
groovy GetProjectRequirements.groovy <username> <password> <project-id>

# Example:
groovy GetProjectRequirements.groovy DBR2 'mypassword' a0be499b-3c33-45d2-96eb-d383a8900393
```

**Interactive API client:**
```bash
groovy SysMLv2ApiClient.groovy <username> <password>
```

### Output
- Results are saved to `output/` as JSON files
- Diagnostic logs are saved to `diagnostics/`

## Context7 Integration

This project uses Context7 for API documentation. Available SysML v2 resources in Context7:

- `/systems-modeling/sysml-v2-pilot-implementation` - Pilot implementation docs
- `/systems-modeling/sysml-v2-release` - Specification documents

To query Context7:
```bash
curl -X GET "https://context7.com/api/v1/search?query=sysmlv2" \
  -H "Authorization: Bearer YOUR_API_KEY"
```

## Key API Endpoints (Based on OMG Spec)

The Systems Modeling API provides REST endpoints for:

- **Projects**: Create, read, update, delete projects
- **Commits**: Version control for model changes
- **Elements**: CRUD operations on model elements
- **Relationships**: Manage relationships between elements
- **Queries**: Query model elements

## Requirements

- Python 3.8+ (for Python scripts)
- Groovy (for Groovy scripts)
- curl or similar HTTP client for testing

## License

This project is for experimental and educational purposes.
