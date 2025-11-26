# Next Session Implementation Plan

## Session Progress Summary

### ✅ Completed Today (3 commits pushed):
1. **Parallel Processing** - 73% faster startup (12s → 2.87s)
2. **Enhanced Properties** - Documentation, multiplicity, direction, specializations, redefinitions
3. **Socket Timeout** - Increased to 180s for slow endpoints

### 🔨 Ready to Implement Next Session:

## 1. ProgressDialog Helper Class
**Location**: Add after line 1950 in SysMLv2Explorer.groovy (before ProjectItem class)

```groovy
static class ProgressDialog extends JDialog {
    JProgressBar progressBar
    JLabel statusLabel
    JButton cancelButton
    volatile boolean cancelled = false

    ProgressDialog(JFrame parent, String title) {
        super(parent, title, false)  // Non-modal so user can see main window
        setupUI()
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10))

        statusLabel = new JLabel("Please wait...")
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10))
        add(statusLabel, BorderLayout.NORTH)

        progressBar = new JProgressBar()
        progressBar.setIndeterminate(true)
        progressBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10))
        add(progressBar, BorderLayout.CENTER)

        cancelButton = new JButton("Cancel")
        cancelButton.addActionListener { e ->
            cancelled = true
            cancelButton.enabled = false
            statusLabel.text = "Cancelling..."
        }

        JPanel buttonPanel = new JPanel()
        buttonPanel.add(cancelButton)
        add(buttonPanel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(parent)
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
    }

    void updateStatus(String message) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
        }
    }

    void setProgress(int current, int total) {
        SwingUtilities.invokeLater {
            if (progressBar.indeterminate) {
                progressBar.indeterminate = false
                progressBar.maximum = total
            }
            progressBar.value = current
        }
    }
}
```

## 2. Integrate Progress into loadProjects()
**Location**: Lines 478-523

Add before worker.execute():
```groovy
ProgressDialog progress = new ProgressDialog(SysMLv2ExplorerFrame.this, "Loading Projects")
progress.visible = true
```

In doInBackground(), add periodic check:
```groovy
if (progress.cancelled) {
    logDiagnostic("Project loading cancelled by user")
    return []
}
```

In done(), close dialog:
```groovy
progress.dispose()
```

## 3. Integrate Progress into loadProjectElements()
**Location**: Lines 625-710

Similar pattern:
- Create progress dialog
- Update status in doInBackground()
- Check cancelled flag in loops
- Dispose in done()

## 4. Dependency Loading
**Location**: After line 650 in loadProjectElements()

```groovy
void loadDependencies(String projectId) {
    try {
        def project = apiGet("/projects/${projectId}")
        def usages = project.projectUsages ?: []

        usages.each { usage ->
            String depId = usage.usedProject?.['@id']
            String depCommit = usage.usedCommit?.['@id']
            if (depId && depCommit) {
                logDiagnostic("Loading dependency: ${usage.usedProject?.name}")
                // Load dependency roots into cache
            }
        }
    } catch (Exception e) {
        logError("Failed to load dependencies", e)
    }
}
```

## 5. Icon Fix
**Location**: Lines 1998+ (ElementTreeCellRenderer.getTreeCellRendererComponent)

Add after getting element type:
```groovy
if (element instanceof Map) {
    String type = element['@type']?.tokenize('.')[-1] ?: ''

    // Only show folder icon for Package types
    if (type.endsWith('Package')) {
        setIcon(expanded ? UIManager.getIcon("Tree.openIcon") :
                          UIManager.getIcon("Tree.closedIcon"))
    } else {
        setIcon(UIManager.getIcon("Tree.leafIcon"))
    }
}
```

## Testing Checklist

Before each commit:
- [ ] Launch GUI and verify no syntax errors
- [ ] Test progress dialog appears
- [ ] Test cancel button works
- [ ] Test with large project (Standard Libraries1)
- [ ] Check diagnostic log for errors
- [ ] Verify no regressions

## Commit Strategy

1. **Commit 1**: ProgressDialog class
2. **Commit 2**: Progress integration for all workers
3. **Commit 3**: Dependency loading
4. **Commit 4**: Icon fixes

## Current Token Usage: ~127k/200k
Recommend starting fresh session for implementation.
