# Element Hierarchy

Understanding and navigating the SysML v2 element hierarchy through the API.

## Table of Contents
- [Overview](#overview)
- [Element Structure](#element-structure)
- [Navigation Patterns](#navigation-patterns)
- [Common Element Types](#common-element-types)
- [Traversal Strategies](#traversal-strategies)

## Overview

SysML v2 models are organized as a tree hierarchy of elements. Each element can contain child elements through various ownership relationships.

### Hierarchy Levels

```
Project
└── Commit
    └── Root Elements
        └── Child Elements
            └── Nested Children
                └── ...
```

## Element Structure

### Basic Element Properties

Every element has these core properties:

```groovy
{
    "@id": "element-unique-id",
    "@type": "full.qualified.TypeName",
    "name": "Element Name",              // Optional
    "declaredName": "Declared Name",     // Optional
    // ... type-specific properties
}
```

### Common Properties

| Property | Type | Description |
|----------|------|-------------|
| `@id` | String | Unique identifier (UUID) |
| `@type` | String | Fully qualified type name |
| `name` | String | Display name (optional) |
| `declaredName` | String | Declared name (optional) |
| `ownedMember` | Array/Object | Child elements |
| `ownedFeature` | Array/Object | Features owned by this element |

### Getting Element Names

Elements may have different name properties:

```groovy
String getName(Map element) {
    return element.name ?:
           element.declaredName ?:
           element['@id'].take(8) + '...'
}
```

### Getting Element Types

Extract short type name:

```groovy
String getType(Map element) {
    String fullType = element['@type'] ?: 'Unknown'
    return fullType.tokenize('.')[-1]  // Last part after dot
}
```

## Navigation Patterns

### Pattern 1: Get Root Elements

Starting point for navigation:

```groovy
// Get roots from latest commit
def roots = getRootElements(projectId, commitId, username, password)

println "Found ${roots.size()} root elements:"
roots.each { root ->
    println "  - ${root.name} (${root['@id'].take(8)}...)"
}
```

### Pattern 2: Get Element Details

Fetch full element data:

```groovy
String elementId = root['@id']
def element = getElement(projectId, commitId, elementId, username, password)

println "Element: ${element.name}"
println "Type: ${element['@type']}"
println "Properties: ${element.keySet()}"
```

### Pattern 3: Access Child Elements

Navigate to children:

```groovy
// Get owned members
def ownedMembers = element.ownedMember ?: []

// Handle both single object and array
if (ownedMembers instanceof Map) {
    ownedMembers = [ownedMembers]
}

// Process children
ownedMembers.each { child ->
    String childId = child['@id']
    def childElement = getElement(projectId, commitId, childId, username, password)
    println "  Child: ${childElement.name}"
}
```

## Common Element Types

### Packages

```groovy
{
    "@type": "Package",
    "name": "MyPackage",
    "ownedMember": [...]  // Contains other elements
}
```

### Definitions

```groovy
{
    "@type": "PartDefinition",
    "name": "Motor",
    "ownedFeature": [...]  // Features like attributes, ports
}
```

### Usages

```groovy
{
    "@type": "PartUsage",
    "name": "motor1",
    "definition": {"@id": "..."}  // Reference to definition
}
```

### Relationships

```groovy
{
    "@type": "Dependency",
    "client": [{"@id": "..."}],
    "supplier": [{"@id": "..."}]
}
```

## Traversal Strategies

### Strategy 1: Breadth-First Search

Process all elements at one level before going deeper:

```groovy
Queue<String> queue = new LinkedList<>()
Set<String> visited = new HashSet<>()

// Start with roots
roots.each { root -> queue.offer(root['@id']) }

while (!queue.isEmpty()) {
    String elementId = queue.poll()

    if (visited.contains(elementId)) continue
    visited.add(elementId)

    // Process element
    def element = getElement(projectId, commitId, elementId, username, password)
    println "Processing: ${element.name}"

    // Add children to queue
    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    children.each { child ->
        queue.offer(child['@id'])
    }
}
```

### Strategy 2: Depth-First Search

Fully explore each branch before moving to the next:

```groovy
Set<String> visited = new HashSet<>()

void traverseDepthFirst(String elementId, int depth = 0) {
    if (visited.contains(elementId)) return
    visited.add(elementId)

    def element = getElement(projectId, commitId, elementId, username, password)

    // Process element
    println "  " * depth + "${element.name} [${getType(element)}]"

    // Recurse on children
    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    children.each { child ->
        traverseDepthFirst(child['@id'], depth + 1)
    }
}

// Start traversal
roots.each { root ->
    traverseDepthFirst(root['@id'])
}
```

### Strategy 3: Limited Depth

Prevent deep recursion:

```groovy
void traverseLimited(String elementId, int depth = 0, int maxDepth = 3) {
    if (depth > maxDepth) return

    def element = getElement(projectId, commitId, elementId, username, password)
    println "  " * depth + element.name

    if (depth < maxDepth) {
        def children = element.ownedMember ?: []
        if (children instanceof Map) children = [children]

        children.each { child ->
            traverseLimited(child['@id'], depth + 1, maxDepth)
        }
    }
}
```

### Strategy 4: Filtered Traversal

Only traverse specific element types:

```groovy
void traverseFiltered(String elementId, List<String> allowedTypes) {
    def element = getElement(projectId, commitId, elementId, username, password)
    String type = getType(element)

    // Only process allowed types
    if (type in allowedTypes) {
        println "${element.name} [${type}]"

        def children = element.ownedMember ?: []
        if (children instanceof Map) children = [children]

        children.each { child ->
            traverseFiltered(child['@id'], allowedTypes)
        }
    }
}

// Example: Only traverse packages and parts
traverseFiltered(rootId, ['Package', 'PartDefinition', 'PartUsage'])
```

## Relationship Types

### Ownership Relationships

| Property | Description | Example |
|----------|-------------|---------|
| `ownedMember` | General containment | Package contains Definitions |
| `ownedFeature` | Features of an element | Part has Attributes |
| `ownedElement` | Generic ownership | Base relationship |

### Reference Relationships

| Property | Description | Example |
|----------|-------------|---------|
| `client` | Source of dependency | Requirement depends on... |
| `supplier` | Target of dependency | ...this Component |
| `source` | Start of connection | Port A connects to... |
| `target` | End of connection | ...Port B |

## Performance Considerations

### Caching

Cache fetched elements to avoid redundant API calls:

```groovy
Map<String, Map> elementCache = [:]

def getElementCached(String elementId) {
    if (!elementCache.containsKey(elementId)) {
        elementCache[elementId] = getElement(projectId, commitId, elementId, username, password)
    }
    return elementCache[elementId]
}
```

### Lazy Loading

Only load children when needed:

```groovy
class LazyElement {
    String id
    Map data
    List<LazyElement> children

    Map getData() {
        if (!data) {
            data = getElement(projectId, commitId, id, username, password)
        }
        return data
    }

    List<LazyElement> getChildren() {
        if (!children) {
            def childRefs = getData().ownedMember ?: []
            if (childRefs instanceof Map) childRefs = [childRefs]

            children = childRefs.collect { ref ->
                new LazyElement(id: ref['@id'])
            }
        }
        return children
    }
}
```

### Batch Fetching

Minimize API round-trips:

```groovy
// Instead of fetching one at a time
roots.each { root ->
    def element = getElement(root['@id'])  // N API calls
}

// Batch the work
def elements = roots.collectEntries { root ->
    [(root['@id']): getElement(root['@id'])]
}  // Still N calls, but can be parallelized
```

## Common Patterns

### Find Element by Name

```groovy
def findByName(String elementId, String targetName) {
    def element = getElement(projectId, commitId, elementId, username, password)

    if (element.name == targetName) {
        return element
    }

    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    for (child in children) {
        def found = findByName(child['@id'], targetName)
        if (found) return found
    }

    return null
}
```

### Collect All Elements of Type

```groovy
List collectByType(String elementId, String targetType, List result = []) {
    def element = getElement(projectId, commitId, elementId, username, password)

    if (getType(element) == targetType) {
        result << element
    }

    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    children.each { child ->
        collectByType(child['@id'], targetType, result)
    }

    return result
}
```

### Count Elements

```groovy
int countElements(String elementId) {
    int count = 1  // Count self

    def element = getElement(projectId, commitId, elementId, username, password)
    def children = element.ownedMember ?: []
    if (children instanceof Map) children = [children]

    children.each { child ->
        count += countElements(child['@id'])
    }

    return count
}
```

## Next Steps

- [Relationships](04_Relationships.md)
- [Example: Traverse Element Tree](../examples/06_TraverseElementTree.groovy)
- [Example: Find Elements by Type](../examples/07_FindElementsByType.groovy)
