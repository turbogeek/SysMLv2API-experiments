# SysMLv2 API Reference

**Server**: Dassault Systèmes - CATIA Magic SysML v2
**Base URL**: `https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api`
**API Version**: 2026x
**OpenAPI Version**: 3.1.0

**Based on**: OMG Systems Modeling API and Services v1.0 (September 2025)

## Authentication

Basic Authentication required for all endpoints.

```bash
curl -k -u "username:password" https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api/projects
```

---

## Specification Overview

The Systems Modeling API and Services specification defines:
- **Platform Independent Model (PIM)**: Core API model and 6 services
- **REST/HTTP PSM**: OpenAPI 3.1 specification for HTTP endpoints
- **OSLC 3.0 PSM**: Integration with OASIS OSLC standards

### Core PIM Services (OMG Spec)

| Service | Description |
|---------|-------------|
| **ProjectService** | CRUD operations on Projects |
| **ElementNavigationService** | Browse and query model elements |
| **ProjectDataVersioningService** | Commits, branches, tags, diff/merge |
| **QueryService** | Create, save, and execute queries |
| **ExternalRelationshipService** | Link elements to external resources |
| **ProjectUsageService** | Manage project dependencies |

### Dassault Extensions

| Service | Description |
|---------|-------------|
| **Evaluation Services** | Execute calculations and expressions |

---

## Service Categories

| Category | Description |
|----------|-------------|
| **Project Services** | Create, read, update, delete projects |
| **Branch Services** | Manage branches, merge operations |
| **Commit Services** | Version control, commit history, diff/changes |
| **Element Navigation Services** | Browse and query model elements |
| **Query Services** | Create and execute queries on model data |
| **Evaluation Services** | Evaluate expressions and calculations (Dassault extension) |
| **Tag Services** | Create and manage tags on commits |

---

## 1. Project Services

### List All Projects
```
GET /api/projects
```
Returns an array of all projects.

**Response**: `ProjectListItem[]`

**Example**:
```bash
curl -k -u "user:pass" "https://server:8443/sysmlv2-api/api/projects"
```

### Get Project by ID
```
GET /api/projects/{projectId}
```

**Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| projectId | string (UUID) | Yes | Project identifier |

### Create Project
```
POST /api/projects
```

**Request Body**: `ProjectListItem`
```json
{
  "name": "My New Project",
  "description": "Project description",
  "@type": "Project"
}
```

### Update Project
```
PUT /api/projects/{projectId}
```

### Delete Project
```
DELETE /api/projects/{projectId}
```

---

## 2. Branch Services

### List Branches
```
GET /api/projects/{projectId}/branches
```
Returns all branches for a project.

### Get Branch by ID
```
GET /api/projects/{projectId}/branches/{branchId}
```

### Create Branch
```
POST /api/projects/{projectId}/branches
```

**Request Body**: `Branch`
```json
{
  "name": "feature-branch",
  "description": "Feature development branch",
  "referencedCommit": {"@id": "commit-uuid"}
}
```

### Delete Branch
```
DELETE /api/projects/{projectId}/branches/{branchId}
```

### Merge Branch
```
POST /api/projects/{projectId}/branches/{targetBranchId}/merge
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| sourceCommitId | string | Yes | Source commit to merge |
| description | string | No | Merge description |

**Response**: `BranchMerge`
```json
{
  "mergeCommit": { /* Commit object */ },
  "conflict": [ /* Array of conflicting DataIdentity objects */ ]
}
```

---

## 3. Commit Services

### List Commits
```
GET /api/projects/{projectId}/commits
```
Returns all commits for a project.

### Get Commit by ID
```
GET /api/projects/{projectId}/commits/{commitId}
```

### Create Commit
```
POST /api/projects/{projectId}/commits
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| branchId | string | No | Target branch for commit |

**Request Body**: `CommitRequest`
```json
{
  "@type": "Commit",
  "description": "Commit message",
  "change": [
    {
      "@type": "DataVersion",
      "identity": {"@id": "element-uuid"},
      "payload": { /* Element data */ }
    }
  ]
}
```

### Get Commit Changes
```
GET /api/projects/{projectId}/commits/{commitId}/changes
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| changeTypes | string | No | Filter: ADDED, UPDATED, DELETED (comma-separated) |

### Get Specific Change
```
GET /api/projects/{projectId}/commits/{commitId}/changes/{changeId}
```

### Diff Between Commits
```
GET /api/projects/{projectId}/commits/{commitId}/diff
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| baseCommitId | string | Yes | Base commit for comparison |
| changeTypes | string | No | Filter: ADDED, UPDATED, DELETED |

