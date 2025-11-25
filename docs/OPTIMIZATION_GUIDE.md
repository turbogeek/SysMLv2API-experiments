# SysMLv2 Explorer Optimization Guide

## Performance Analysis & Improvement Plan

### Current Performance Issues Identified

#### 1. **Project Accessibility Checks** (CRITICAL - 12 second delay)
**Location:** `SysMLv2Explorer.groovy:477-511`

**Current Code:**
```groovy
projects.each { project ->
    boolean accessible = checkProjectAccessibility(project['@id'])
    // Sequential HTTP call for each of 62 projects
}
```

**Improvement:**
```groovy
import groovyx.gpars.GParsPool

GParsPool.withPool(10) {  // 10 concurrent threads
    projects.eachParallel { project ->
        boolean accessible = checkProjectAccessibility(project['@id'])
    }
}
```

**Expected:** 12s → 1-2s (83% faster)

#### 2. **HTTP Request Timeouts** (Prevents hangs)
**Location:** `SysMLv2Explorer.groovy:392-406` (initHttpClient)

**Add to HTTP client configuration:**
```groovy
import org.apache.http.client.config.RequestConfig

RequestConfig requestConfig = RequestConfig.custom()
    .setConnectTimeout(30000)        // 30 sec connection timeout
    .setSocketTimeout(60000)          // 60 sec socket timeout
    .setConnectionRequestTimeout(5000) // 5 sec from pool
    .build()

httpClient = HttpClients.custom()
    .setSSLSocketFactory(sslSocketFactory)
    .setDefaultCredentialsProvider(credentialsProvider)
    .setDefaultRequestConfig(requestConfig)
    .setMaxConnPerRoute(20)           // Connection pooling
    .setMaxConnTotal(50)
    .build()
```

#### 3. **Parallel Element Loading** (Tree loading 6s → 1-2s)
**Location:** `SysMLv2Explorer.groovy:701-738` (loadNodeChildren)

**Current:** Sequential child loading
**Improvement:** Batch-fetch children in parallel

```groovy
void loadNodeChildren(DefaultMutableTreeNode node) {
    def element = node.getUserObject()
    def members = element.ownedMember ?: []

    // Parallel fetch of all children
    GParsPool.withPool(10) {
        def children = members.collectParallel { memberRef ->
            try {
                return getElement(currentProjectId, currentCommitId, memberRef['@id'])
            } catch (Exception e) {
                logError("Failed to load child ${memberRef['@id']}", e)
                return null
            }
        }.findAll { it != null }  // Remove nulls

        // Add to tree on EDT
        SwingUtilities.invokeLater {
            children.each { child ->
                node.add(new DefaultMutableTreeNode(child, true))
            }
            treeModel.reload(node)
        }
    }
}
```

### Enhanced SysML v2 Text Details

#### 1. **Extract Missing Properties**
**Location:** `SysMLv2Explorer.groovy:786-818` (displayElementProperties)

**Add these property extractions:**
```groovy
void displayElementProperties(Map element) {
    StringBuilder props = new StringBuilder()

    // Existing properties...
    props.append("@type: ${element['@type']}\n")
    props.append("@id: ${element['@id']}\n")

    // NEW: Additional SysML v2 properties
    if (element.documentation) {
        props.append("\nDocumentation:\n${element.documentation}\n")
    }
    if (element.multiplicity) {
        props.append("Multiplicity: ${element.multiplicity}\n")
    }
    if (element.direction) {
        props.append("Direction: ${element.direction}\n")  // in/out/inout
    }
    if (element.isComposite != null) {
        props.append("Composite: ${element.isComposite}\n")
    }
    if (element.isReadOnly != null) {
        props.append("Read Only: ${element.isReadOnly}\n")
    }
    if (element.isDerived != null) {
        props.append("Derived: ${element.isDerived}\n")
    }
    if (element.isOrdered != null) {
        props.append("Ordered: ${element.isOrdered}\n")
    }
    if (element.isUnique != null) {
        props.append("Unique: ${element.isUnique}\n")
    }
    if (element.declaredType || element.type) {
        def typeRef = element.declaredType ?: element.type
        props.append("Type: ${resolveElementName(typeRef)}\n")
    }

    // Specializations
    if (element.specialization) {
        def specs = element.specialization instanceof List ?
            element.specialization : [element.specialization]
        props.append("\nSpecializations:\n")
        specs.each { spec ->
            props.append("  :> ${resolveElementName(spec.general)}\n")
        }
    }

    // Redefinitions
    if (element.redefinition) {
        def redefs = element.redefinition instanceof List ?
            element.redefinition : [element.redefinition]
        props.append("\nRedefinitions:\n")
        redefs.each { redef ->
            props.append("  :>> ${resolveElementName(redef.redefinedFeature)}\n")
        }
    }

    // ... rest of properties
}

String resolveElementName(def elementRef) {
    if (!elementRef) return "(none)"
    if (elementRef instanceof String) return elementRef
    if (elementRef['@id']) {
        def cached = elementCache[elementRef['@id']]
        return cached?.name ?: elementRef['@id'].take(12) + "..."
    }
    return "(unknown)"
}
```

