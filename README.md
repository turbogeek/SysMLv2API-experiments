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
├── scripts/           # API interaction scripts
├── examples/          # Example requests and responses
├── docs/              # Additional documentation
└── tests/             # Test scripts and validation
```

## Getting Started

1. Ensure you have access to the SysMLv2 API endpoint (may require VPN/authentication)
2. Configure your authentication credentials
3. Run the example scripts to explore API capabilities

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