**Response**: `DataDifference[]`

---

## 4. Element Navigation Services

### Get All Elements
```
GET /api/projects/{projectId}/commits/{commitId}/elements
```
Returns all elements in a commit.

**Example**:
```bash
curl -k -u "user:pass" \
  "https://server:8443/sysmlv2-api/api/projects/{projectId}/commits/{commitId}/elements?page[size]=100"
```

### Get Root Elements
```
GET /api/projects/{projectId}/commits/{commitId}/roots
```
Returns only root-level elements (top of hierarchy).

### Get Element by ID
```
GET /api/projects/{projectId}/commits/{commitId}/elements/{elementId}
```

### Get Element Relationships
```
GET /api/projects/{projectId}/commits/{commitId}/elements/{relatedElementId}/relationships
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| direction | enum | Yes | BOTH, IN, OUT |

---

## 5. Query Services

### List Saved Queries
```
GET /api/projects/{projectId}/queries
```

### Get Query by ID
```
GET /api/projects/{projectId}/queries/{queryId}
```

### Create Query
```
POST /api/projects/{projectId}/queries
```

**Request Body**: `Query`
```json
{
  "name": "Find All Requirements",
  "description": "Query for requirement elements",
  "select": ["@id", "name", "@type", "qualifiedName"],
  "where": {
    "@type": "PrimitiveConstraint",
    "property": "@type",
    "operator": "=",
    "value": ["RequirementUsage"]
  }
}
```

### Update Query
```
PUT /api/projects/{projectId}/queries/{queryId}
```

### Delete Query
```
DELETE /api/projects/{projectId}/queries/{queryId}
```

### Execute Query (Ad-hoc)
```
POST /api/projects/{projectId}/query-results
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| commitId | string | No | Specific commit to query |

**Request Body**: `Query`

### Execute Saved Query
```
GET /api/projects/{projectId}/queries/{queryId}/results
```

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| commitId | string | No | Specific commit to query |

---

## 6. Evaluation Services

### Evaluate Expressions
```
POST /api/projects/{projectId}/evaluate
POST /api/projects/{projectId}/commits/{commitId}/evaluate
```

Evaluate calculations and expressions in a SysML v2 model.

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| context | string | No | Context path (e.g., `SpacecraftMassRollup::spaceCraft`) |

**Request Body**: `EvaluationRequest`
```json
{
  "inputs": {
    "propulsion.thruster.me": 10,
    "telecom.amplifier.me": 15
  },
  "outputs": [
    "telecom.me",
    "telecom.antenna.me",
    "propulsion.tank"
  ],
  "expressions": [
    "1+1",
    "telecom.antenna.me + 10"
  ]
}
```

### Evaluation Table View
```
GET /api/projects/{projectId}/evaluate/table-view
GET /api/projects/{projectId}/commits/{commitId}/evaluate/table-view
```

Returns an interactive HTML table for evaluation results.

**Query Parameters**:
| Name | Type | Required | Description |
|------|------|----------|-------------|
| context | string | No | Context path |
| params | string | No | Comma-separated list of parameters (use `all` for all) |
| title | string | No | Table title |
| verification | enum | No | ALL, NONE, FAIL (default: FAIL) |

---

## 7. Tag Services

### List Tags
```
GET /api/projects/{projectId}/tags
```

### Get Tag by ID
```
GET /api/projects/{projectId}/tags/{tagId}
```

### Create Tag
```
POST /api/projects/{projectId}/tags
```

**Request Body**: `Tag`
```json
{
  "name": "v1.0.0",
  "description": "Release version 1.0.0",
  "referencedCommit": {"@id": "commit-uuid"}
}
```

### Delete Tag
```
DELETE /api/projects/{projectId}/tags/{tagId}
```

---

## Data Models

### ProjectListItem
```json
{
  "created": "2025-01-15T22:17:46.407Z",
  "@id": "uuid",
  "description": "Project description",
  "name": "Project Name",
  "@type": "Project",
  "defaultBranch": {
    "@id": "branch-uuid",
    "name": "trunk"
  }
}
```

### Commit
```json
{
  "created": "2025-01-15T22:17:45.865Z",
  "@id": "uuid",
  "description": "Commit message",
  "name": "1",
  "owningProject": {"@id": "project-uuid"},
  "previousCommit": [{"@id": "previous-commit-uuid"}]
}
```