#### 2. **Enhanced SysML Text Generation**
**Location:** `SysMLv2Explorer.groovy:826-905` (generateSysmlText)

**Add detailed notation:**
```groovy
String generateSysmlText(Map element, int indent = 0) {
    String type = element['@type']?.tokenize('.')[-1] ?: 'Element'
    String keyword = typeToKeyword(type)
    String name = element.name ?: element.declaredName ?: element['@id']?.take(8)

    String prefix = "  " * indent
    StringBuilder text = new StringBuilder()

    // Build declaration with modifiers
    text.append(prefix)

    // Add visibility (if present)
    if (element.visibility) {
        text.append("${element.visibility} ")
    }

    // Add modifiers
    def modifiers = []
    if (element.isAbstract) modifiers << "abstract"
    if (element.isComposite) modifiers << "composite"
    if (element.isReadOnly) modifiers << "readonly"
    if (element.isDerived) modifiers << "derived"
    if (element.isOrdered) modifiers << "ordered"
    if (element.isUnique) modifiers << "unique"

    if (modifiers) {
        text.append(modifiers.join(' ')).append(' ')
    }

    // Add direction for features
    if (element.direction) {
        text.append("${element.direction} ")
    }

    // Keyword and name
    text.append("${keyword} ${name}")

    // Add multiplicity
    if (element.multiplicity) {
        def lower = element.multiplicity.lowerBound ?: 0
        def upper = element.multiplicity.upperBound ?: '*'
        text.append(" [${lower}..${upper}]")
    }

    // Add typing
    if (element.declaredType || element.type) {
        def typeRef = element.declaredType ?: element.type
        def typeName = resolveElementName(typeRef)
        text.append(" : ${typeName}")
    }

    // Add specializations
    if (element.specialization) {
        def specs = element.specialization instanceof List ?
            element.specialization : [element.specialization]
        def specNames = specs.collect { resolveElementName(it.general) }
        if (specNames) {
            text.append(" :> ${specNames.join(', ')}")
        }
    }

    // Add redefinitions
    if (element.redefinition) {
        def redefs = element.redefinition instanceof List ?
            element.redefinition : [element.redefinition]
        def redefNames = redefs.collect { resolveElementName(it.redefinedFeature) }
        if (redefNames) {
            text.append(" :>> ${redefNames.join(', ')}")
        }
    }

    // Add default value
    if (element.defaultValue) {
        text.append(" = ${element.defaultValue}")
    }

    // ... rest of method for children
    text.append(" {\n")

    // Children...

    text.append(prefix).append("}\n")
    return text.toString()
}
```

### Icon System Improvements

#### 1. **Element Type to Icon Mapping**
**Location:** `SysMLv2Explorer.groovy:1904-1947` (ElementTreeCellRenderer)

