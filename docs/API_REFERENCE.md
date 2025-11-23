# SysMLv2 API Reference

**Server**: Dassault Systèmes - CATIA Magic SysML v2
**Base URL**: `https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api`
**API Version**: 2026x
**OpenAPI Version**: 3.1.0

## Authentication

Basic Authentication required for all endpoints.

```bash
curl -k -u "username:password" https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api/projects
```

---

## Service Categories

| Category | Description |
|----------|-------------|
| **Project Services** | Create, read, update, delete projects |
| **Branch Services** | Manage branches, merge operations |
| **Commit Services** | Version control, commit history, diff/changes |
| **Element Navigation Services** | Browse and query model elements |
| **Query Services** | Create and execute queries on model data |
| **Evaluation Services** | Evaluate expressions and calculations |
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