### Branch
```json
{
  "created": "2025-01-15T22:17:45.865Z",
  "@id": "uuid",
  "description": "Branch description",
  "name": "trunk",
  "referencedCommit": {"@id": "commit-uuid"},
  "owningProject": {"@id": "project-uuid"},
  "head": {"@id": "head-commit-uuid"}
}
```

### Element
```json
{
  "@id": "uuid",
  "@type": "PartUsage",
  "name": "elementName",
  "qualifiedName": "Package::Element",
  "shortName": "elem",
  "elementId": "internal-id",
  "isImpliedIncluded": true,
  "isLibraryElement": false,
  "owner": {"@id": "owner-uuid"},
  "owningMembership": {"@id": "membership-uuid"},
  "owningNamespace": {"@id": "namespace-uuid"},
  "ownedElement": [{"@id": "child-uuid"}],
  "ownedRelationship": [{"@id": "relationship-uuid"}]
}
```

### Query (with Constraints)
```json
{
  "@id": "uuid",
  "name": "Query Name",
  "description": "Query description",
  "select": ["@id", "name", "@type"],
  "where": {
    "@type": "CompositeConstraint",
    "operator": "AND",
    "constraint": [
      {
        "@type": "PrimitiveConstraint",
        "property": "@type",
        "operator": "=",
        "value": ["RequirementUsage"],
        "inverse": false
      }
    ]
  },
  "scope": [{"@id": "element-uuid"}]
}
```

---

## Common SysML v2 Element Types

| Type | Description |
|------|-------------|
| `Package` | Container for model elements |
| `Namespace` | Named container |
| `PartDefinition` | Definition of a part type |
| `PartUsage` | Instance/usage of a part |
| `RequirementDefinition` | Definition of a requirement |
| `RequirementUsage` | Instance of a requirement |
| `ConstraintDefinition` | Definition of a constraint |
| `ConstraintUsage` | Instance of a constraint |
| `ActionDefinition` | Definition of an action/behavior |
| `ActionUsage` | Instance of an action |
| `StateDefinition` | Definition of a state |
| `StateUsage` | Instance of a state |
| `ConnectionUsage` | Connection between parts |
| `InterfaceDefinition` | Interface type definition |
| `PortUsage` | Port instance |
| `AttributeUsage` | Attribute/property |
| `FeatureMembership` | Membership relationship |
| `Subsetting` | Subsetting relationship |
| `Subclassification` | Specialization relationship |
| `FeatureTyping` | Type relationship |

---

## Pagination

Elements endpoint supports pagination:

```
GET /api/projects/{projectId}/commits/{commitId}/elements?page[size]=100&page[after]=0
```

---

## Error Responses

```json
{
  "timestamp": "2025-11-23T20:23:58.792Z",
  "error": "Not authorized to process GetRevisionsRangeInResourceMsg - project-uuid."
}
```

Common errors:
- **404**: Resource not found
- **401/403**: Not authorized (check credentials or project permissions)
- **500**: Server error

---

## OpenAPI Spec

The full OpenAPI 3.1.0 specification is available at:
```
GET /api/v3/api-docs
```

Swagger config:
```
GET /api/v3/api-docs/swagger-config
```

---

## PIM Data Model (OMG Spec)

### Record (Base Type)

All API data types inherit from Record:

| Attribute | Type | Description |
|-----------|------|-------------|
| id | UUID | Unique identifier (read-only) |
| resourceIdentifier | IRI | Optional IRI for the record |
| alias | String[] | Alternative identifiers |
| name | String | Human-friendly identifier (must be in alias set) |
| description | String | Details about the record |

### Data Interface

Data represents entities that can be created, updated, deleted, and queried:
- **Element** - Root metaclass in SysML v2 language metamodel
- **ExternalData** - Resources available over the web via IRI
- **ExternalRelationship** - Links between Elements and ExternalData
- **ProjectUsage** - Project dependency references

### DataIdentity

Version-independent representation of Data through its lifecycle:

| Attribute | Type | Description |
|-----------|------|-------------|
| createdAt | Commit | Commit where Data was created (derived) |
| deletedAt | Commit | Commit where Data was deleted (derived) |

### DataVersion

Data at a specific version in its lifecycle:

| Attribute | Type | Description |
|-----------|------|-------------|
| commit | Commit | Commit where payload was created/modified/deleted |
| identity | DataIdentity | Reference to version-independent identity |
| payload | Data | The actual data (null if deleted) |

### Commit Semantics