**Add icon mapping:**
```groovy
class ElementTreeCellRenderer extends DefaultTreeCellRenderer {
    static final Icon PACKAGE_ICON = UIManager.getIcon("Tree.closedIcon")
    static final Icon FILE_ICON = UIManager.getIcon("Tree.leafIcon")
    static final Icon DEFINITION_ICON = new ColorIcon(Color.BLUE, 12)
    static final Icon USAGE_ICON = new ColorIcon(Color.CYAN, 10)
    static final Icon ATTRIBUTE_ICON = new ColorIcon(Color.GREEN, 10)
    static final Icon ACTION_ICON = new ColorIcon(Color.ORANGE, 12)
    static final Icon REQUIREMENT_ICON = new ColorIcon(Color.RED, 12)
    static final Icon PORT_ICON = new ColorIcon(Color.MAGENTA, 10)

    Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        if (value instanceof DefaultMutableTreeNode) {
            def element = value.getUserObject()
            if (element instanceof Map) {
                String type = element['@type']?.tokenize('.')[-1] ?: ''

                // Set icon based on type
                setIcon(getIconForType(type, expanded))

                // Enhanced tooltip
                setToolTipText(createTooltip(element))
            }
        }
        return this
    }

    Icon getIconForType(String type, boolean expanded) {
        switch (type) {
            case 'Package':
                return expanded ? UIManager.getIcon("Tree.openIcon") :
                                UIManager.getIcon("Tree.closedIcon")
            case 'PartDefinition':
            case 'AttributeDefinition':
            case 'ActionDefinition':
            case 'RequirementDefinition':
            case 'PortDefinition':
                return DEFINITION_ICON
            case 'PartUsage':
            case 'AttributeUsage':
            case 'ActionUsage':
            case 'RequirementUsage':
            case 'PortUsage':
                return USAGE_ICON
            case 'ConnectionDefinition':
            case 'ConnectionUsage':
                return new ColorIcon(Color.GRAY, 10)
            default:
                return FILE_ICON
        }
    }

    String createTooltip(Map element) {
        StringBuilder tooltip = new StringBuilder("<html>")
        tooltip.append("<b>${element.name ?: 'Unnamed'}</b><br>")
        tooltip.append("Type: ${element['@type']?.tokenize('.')[-1]}<br>")

        if (element.documentation) {
            String doc = element.documentation
            if (doc.length() > 100) doc = doc.take(100) + "..."
            tooltip.append("Doc: ${doc}<br>")
        }

        if (element.multiplicity) {
            tooltip.append("Multiplicity: [${element.multiplicity.lowerBound}..${element.multiplicity.upperBound}]<br>")
        }

        tooltip.append("</html>")
        return tooltip.toString()
    }
}

// Helper class for colored icons
class ColorIcon implements Icon {
    Color color
    int size

    ColorIcon(Color color, int size) {
        this.color = color
        this.size = size
    }

    void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(color)
        g.fillRect(x, y, size, size)
        g.setColor(Color.BLACK)
        g.drawRect(x, y, size, size)
    }

    int getIconWidth() { return size }
    int getIconHeight() { return size }
}
```

## Implementation Checklist

### Phase 1: Quick Wins (Priority 1)
- [ ] Add GPars dependency to @Grab annotations
- [ ] Parallelize project accessibility checks (lines 477-511)
- [ ] Add HTTP timeouts to httpClient configuration (lines 392-406)
- [ ] Extract documentation field in displayElementProperties (lines 786-818)
- [ ] Fix icon renderer to show folders only for Package type (lines 1904-1947)
- [ ] Test with Standard Libraries1 project
- [ ] Measure performance improvements

### Phase 2: Core Optimizations (Priority 2)
- [ ] Implement parallel element loading in loadNodeChildren (lines 701-738)
- [ ] Add connection pooling configuration
- [ ] Extract all SysML v2 properties (multiplicity, direction, modifiers)
- [ ] Enhance SysML text generation with full notation
- [ ] Add type name resolution helper
- [ ] Test with multiple projects

### Phase 3: Polish (Priority 3)
- [ ] Implement complete icon system for all types
- [ ] Add element tooltips with summaries
- [ ] Create ColorIcon helper class
- [ ] Add cache warming on project load
- [ ] Implement LRU cache eviction
- [ ] Add progress indicators for long operations
- [ ] Create interactive properties panel with clickable IDs

## Dependencies to Add

```groovy
@Grab('org.codehaus.gpars:gpars:1.2.1')  // For parallel processing
```

## Expected Results

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Startup (project checks) | 12s | 1-2s | 83-92% faster |
| Tree loading (100 elements) | 6s | 1-2s | 67-83% faster |
| Element detail display | Basic (8 props) | Rich (20+ props) | 2.5x more info |
| Icon accuracy | All folders | Type-specific | 100% accurate |
| Hang risk | High (PegAndHole) | None | Timeouts added |

## Testing Plan

1. **Performance Testing:**
   - Measure startup time with 62 projects
   - Measure tree loading time with Standard Libraries1 (94 elements)
   - Test with problematic projects (PegAndHole) to verify timeouts work

2. **Functionality Testing:**
   - Verify all element properties display correctly
   - Verify SysML text notation is accurate
   - Verify icons match element types
   - Test with various element types (Parts, Attributes, Requirements, etc.)

3. **Regression Testing:**
   - Ensure existing features still work
   - Verify no new errors in diagnostic log
   - Test all menu items and buttons

## Notes

- Use GPars for parallelization (already compatible with Groovy)
- Keep EDT updates separate from background processing
- Add error handling for all parallel operations
- Log performance metrics to diagnostic log
- Consider memory usage with large models