- **Immutable**: Commit.change cannot be modified after creation
- **Non-destructible**: Cannot be deleted during normal operation
- **Monotonic timestamps**: Each commit's `created` must be newer than all previousCommits

**Change Types** (ChangeType enumeration):
- `CREATED` - New data added
- `UPDATED` - Existing data modified
- `DELETED` - Data removed (payload = null)

### Branch vs Tag

| Type | Mutable | Destructible | Description |
|------|---------|--------------|-------------|
| **Commit** | No | No | Immutable history |
| **Branch** | Yes | Yes | Pointer to head commit, can be updated |
| **Tag** | No | Yes | Fixed pointer to a specific commit |

---

## 8. ExternalRelationship Service (OMG Spec)

Links KerML Elements to external web resources via IRI.

### Get External Relationships
```
GET /api/projects/{projectId}/commits/{commitId}/external-relationships
```

### Get External Relationship by ID
```
GET /api/projects/{projectId}/commits/{commitId}/external-relationships/{externalRelationshipId}
```

### Create External Relationship
```
POST /api/projects/{projectId}/commits?branchId={branchId}
```

**ExternalRelationship Model**:
```json
{
  "@type": "ExternalRelationship",
  "elementEnd": {"@id": "element-uuid"},
  "externalDataEnd": {
    "resourceIdentifier": "https://example.com/resource"
  },
  "specification": "mapping expression",
  "language": "expression-language-name"
}
```

---

## 9. ProjectUsage Service (OMG Spec)

Manages dependencies between projects.

### Get Project Usages
```
GET /api/projects/{projectId}/commits/{commitId}/project-usages
```

### Create Project Usage
```
POST /api/projects/{projectId}/commits?branchId={branchId}
```

**ProjectUsage Model**:
```json
{
  "@type": "ProjectUsage",
  "usedProjectCommit": {"@id": "commit-uuid-of-used-project"}
}
```

### Delete Project Usage
```
POST /api/projects/{projectId}/commits?branchId={branchId}
```
(With DataVersion.payload = null and DataVersion.identity = projectUsageId)

---

## Query Constraint Syntax (OMG Spec)

### PrimitiveConstraint

Simple property-operator-value conditions:

| Attribute | Type | Description |
|-----------|------|-------------|
| property | String | Property being constrained |
| operator | Operator | Comparison operator |
| value | Any[] | Value(s) to compare |
| inverse | Boolean | Apply logical NOT |

**Operators**:
- `=` - Equals
- `<` - Less than
- `<=` - Less than or equal
- `>` - Greater than
- `>=` - Greater than or equal
- `in` - Value in set
- `instanceOf` - Type check

### CompositeConstraint

Combines multiple constraints:

| Attribute | Type | Description |
|-----------|------|-------------|
| constraint | Constraint[] | 2+ constraints to combine |
| operator | JoinOperator | `and` or `or` |

**Example - Complex Query**:
```json
{
  "name": "Requirements with specific owner",
  "select": ["@id", "name", "qualifiedName"],
  "where": {
    "@type": "CompositeConstraint",
    "operator": "and",
    "constraint": [
      {
        "@type": "PrimitiveConstraint",
        "property": "@type",
        "operator": "=",
        "value": ["RequirementUsage"],
        "inverse": false
      },
      {
        "@type": "PrimitiveConstraint",
        "property": "owner.name",
        "operator": "=",
        "value": ["SystemRequirements"],
        "inverse": false
      }
    ]
  }
}
```

---

## Timestamps

All timestamps use ISO8601DateTime format in UTC or with explicit timezone offset:

**Valid formats**:
- `2024-12-18T16:45:34.635726Z` (UTC)
- `2024-12-18T16:45:34.635726+00:00` (UTC explicit)
- `2024-12-18T11:45:34.635726-05:00` (with offset)

---

## References

### Specifications
- **OMG Systems Modeling API and Services v1.0**: https://www.omg.org/spec/SystemsModelingAPI/1.0

### Implementation Resources
- **SysML v2 Pilot Implementation**: https://github.com/Systems-Modeling/SysML-v2-Pilot-Implementation
- **SysML v2 API Services**: https://github.com/Systems-Modeling/SysML-v2-API-Services
- **SysML v2 API Cookbook**: https://github.com/Systems-Modeling/SysML-v2-API-Cookbook (usage patterns and examples)

### Related Standards
- **OSLC Configuration Management**: https://oslc-op.github.io/oslc-specs/specs/config/oslc-config-mgt.html
