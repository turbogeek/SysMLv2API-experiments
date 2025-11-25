#!/usr/bin/env groovy
/**
 * SysMLv2Explorer.groovy
 *
 * A Swing GUI tool for exploring SysML v2 projects via the API.
 * Features:
 *   - Tree view of project elements
 *   - Properties panel showing element details
 *   - SysML v2 textual notation preview
 *   - Export selected element or entire project to .sysml file
 *   - Secure credential handling (passwords never displayed)
 *
 * Usage: groovy SysMLv2Explorer.groovy [username] [password]
 *   - Credentials can be provided via:
 *     1. Command line arguments (not recommended for demos)
 *     2. Environment variables: SYSMLV2_USERNAME, SYSMLV2_PASSWORD
 *     3. credentials.properties file
 *     4. Login dialog (password masked)
 */

@Grab('org.apache.httpcomponents:httpclient:4.5.14')
@Grab('org.apache.httpcomponents:httpcore:4.4.16')
@Grab('com.google.code.gson:gson:2.10.1')
@Grab('org.codehaus.gpars:gpars:1.2.1')

import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import org.apache.http.auth.*
import org.apache.http.conn.ssl.*
import org.apache.http.ssl.*
import org.apache.http.client.config.RequestConfig
import javax.net.ssl.*
import java.security.cert.X509Certificate
import com.google.gson.*
import groovyx.gpars.GParsPool

import javax.swing.*
import javax.swing.tree.*
import javax.swing.event.*
import javax.swing.border.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.*
import java.util.List  // Explicit import to avoid java.awt.List collision

class SysMLv2ExplorerFrame extends JFrame {

    static final String BASE_URL = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"
    static final String OUTPUT_DIR = "../output"
    static final String DIAGNOSTIC_DIR = "../diagnostics"
    static final String DIAGNOSTIC_FILE = "${DIAGNOSTIC_DIR}/explorer_diagnostic.log"

    // API client
    String username
    String password
    CloseableHttpClient httpClient
    Gson gson = new GsonBuilder().setPrettyPrinting().create()

    // Diagnostic logging
    static File diagnosticLog

    static void initDiagnostics() {
        new File(DIAGNOSTIC_DIR).mkdirs()
        diagnosticLog = new File(DIAGNOSTIC_FILE)
        diagnosticLog.text = "=== SysMLv2Explorer Diagnostic Log ===\n"
        diagnosticLog.append("Started: ${new Date()}\n\n")
    }

    static void logDiagnostic(String message) {
        String timestamp = new Date().format("yyyy-MM-dd HH:mm:ss.SSS")
        String logLine = "[${timestamp}] ${message}\n"
        println logLine.trim()  // Also print to console
        if (diagnosticLog) {
            diagnosticLog.append(logLine)
        }
    }

    static void logError(String context, Throwable e) {
        logDiagnostic("ERROR in ${context}: ${e.class.simpleName}: ${e.message}")
        StringWriter sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        if (diagnosticLog) {
            diagnosticLog.append("Stack trace:\n${sw.toString()}\n")
        }
    }

    /**
     * Show a user-friendly error dialog with wrapped text
     */
    static void showErrorDialog(Component parent, String title, String message, Throwable e = null) {
        // Truncate and wrap message for readability
        String shortMessage = message
        if (message.length() > 200) {
            shortMessage = message.take(200) + "..."
        }

        // Create a wrapped text area for the message
        JTextArea textArea = new JTextArea(shortMessage)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.editable = false
        textArea.background = null
        textArea.border = null
        textArea.font = UIManager.getFont("Label.font")

        JScrollPane scrollPane = new JScrollPane(textArea)
        scrollPane.preferredSize = new Dimension(400, 150)
        scrollPane.border = null

        String fullMessage = e ? "${shortMessage}\n\nError type: ${e.class.simpleName}" : shortMessage

        JOptionPane.showMessageDialog(parent, scrollPane, title, JOptionPane.ERROR_MESSAGE)

        // Log full error details
        if (e) {
            logError(title, e)
        }
    }

    // Current state
    String currentProjectId
    String currentCommitId
    Map<String, Map> elementCache = [:]
    boolean allowProjectSelection = false  // Prevent auto-load on startup

    // UI Components
    JComboBox<ProjectItem> projectCombo
    JComboBox<CommitItem> commitCombo
    JTree elementTree
    DefaultTreeModel treeModel
    JTextArea propertiesArea
    JTextArea sysmlTextArea
    JLabel statusLabel
    JSplitPane mainSplit
    JSplitPane rightSplit
    List<Map> currentCommits = []  // Store commits for current project

    SysMLv2ExplorerFrame(String username = null, String password = null) {
        super("SysML v2 Explorer")
        initDiagnostics()
        logDiagnostic("Initializing SysMLv2ExplorerFrame")

        this.username = username
        this.password = password

        try {
            initUI()
            logDiagnostic("UI initialized successfully")

            if (username && password) {
                logDiagnostic("Credentials provided, username: ${username}, password: ***masked***")
                initHttpClient()
                loadProjects()
            } else {
                logDiagnostic("No credentials provided, showing login dialog")
                showLoginDialog()
            }
        } catch (Exception e) {
            logError("Constructor", e)
            throw e
        }
    }

    void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
        setSize(1200, 800)
        setLocationRelativeTo(null)

        // Menu bar
        JMenuBar menuBar = new JMenuBar()

        JMenu fileMenu = new JMenu("File")
        fileMenu.setMnemonic(KeyEvent.VK_F)

        JMenuItem loginItem = new JMenuItem("Login...", KeyEvent.VK_L)
        loginItem.addActionListener { showLoginDialog() }
        fileMenu.add(loginItem)

        JMenuItem refreshItem = new JMenuItem("Refresh", KeyEvent.VK_R)
        refreshItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)
        refreshItem.addActionListener { refreshCurrentProject() }
        fileMenu.add(refreshItem)

        fileMenu.addSeparator()

        JMenuItem exportItem = new JMenuItem("Export to SysML...", KeyEvent.VK_E)
        exportItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
        exportItem.addActionListener { exportToFile() }
        fileMenu.add(exportItem)

        JMenuItem exportHtmlItem = new JMenuItem("Export to HTML...", KeyEvent.VK_H)
        exportHtmlItem.addActionListener { exportToHTML() }
        fileMenu.add(exportHtmlItem)

        fileMenu.addSeparator()

        JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X)
        exitItem.addActionListener { dispose() }
        fileMenu.add(exitItem)

        menuBar.add(fileMenu)

        JMenu viewMenu = new JMenu("View")
        viewMenu.setMnemonic(KeyEvent.VK_V)

        JMenuItem expandAllItem = new JMenuItem("Expand All", KeyEvent.VK_E)
        expandAllItem.addActionListener { expandAllNodes() }
        viewMenu.add(expandAllItem)

        JMenuItem collapseAllItem = new JMenuItem("Collapse All", KeyEvent.VK_C)
        collapseAllItem.addActionListener { collapseAllNodes() }
        viewMenu.add(collapseAllItem)

        viewMenu.addSeparator()

        JMenuItem statsItem = new JMenuItem("Model Statistics...", KeyEvent.VK_S)
        statsItem.addActionListener { showModelStatistics() }
        viewMenu.add(statsItem)

        JMenuItem traceItem = new JMenuItem("Traceability Matrix...", KeyEvent.VK_T)
        traceItem.addActionListener { showTraceabilityMatrix() }
        viewMenu.add(traceItem)

        JMenuItem diffItem = new JMenuItem("Commit Diff Viewer...", KeyEvent.VK_D)
        diffItem.addActionListener { showCommitDiffViewer() }
        viewMenu.add(diffItem)

        menuBar.add(viewMenu)

        JMenu helpMenu = new JMenu("Help")
        helpMenu.setMnemonic(KeyEvent.VK_H)

        JMenuItem aboutItem = new JMenuItem("About", KeyEvent.VK_A)
        aboutItem.addActionListener { showAboutDialog() }
        helpMenu.add(aboutItem)

        menuBar.add(helpMenu)

        setJMenuBar(menuBar)

        // Main content
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5))
        mainPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        // Top toolbar
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT))
        toolbarPanel.add(new JLabel("Project:"))

        projectCombo = new JComboBox<>()
        projectCombo.preferredSize = new Dimension(350, 25)
        projectCombo.renderer = new ProjectComboRenderer()
        projectCombo.addActionListener { onProjectSelected() }
        toolbarPanel.add(projectCombo)

        toolbarPanel.add(new JLabel("  Commit:"))

        commitCombo = new JComboBox<>()
        commitCombo.preferredSize = new Dimension(300, 25)
        commitCombo.enabled = false  // Disabled until project selected
        commitCombo.addActionListener { onCommitSelected() }
        toolbarPanel.add(commitCombo)

        JButton loadAllBtn = new JButton("Load All")
        loadAllBtn.toolTipText = "Recursively load all elements in tree"
        loadAllBtn.addActionListener { loadAllElements() }
        toolbarPanel.add(loadAllBtn)

        JButton refreshBtn = new JButton("Refresh")
        refreshBtn.addActionListener { refreshCurrentProject() }
        toolbarPanel.add(refreshBtn)

        mainPanel.add(toolbarPanel, BorderLayout.NORTH)

        // Left panel - Tree
        JPanel treePanel = new JPanel(new BorderLayout())
        treePanel.border = BorderFactory.createTitledBorder("Elements")

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("No Project Loaded")
        treeModel = new DefaultTreeModel(rootNode)
        elementTree = new JTree(treeModel)
        elementTree.cellRenderer = new ElementTreeCellRenderer()
        elementTree.addTreeSelectionListener { onElementSelected(it) }
        elementTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            void treeWillExpand(TreeExpansionEvent e) { onNodeExpanding(e) }
            void treeWillCollapse(TreeExpansionEvent e) {}
        })

        JScrollPane treeScroll = new JScrollPane(elementTree)
        treeScroll.preferredSize = new Dimension(350, 600)
        treePanel.add(treeScroll, BorderLayout.CENTER)

        // Right panel - Details
        JPanel detailsPanel = new JPanel(new BorderLayout())

        // Properties panel
        JPanel propsPanel = new JPanel(new BorderLayout())
        propsPanel.border = BorderFactory.createTitledBorder("Properties")

        propertiesArea = new JTextArea()
        propertiesArea.editable = false
        propertiesArea.font = new Font("Monospaced", Font.PLAIN, 12)
        JScrollPane propsScroll = new JScrollPane(propertiesArea)
        propsPanel.add(propsScroll, BorderLayout.CENTER)

        // SysML Text panel
        JPanel sysmlPanel = new JPanel(new BorderLayout())
        sysmlPanel.border = BorderFactory.createTitledBorder("SysML v2 Text")

        sysmlTextArea = new JTextArea()
        sysmlTextArea.editable = false
        sysmlTextArea.font = new Font("Monospaced", Font.PLAIN, 12)
        sysmlTextArea.tabSize = 4
        JScrollPane sysmlScroll = new JScrollPane(sysmlTextArea)
        sysmlPanel.add(sysmlScroll, BorderLayout.CENTER)

        // Copy button for SysML text
        JPanel sysmlButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        JButton copyBtn = new JButton("Copy to Clipboard")
        copyBtn.addActionListener { copyToClipboard() }
        sysmlButtonPanel.add(copyBtn)

        JButton exportBtn = new JButton("Export...")
        exportBtn.addActionListener { exportToFile() }
        sysmlButtonPanel.add(exportBtn)

        sysmlPanel.add(sysmlButtonPanel, BorderLayout.SOUTH)

        // Split between properties and SysML text
        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, propsPanel, sysmlPanel)
        rightSplit.dividerLocation = 250
        rightSplit.resizeWeight = 0.3

        detailsPanel.add(rightSplit, BorderLayout.CENTER)

        // Main split
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, detailsPanel)
        mainSplit.dividerLocation = 350
        mainSplit.resizeWeight = 0.3

        mainPanel.add(mainSplit, BorderLayout.CENTER)

        // Status bar
        statusLabel = new JLabel("Ready")
        statusLabel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        )
        mainPanel.add(statusLabel, BorderLayout.SOUTH)

        contentPane = mainPanel
    }

    void showLoginDialog() {
        JPanel panel = new JPanel(new GridBagLayout())
        GridBagConstraints gbc = new GridBagConstraints()
        gbc.insets = new Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(new JLabel("Username:"), gbc)

        JTextField userField = new JTextField(username ?: "", 20)
        gbc.gridx = 1
        panel.add(userField, gbc)

        gbc.gridx = 0; gbc.gridy = 1
        panel.add(new JLabel("Password:"), gbc)

        JPasswordField passField = new JPasswordField(password ?: "", 20)
        gbc.gridx = 1
        panel.add(passField, gbc)

        int result = JOptionPane.showConfirmDialog(this, panel, "Login to SysML v2 API",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)

        if (result == JOptionPane.OK_OPTION) {
            username = userField.text
            password = new String(passField.password)

            if (username && password) {
                initHttpClient()
                loadProjects()
            }
        }
    }

    void initHttpClient() {
        def trustStrategy = { X509Certificate[] chain, String authType -> true } as TrustStrategy
        def sslContext = SSLContexts.custom()
            .loadTrustMaterial(null, trustStrategy)
            .build()
        def sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)

        def credentialsProvider = new BasicCredentialsProvider()
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

        // Configure timeouts to prevent hangs
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(30000)           // 30 sec connection timeout
            .setSocketTimeout(60000)            // 60 sec socket timeout
            .setConnectionRequestTimeout(5000)  // 5 sec from pool timeout
            .build()

        httpClient = HttpClients.custom()
            .setSSLSocketFactory(sslSocketFactory)
            .setDefaultCredentialsProvider(credentialsProvider)
            .setDefaultRequestConfig(requestConfig)
            .setMaxConnPerRoute(20)             // Connection pooling
            .setMaxConnTotal(50)
            .build()

        logDiagnostic("HTTP client initialized with timeouts: connect=30s, socket=60s, pool=5s")
    }

    Map apiGet(String endpoint) {
        logDiagnostic("API GET: ${endpoint}")
        try {
            def request = new HttpGet("${BASE_URL}${endpoint}")
            request.setHeader("Accept", "application/json")

            def response = httpClient.execute(request)
            int statusCode = response.statusLine.statusCode
            def content = response.entity.content.text
            response.close()

            logDiagnostic("API Response: status=${statusCode}, length=${content?.length() ?: 0}")

            if (statusCode >= 400) {
                logDiagnostic("API Error Response: ${content?.take(500)}")
                throw new RuntimeException("API returned status ${statusCode}: ${content?.take(200)}")
            }

            return gson.fromJson(content, Map)
        } catch (Exception e) {
            logError("apiGet(${endpoint})", e)
            throw e
        }
    }

    List apiGetList(String endpoint) {
        logDiagnostic("API GET List: ${endpoint}")
        try {
            def request = new HttpGet("${BASE_URL}${endpoint}")
            request.setHeader("Accept", "application/json")

            def response = httpClient.execute(request)
            int statusCode = response.statusLine.statusCode
            def content = response.entity.content.text
            response.close()

            logDiagnostic("API Response: status=${statusCode}, items=${content?.length() ?: 0} chars")

            if (statusCode >= 400) {
                logDiagnostic("API Error Response: ${content?.take(500)}")
                throw new RuntimeException("API returned status ${statusCode}")
            }

            // Use ArrayList explicitly to avoid java.awt.List confusion
            return gson.fromJson(content, ArrayList.class)
        } catch (Exception e) {
            logError("apiGetList(${endpoint})", e)
            throw e
        }
    }

    void loadProjects() {
        setStatus("Loading projects...")
        logDiagnostic("Starting loadProjects()")

        SwingWorker worker = new SwingWorker<ArrayList, Void>() {
            @Override
            protected ArrayList doInBackground() {
                return apiGetList("/projects") as ArrayList
            }

            @Override
            protected void done() {
                try {
                    ArrayList projects = get()
                    logDiagnostic("Received ${projects?.size() ?: 0} projects")
                    projectCombo.removeAllItems()

                    // Check accessibility for each project in parallel for faster startup
                    long startTime = System.currentTimeMillis()
                    logDiagnostic("Starting parallel accessibility checks for ${projects.size()} projects...")

                    def projectItems = Collections.synchronizedList([])

                    GParsPool.withPool(10) {  // 10 concurrent threads
                        projects.eachParallel { p ->
                            String projectId = p['@id'] as String
                            String projectName = p['name'] as String
                            boolean accessible = checkProjectAccessibility(projectId)
                            projectItems << new ProjectItem(projectId, projectName, accessible)
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startTime
                    logDiagnostic("Parallel accessibility checks completed in ${elapsed}ms (${projects.size()} projects)")

                    // Add all items to combo box
                    projectItems.each { projectCombo.addItem(it) }

                    setStatus("Loaded ${projects.size()} projects (${elapsed}ms)")
                    logDiagnostic("Projects loaded successfully")
                    allowProjectSelection = true  // Enable user selections now
                } catch (Exception e) {
                    setStatus("Error loading projects")
                    showErrorDialog(SysMLv2ExplorerFrame.this, "Load Projects Error",
                        "Failed to load projects from server.", e)
                }
            }
        }
        worker.execute()
    }

    /**
     * Check if a project is accessible by trying to fetch its commits
     */
    boolean checkProjectAccessibility(String projectId) {
        try {
            def request = new HttpGet("${BASE_URL}/projects/${projectId}/commits")
            request.setHeader("Accept", "application/json")
            def response = httpClient.execute(request)
            int statusCode = response.statusLine.statusCode
            response.close()
            return statusCode == 200
        } catch (Exception e) {
            return false
        }
    }

    void onProjectSelected() {
        if (!allowProjectSelection) {
            return  // Skip auto-load during initialization
        }
        ProjectItem selected = projectCombo.selectedItem as ProjectItem
        if (selected) {
            if (!selected.accessible) {
                setStatus("Project ${selected.name} is not accessible")
                commitCombo.removeAllItems()
                commitCombo.enabled = false
                treeModel.setRoot(new DefaultMutableTreeNode("Project Not Accessible"))
                return
            }
            loadCommitsForProject(selected.id)
        }
    }

    /**
     * Load commits for the selected project and populate commit dropdown
     */
    void loadCommitsForProject(String projectId) {
        setStatus("Loading commits...")
        commitCombo.removeAllItems()
        commitCombo.enabled = false

        SwingWorker worker = new SwingWorker<ArrayList, Void>() {
            @Override
            protected ArrayList doInBackground() {
                return apiGetList("/projects/${projectId}/commits") as ArrayList
            }

            @Override
            protected void done() {
                try {
                    ArrayList commits = get()
                    logDiagnostic("Received ${commits?.size() ?: 0} commits for project ${projectId}")
                    currentCommits = commits

                    if (commits && !commits.isEmpty()) {
                        // Latest commit is at the end of the list
                        commits.eachWithIndex { commit, index ->
                            String commitId = commit['@id'] as String
                            String timestamp = commit['timestamp'] ?: commit['created'] ?: 'Unknown date'
                            boolean isLatest = (index == commits.size() - 1)
                            commitCombo.addItem(new CommitItem(commitId, timestamp, isLatest))
                        }

                        // Select the latest commit by default
                        commitCombo.setSelectedIndex(commits.size() - 1)
                        commitCombo.enabled = true
                        setStatus("Loaded ${commits.size()} commits")
                    } else {
                        setStatus("No commits found for project")
                        commitCombo.addItem(new CommitItem('none', 'No commits available', false))
                    }
                } catch (Exception e) {
                    setStatus("Error loading commits")
                    logError("loadCommitsForProject", e)
                    showErrorDialog(SysMLv2ExplorerFrame.this, "Load Commits Error",
                        "Failed to load commits for project.", e)
                }
            }
        }
        worker.execute()
    }

    /**
     * Handler for commit selection - loads elements for the selected commit
     */
    void onCommitSelected() {
        CommitItem selected = commitCombo.selectedItem as CommitItem
        if (selected && selected.id != 'none') {
            ProjectItem project = projectCombo.selectedItem as ProjectItem
            if (project) {
                loadProjectElements(project.id, selected.id)
            }
        }
    }

    void loadProjectElements(String projectId, String commitId = null) {
        currentProjectId = projectId
        elementCache.clear()
        setStatus("Loading project elements...")

        SwingWorker worker = new SwingWorker<Void, Void>() {
            List roots
            String useCommitId = commitId

            @Override
            protected Void doInBackground() {
                // If no commit specified, use the latest from currentCommits
                if (!useCommitId && currentCommits && !currentCommits.isEmpty()) {
                    useCommitId = currentCommits[-1]['@id']
                }

                if (useCommitId) {
                    currentCommitId = useCommitId
                    roots = apiGetList("/projects/${projectId}/commits/${currentCommitId}/roots")
                }
                return null
            }

            @Override
            protected void done() {
                try {
                    get()
                    if (roots) {
                        buildTree(roots)
                        String commitLabel = useCommitId?.take(8) ?: 'Unknown'
                        setStatus("Loaded project - Commit: ${commitLabel}...")
                    } else {
                        setStatus("No elements found in project")
                    }
                } catch (Exception e) {
                    setStatus("Error: ${e.message}")
                    logError("loadProjectElements", e)
                }
            }
        }
        worker.execute()
    }

    void buildTree(List roots) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Project")

        roots.each { root ->
            // Cache root element
            String rootId = root['@id']
            elementCache[rootId] = root

            // Process owned members
            List ownedMember = root['ownedMember'] ?: []
            ownedMember.each { memberRef ->
                String memberId = memberRef['@id']
                if (memberId) {
                    try {
                        Map member = getElement(memberId)
                        ElementTreeNode node = createTreeNode(member)
                        rootNode.add(node)
                    } catch (Exception e) {
                        // Skip inaccessible elements
                    }
                }
            }
        }

        treeModel.setRoot(rootNode)
        elementTree.expandRow(0)
    }

    ElementTreeNode createTreeNode(Map element) {
        ElementTreeNode node = new ElementTreeNode(element)

        // Add placeholder for expandable nodes
        List ownedMember = element['ownedMember'] ?: []
        List ownedFeature = element['ownedFeature'] ?: []

        if (!ownedMember.isEmpty() || !ownedFeature.isEmpty()) {
            node.add(new DefaultMutableTreeNode("Loading..."))
        }

        return node
    }

    Map getElement(String elementId) {
        if (elementCache.containsKey(elementId)) {
            return elementCache[elementId]
        }

        Map element = apiGet("/projects/${currentProjectId}/commits/${currentCommitId}/elements/${elementId}")
        elementCache[elementId] = element
        return element
    }

    void onNodeExpanding(TreeExpansionEvent e) {
        TreePath path = e.path
        ElementTreeNode node = path.lastPathComponent as ElementTreeNode

        if (node == null || !(node instanceof ElementTreeNode)) return

        // Check if children need to be loaded
        if (node.childCount == 1 && node.getChildAt(0).toString() == "Loading...") {
            node.removeAllChildren()
            loadNodeChildren(node)
            treeModel.nodeStructureChanged(node)
        }
    }

    void loadNodeChildren(ElementTreeNode parentNode) {
        Map element = parentNode.element

        Set<String> addedIds = []

        // Add owned members
        List ownedMember = element['ownedMember'] ?: []
        ownedMember.each { memberRef ->
            String memberId = memberRef['@id']
            if (memberId && !addedIds.contains(memberId)) {
                try {
                    Map member = getElement(memberId)
                    if (isDisplayableType(member['@type'])) {
                        parentNode.add(createTreeNode(member))
                        addedIds.add(memberId)
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        // Add owned features not in ownedMember
        List ownedFeature = element['ownedFeature'] ?: []
        ownedFeature.each { featureRef ->
            String featureId = featureRef['@id']
            if (featureId && !addedIds.contains(featureId)) {
                try {
                    Map feature = getElement(featureId)
                    if (isDisplayableType(feature['@type'])) {
                        parentNode.add(createTreeNode(feature))
                        addedIds.add(featureId)
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }
    }

    boolean isDisplayableType(String type) {
        def displayable = [
            'Package', 'Namespace',
            'PartDefinition', 'PartUsage',
            'AttributeDefinition', 'AttributeUsage',
            'ItemDefinition', 'ItemUsage',
            'PortDefinition', 'PortUsage',
            'InterfaceDefinition', 'InterfaceUsage',
            'ConnectionDefinition', 'ConnectionUsage',
            'ActionDefinition', 'ActionUsage',
            'StateDefinition', 'StateUsage',
            'RequirementDefinition', 'RequirementUsage',
            'ConstraintDefinition', 'ConstraintUsage',
            'ViewDefinition', 'ViewUsage',
            'ViewpointDefinition', 'ViewpointUsage',
            'RenderingDefinition', 'RenderingUsage',
            'Comment', 'Documentation',
            'EnumerationDefinition', 'EnumerationUsage',
            'OccurrenceDefinition', 'OccurrenceUsage',
            'AllocationDefinition', 'AllocationUsage',
            'AnalysisCaseDefinition', 'AnalysisCaseUsage',
            'CalculationDefinition', 'CalculationUsage',
            'CaseDefinition', 'CaseUsage',
            'ConcernDefinition', 'ConcernUsage',
            'FlowConnectionUsage',
            'MetadataDefinition', 'MetadataUsage'
        ]
        return type in displayable
    }

    void onElementSelected(TreeSelectionEvent e) {
        TreePath path = e.path
        if (path == null) return

        def node = path.lastPathComponent
        if (node instanceof ElementTreeNode) {
            Map element = node.element
            displayElementProperties(element)
            displaySysmlText(element)
        } else {
            propertiesArea.text = ""
            sysmlTextArea.text = ""
        }
    }

    void displayElementProperties(Map element) {
        StringBuilder sb = new StringBuilder()

        sb.append("Type: ${element['@type']}\n")
        sb.append("ID: ${element['@id']}\n")
        sb.append("Name: ${element['name'] ?: '(unnamed)'}\n")
        sb.append("Qualified Name: ${element['qualifiedName'] ?: 'N/A'}\n")
        sb.append("Short Name: ${element['shortName'] ?: 'N/A'}\n")
        sb.append("Element ID: ${element['elementId'] ?: 'N/A'}\n")

        // Documentation
        if (element['documentation']) {
            sb.append("\n--- Documentation ---\n")
            sb.append("${element['documentation']}\n")
        }

        // SysML v2 Specific Properties
        sb.append("\n--- SysML v2 Properties ---\n")
        if (element['direction']) {
            sb.append("Direction: ${element['direction']}\n")
        }
        if (element['multiplicity']) {
            def mult = element['multiplicity']
            def lower = mult['lowerBound'] ?: 0
            def upper = mult['upperBound'] ?: '*'
            sb.append("Multiplicity: [${lower}..${upper}]\n")
        }
        if (element['declaredType'] || element['type']) {
            def typeRef = element['declaredType'] ?: element['type']
            sb.append("Type: ${typeRef}\n")
        }

        sb.append("\n--- Flags ---\n")
        sb.append("Is Abstract: ${element['isAbstract'] ?: 'false'}\n")
        sb.append("Is Library Element: ${element['isLibraryElement'] ?: 'false'}\n")
        sb.append("Is Implied Included: ${element['isImpliedIncluded'] ?: 'false'}\n")
        if (element['isComposite'] != null) {
            sb.append("Is Composite: ${element['isComposite']}\n")
        }
        if (element['isReadOnly'] != null) {
            sb.append("Is Read Only: ${element['isReadOnly']}\n")
        }
        if (element['isDerived'] != null) {
            sb.append("Is Derived: ${element['isDerived']}\n")
        }
        if (element['isOrdered'] != null) {
            sb.append("Is Ordered: ${element['isOrdered']}\n")
        }
        if (element['isUnique'] != null) {
            sb.append("Is Unique: ${element['isUnique']}\n")
        }

        // Specializations
        if (element['specialization']) {
            def specs = element['specialization'] instanceof List ?
                element['specialization'] : [element['specialization']]
            if (specs.size() > 0) {
                sb.append("\n--- Specializations ---\n")
                specs.each { spec ->
                    if (spec['general']) {
                        sb.append("Specializes: ${spec['general']}\n")
                    }
                }
            }
        }

        // Redefinitions
        if (element['redefinition']) {
            def redefs = element['redefinition'] instanceof List ?
                element['redefinition'] : [element['redefinition']]
            if (redefs.size() > 0) {
                sb.append("\n--- Redefinitions ---\n")
                redefs.each { redef ->
                    if (redef['redefinedFeature']) {
                        sb.append("Redefines: ${redef['redefinedFeature']}\n")
                    }
                }
            }
        }

        // Count relationships
        List ownedMember = element['ownedMember'] ?: []
        List ownedFeature = element['ownedFeature'] ?: []
        List ownedRelationship = element['ownedRelationship'] ?: []

        sb.append("\n--- Owned Elements ---\n")
        sb.append("Owned Members: ${ownedMember.size()}\n")
        sb.append("Owned Features: ${ownedFeature.size()}\n")
        sb.append("Owned Relationships: ${ownedRelationship.size()}\n")

        // Owner info
        if (element['owner']) {
            sb.append("\n--- Owner ---\n")
            sb.append("Owner ID: ${element['owner']['@id']}\n")
        }

        propertiesArea.text = sb.toString()
        propertiesArea.caretPosition = 0
    }

    void displaySysmlText(Map element) {
        String text = generateSysmlText(element, 0)
        sysmlTextArea.text = text
        sysmlTextArea.caretPosition = 0
    }

    String generateSysmlText(Map element, int indentLevel) {
        StringBuilder sb = new StringBuilder()
        String indent = "    " * indentLevel

        String type = element['@type']
        String name = getElementName(element)
        String keyword = typeToKeyword(type)

        if (keyword.startsWith("/*")) {
            // Non-exportable type
            return ""
        }

        // Handle comments
        if (type == 'Comment') {
            String body = element['body'] ?: ''
            if (body) {
                sb.append("${indent}/* ${body} */\n")
            }
            return sb.toString()
        }

        // Build declaration
        sb.append(indent)
        sb.append(keyword)

        if (name) {
            sb.append(" ${escapeName(name)}")
        }

        // Get children
        List ownedMember = element['ownedMember'] ?: []
        List ownedFeature = element['ownedFeature'] ?: []

        boolean hasContent = !ownedMember.isEmpty() || !ownedFeature.isEmpty()

        if (hasContent) {
            sb.append(" {\n")

            Set<String> processedIds = []

            // Process owned members
            ownedMember.each { memberRef ->
                String memberId = memberRef['@id']
                if (memberId && !processedIds.contains(memberId)) {
                    try {
                        Map member = getElement(memberId)
                        if (isDisplayableType(member['@type'])) {
                            sb.append(generateSysmlText(member, indentLevel + 1))
                            processedIds.add(memberId)
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }

            // Process owned features not in ownedMember
            ownedFeature.each { featureRef ->
                String featureId = featureRef['@id']
                if (featureId && !processedIds.contains(featureId)) {
                    try {
                        Map feature = getElement(featureId)
                        if (isDisplayableType(feature['@type'])) {
                            sb.append(generateSysmlText(feature, indentLevel + 1))
                            processedIds.add(featureId)
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }

            sb.append("${indent}}\n")
        } else {
            sb.append(";\n")
        }

        return sb.toString()
    }

    String getElementName(Map element) {
        String name = element['name'] ?: element['declaredName']
        if (name == null) {
            String qn = element['qualifiedName']
            if (qn) {
                def parts = qn.split("::")
                name = parts[-1]?.replaceAll("^'|'\$", "")
            }
        }
        return name
    }

    String escapeName(String name) {
        if (name == null) return null
        if (name.contains(' ') || name.contains("'") || name.contains('(') || name.contains(')')) {
            return "'${name.replace("'", "\\'")}'"
        }
        return name
    }

    String typeToKeyword(String type) {
        switch (type) {
            case 'Package': return 'package'
            case 'PartDefinition': return 'part def'
            case 'PartUsage': return 'part'
            case 'AttributeDefinition': return 'attribute def'
            case 'AttributeUsage': return 'attribute'
            case 'ItemDefinition': return 'item def'
            case 'ItemUsage': return 'item'
            case 'PortDefinition': return 'port def'
            case 'PortUsage': return 'port'
            case 'InterfaceDefinition': return 'interface def'
            case 'InterfaceUsage': return 'interface'
            case 'ConnectionDefinition': return 'connection def'
            case 'ConnectionUsage': return 'connection'
            case 'ActionDefinition': return 'action def'
            case 'ActionUsage': return 'action'
            case 'StateDefinition': return 'state def'
            case 'StateUsage': return 'state'
            case 'RequirementDefinition': return 'requirement def'
            case 'RequirementUsage': return 'requirement'
            case 'ConstraintDefinition': return 'constraint def'
            case 'ConstraintUsage': return 'constraint'
            case 'ViewDefinition': return 'view def'
            case 'ViewUsage': return 'view'
            case 'ViewpointDefinition': return 'viewpoint def'
            case 'ViewpointUsage': return 'viewpoint'
            case 'RenderingDefinition': return 'rendering def'
            case 'RenderingUsage': return 'rendering'
            case 'Comment': return 'comment'
            case 'Documentation': return 'doc'
            case 'Namespace': return 'namespace'
            case 'EnumerationDefinition': return 'enum def'
            case 'EnumerationUsage': return 'enum'
            case 'AnalysisCaseDefinition': return 'analysis def'
            case 'AnalysisCaseUsage': return 'analysis'
            case 'CalculationDefinition': return 'calc def'
            case 'CalculationUsage': return 'calc'
            case 'CaseDefinition': return 'case def'
            case 'CaseUsage': return 'case'
            case 'AllocationDefinition': return 'allocation def'
            case 'AllocationUsage': return 'allocate'
            case 'FlowConnectionUsage': return 'flow'
            default: return "/* ${type} */"
        }
    }

    void refreshCurrentProject() {
        if (currentProjectId) {
            loadProjectElements(currentProjectId)
        }
    }

    /**
     * Load all elements recursively in a background worker
     */
    void loadAllElements() {
        if (!currentProjectId || !currentCommitId) {
            JOptionPane.showMessageDialog(this, "Please select a project first",
                "No Project", JOptionPane.WARNING_MESSAGE)
            return
        }

        int response = JOptionPane.showConfirmDialog(this,
            "This will load all elements recursively.\nThis may take some time for large projects.\nContinue?",
            "Load All Elements",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE)

        if (response != JOptionPane.YES_OPTION) {
            return
        }

        setStatus("Loading all elements...")
        logDiagnostic("Starting full recursive load")

        SwingWorker worker = new SwingWorker<Integer, String>() {
            int loadedCount = 0

            @Override
            protected Integer doInBackground() {
                def root = treeModel.root
                if (root) {
                    loadAllChildrenRecursively(root)
                }
                return loadedCount
            }

            void loadAllChildrenRecursively(TreeNode node) {
                if (node instanceof ElementTreeNode) {
                    ElementTreeNode eNode = (ElementTreeNode) node

                    // Check if children need to be loaded
                    if (eNode.childCount == 1 && eNode.getChildAt(0).toString() == "Loading...") {
                        eNode.removeAllChildren()
                        loadNodeChildren(eNode)
                        loadedCount++
                        publish("Loaded ${loadedCount} nodes...")
                    }

                    // Recursively load children
                    for (int i = 0; i < eNode.childCount; i++) {
                        loadAllChildrenRecursively(eNode.getChildAt(i))
                    }
                } else {
                    // For non-ElementTreeNode, still recurse through children
                    for (int i = 0; i < node.childCount; i++) {
                        loadAllChildrenRecursively(node.getChildAt(i))
                    }
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    setStatus(chunks.last())
                }
            }

            @Override
            protected void done() {
                try {
                    int count = get()
                    treeModel.reload()
                    setStatus("Loaded all elements: ${count} nodes expanded, ${elementCache.size()} total elements")
                    logDiagnostic("Full recursive load complete: ${count} nodes, ${elementCache.size()} elements cached")

                    // Optionally expand all after loading
                    expandAllNodes()
                } catch (Exception e) {
                    setStatus("Error loading all elements")
                    logError("loadAllElements", e)
                    showErrorDialog(SysMLv2ExplorerFrame.this, "Load All Error",
                        "Failed to load all elements.", e)
                }
            }
        }
        worker.execute()
    }

    void expandAllNodes() {
        for (int i = 0; i < elementTree.rowCount; i++) {
            elementTree.expandRow(i)
        }
    }

    void collapseAllNodes() {
        for (int i = elementTree.rowCount - 1; i >= 1; i--) {
            elementTree.collapseRow(i)
        }
    }

    void copyToClipboard() {
        String text = sysmlTextArea.text
        if (text) {
            def clipboard = Toolkit.defaultToolkit.systemClipboard
            clipboard.setContents(new java.awt.datatransfer.StringSelection(text), null)
            setStatus("Copied to clipboard")
        }
    }

    void exportToFile() {
        String text = sysmlTextArea.text
        if (!text) {
            JOptionPane.showMessageDialog(this, "No SysML text to export", "Export", JOptionPane.WARNING_MESSAGE)
            return
        }

        JFileChooser chooser = new JFileChooser(OUTPUT_DIR)
        chooser.selectedFile = new File("export_${new Date().format('yyyyMMdd_HHmmss')}.sysml")
        chooser.fileFilter = new javax.swing.filechooser.FileNameExtensionFilter("SysML Files", "sysml")

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.selectedFile
            if (!file.name.endsWith('.sysml')) {
                file = new File(file.path + '.sysml')
            }

            file.text = "// SysML v2 Export\n// Exported: ${new Date()}\n// Generated by SysMLv2Explorer\n\n${text}"
            setStatus("Exported to: ${file.name}")
        }
    }

    void setStatus(String message) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
        }
    }

    /**
     * Show model statistics dashboard
     */
    void showModelStatistics() {
        if (!currentProjectId) {
            JOptionPane.showMessageDialog(this, "Please select a project first",
                "No Project", JOptionPane.WARNING_MESSAGE)
            return
        }

        // Analyze the current tree and cache
        Map<String, Integer> typeCounts = [:]
        int totalElements = 0
        int loadedNodes = 0

        // Count from cache
        elementCache.each { id, element ->
            totalElements++
            String type = element['@type'] ?: 'Unknown'
            typeCounts[type] = (typeCounts[type] ?: 0) + 1
        }

        // Count loaded tree nodes
        loadedNodes = countTreeNodes(treeModel.root)

        // Build statistics report
        StringBuilder stats = new StringBuilder()
        stats.append("═══ MODEL STATISTICS ═══\n\n")
        stats.append("Project: ${currentProjectId?.take(8)}...\n")
        stats.append("Commit:  ${currentCommitId?.take(8)}...\n")
        stats.append("\n")
        stats.append("─── Overview ───\n")
        stats.append("Total Elements Cached: ${totalElements}\n")
        stats.append("Tree Nodes Loaded:     ${loadedNodes}\n")
        stats.append("Unique Element Types:  ${typeCounts.size()}\n")
        stats.append("\n")
        stats.append("─── Element Types ───\n")

        // Sort by count descending
        def sortedTypes = typeCounts.sort { a, b -> b.value <=> a.value }
        sortedTypes.each { type, count ->
            String paddedCount = count.toString().padLeft(5)
            stats.append("${paddedCount}  ${type}\n")
        }

        stats.append("\n")
        stats.append("─── Cache Information ───\n")
        stats.append("Cache Size:  ${elementCache.size()} elements\n")
        stats.append("Memory Est:  ~${(elementCache.size() * 2)} KB\n")

        // Create dialog
        JTextArea textArea = new JTextArea(stats.toString())
        textArea.editable = false
        textArea.font = new Font("Monospaced", Font.PLAIN, 12)
        textArea.caretPosition = 0

        JScrollPane scrollPane = new JScrollPane(textArea)
        scrollPane.preferredSize = new Dimension(500, 600)

        JOptionPane.showMessageDialog(this, scrollPane,
            "Model Statistics",
            JOptionPane.INFORMATION_MESSAGE)
    }

    int countTreeNodes(TreeNode node) {
        if (!node) return 0
        int count = 1
        for (int i = 0; i < node.childCount; i++) {
            count += countTreeNodes(node.getChildAt(i))
        }
        return count
    }

    /**
     * Show traceability matrix - relationships between model elements
     */
    void showTraceabilityMatrix() {
        if (!currentProjectId) {
            JOptionPane.showMessageDialog(this, "Please select a project first",
                "No Project", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (elementCache.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please load a project first.\nUse 'Load All' button to load all elements for complete traceability.",
                "No Elements Loaded", JOptionPane.WARNING_MESSAGE)
            return
        }

        setStatus("Analyzing traceability relationships...")
        logDiagnostic("Building traceability matrix")

        // Analyze relationships in the cache
        Map<String, List<String>> relationships = [:]
        Map<String, String> elementNames = [:]
        Map<String, String> elementTypes = [:]

        elementCache.each { id, element ->
            String name = element['name'] ?: element['declaredName'] ?: id.take(8)
            String type = element['@type'] ?: 'Unknown'
            elementNames[id] = name
            elementTypes[id] = type

            // Look for various relationship types
            ['ownedMember', 'ownedFeature', 'client', 'supplier', 'source', 'target'].each { relType ->
                def related = element[relType]
                if (related) {
                    if (related instanceof List) {
                        related.each { ref ->
                            String refId = ref['@id']
                            if (refId) {
                                String key = "${id}:${relType}"
                                if (!relationships[key]) relationships[key] = []
                                relationships[key] << refId
                            }
                        }
                    } else if (related instanceof Map) {
                        String refId = related['@id']
                        if (refId) {
                            String key = "${id}:${relType}"
                            if (!relationships[key]) relationships[key] = []
                            relationships[key] << refId
                        }
                    }
                }
            }
        }

        // Build matrix display
        StringBuilder matrix = new StringBuilder()
        matrix.append("═══ TRACEABILITY MATRIX ═══\n\n")
        matrix.append("Project: ${currentProjectId?.take(8)}...\n")
        matrix.append("Commit:  ${currentCommitId?.take(8)}...\n")
        matrix.append("Elements Analyzed: ${elementCache.size()}\n")
        matrix.append("Relationships Found: ${relationships.size()}\n")
        matrix.append("\n")

        if (relationships.isEmpty()) {
            matrix.append("No relationships found in loaded elements.\n")
            matrix.append("Try using 'Load All' to load complete model structure.\n")
        } else {
            matrix.append("─── Relationship Summary ───\n")

            // Count relationship types
            Map<String, Integer> relTypeCounts = [:]
            relationships.each { key, targets ->
                String relType = key.split(':')[1]
                relTypeCounts[relType] = (relTypeCounts[relType] ?: 0) + targets.size()
            }

            relTypeCounts.sort { a, b -> b.value <=> a.value }.each { relType, count ->
                matrix.append(String.format("  %-15s: %4d\n", relType, count))
            }

            matrix.append("\n─── Detailed Relationships ───\n")
            matrix.append("(Showing first 50 relationships)\n\n")

            int shown = 0
            relationships.take(50).each { key, targets ->
                String[] parts = key.split(':')
                String sourceId = parts[0]
                String relType = parts[1]

                String sourceName = elementNames[sourceId] ?: sourceId.take(8)
                String sourceType = elementTypes[sourceId] ?: 'Unknown'

                targets.each { targetId ->
                    String targetName = elementNames[targetId] ?: targetId.take(8)
                    String targetType = elementTypes[targetId] ?: 'Unknown'

                    matrix.append(String.format("%-25s ─[%-12s]→ %-25s\n",
                        "${sourceName} (${sourceType})".take(25),
                        relType,
                        "${targetName} (${targetType})".take(25)))
                    shown++
                }
            }

            if (relationships.size() > 50) {
                matrix.append("\n... and ${relationships.size() - 50} more relationships\n")
            }
        }

        // Create dialog with table
        JTextArea textArea = new JTextArea(matrix.toString())
        textArea.editable = false
        textArea.font = new Font("Monospaced", Font.PLAIN, 11)
        textArea.caretPosition = 0

        JScrollPane scrollPane = new JScrollPane(textArea)
        scrollPane.preferredSize = new Dimension(700, 600)

        JOptionPane.showMessageDialog(this, scrollPane,
            "Traceability Matrix",
            JOptionPane.INFORMATION_MESSAGE)

        setStatus("Traceability analysis complete")
        logDiagnostic("Traceability matrix shown: ${relationships.size()} relationships")
    }

    void showCommitDiffViewer() {
        if (!currentProjectId) {
            JOptionPane.showMessageDialog(this, "Please select a project first",
                "No Project", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (currentCommits.size() < 2) {
            JOptionPane.showMessageDialog(this,
                "Project must have at least 2 commits to compare.\nThis project has ${currentCommits.size()} commit(s).",
                "Insufficient Commits", JOptionPane.WARNING_MESSAGE)
            return
        }

        // Create dialog for selecting two commits
        JDialog dialog = new JDialog(this, "Select Commits to Compare", true)
        dialog.layout = new BorderLayout(10, 10)

        JPanel panel = new JPanel(new GridBagLayout())
        GridBagConstraints gbc = new GridBagConstraints()
        gbc.insets = new Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        // First commit selector
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(new JLabel("Base Commit:"), gbc)

        JComboBox<CommitItem> commit1Combo = new JComboBox<>()
        currentCommits.each { commit1Combo.addItem(new CommitItem(commit['@id'], commit['timestamp'] ?: 'No timestamp', false)) }
        commit1Combo.selectedIndex = Math.max(0, currentCommits.size() - 2) // Second most recent
        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(commit1Combo, gbc)

        // Second commit selector
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0
        panel.add(new JLabel("Compare Commit:"), gbc)

        JComboBox<CommitItem> commit2Combo = new JComboBox<>()
        currentCommits.each { commit2Combo.addItem(new CommitItem(commit['@id'], commit['timestamp'] ?: 'No timestamp', false)) }
        commit2Combo.selectedIndex = 0 // Most recent
        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(commit2Combo, gbc)

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        JButton compareBtn = new JButton("Compare")
        JButton cancelBtn = new JButton("Cancel")

        boolean[] shouldCompare = [false]

        compareBtn.addActionListener {
            shouldCompare[0] = true
            dialog.dispose()
        }

        cancelBtn.addActionListener {
            dialog.dispose()
        }

        buttonPanel.add(compareBtn)
        buttonPanel.add(cancelBtn)

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.visible = true

        if (!shouldCompare[0]) {
            return
        }

        // Get selected commits
        CommitItem commit1 = commit1Combo.selectedItem
        CommitItem commit2 = commit2Combo.selectedItem

        if (commit1.id == commit2.id) {
            JOptionPane.showMessageDialog(this, "Please select two different commits",
                "Same Commit Selected", JOptionPane.WARNING_MESSAGE)
            return
        }

        setStatus("Loading commits for comparison...")
        logDiagnostic("Comparing commits: ${commit1.id} vs ${commit2.id}")

        // Load root elements for both commits
        SwingWorker worker = new SwingWorker<Map, Void>() {
            @Override
            protected Map doInBackground() {
                Map commit1Data = [:]
                Map commit2Data = [:]

                try {
                    // Load roots for commit 1
                    def roots1 = apiGetList("/projects/${currentProjectId}/commits/${commit1.id}/roots")
                    roots1.each { root ->
                        def element = apiGet("/projects/${currentProjectId}/commits/${commit1.id}/elements/${root['@id']}")
                        commit1Data[root['@id']] = element
                    }

                    // Load roots for commit 2
                    def roots2 = apiGetList("/projects/${currentProjectId}/commits/${commit2.id}/roots")
                    roots2.each { root ->
                        def element = apiGet("/projects/${currentProjectId}/commits/${commit2.id}/elements/${root['@id']}")
                        commit2Data[root['@id']] = element
                    }
                } catch (Exception e) {
                    logError("showCommitDiffViewer", e)
                }

                return [commit1: commit1Data, commit2: commit2Data]
            }

            @Override
            protected void done() {
                try {
                    Map data = get()
                    displayCommitDiff(commit1, commit2, data['commit1'], data['commit2'])
                } catch (Exception e) {
                    setStatus("Error comparing commits")
                    logError("showCommitDiffViewer.done", e)
                    showErrorDialog(SysMLv2ExplorerFrame.this, "Commit Comparison Error",
                        "Failed to compare commits.", e)
                }
            }
        }
        worker.execute()
    }

    void displayCommitDiff(CommitItem commit1, CommitItem commit2, Map commit1Data, Map commit2Data) {
        // Analyze differences
        Set allIds = new HashSet()
        allIds.addAll(commit1Data.keySet())
        allIds.addAll(commit2Data.keySet())

        List<String> added = []
        List<String> removed = []
        List<String> modified = []
        List<String> unchanged = []

        allIds.each { id ->
            boolean inCommit1 = commit1Data.containsKey(id)
            boolean inCommit2 = commit2Data.containsKey(id)

            if (!inCommit1 && inCommit2) {
                // Added in commit2
                def element = commit2Data[id]
                String name = element['name'] ?: element['declaredName'] ?: id.take(8)
                String type = element['@type'] ?: 'Unknown'
                added << "${name} (${type})"
            } else if (inCommit1 && !inCommit2) {
                // Removed in commit2
                def element = commit1Data[id]
                String name = element['name'] ?: element['declaredName'] ?: id.take(8)
                String type = element['@type'] ?: 'Unknown'
                removed << "${name} (${type})"
            } else {
                // Exists in both - check if modified
                def elem1 = commit1Data[id]
                def elem2 = commit2Data[id]

                String name = elem2['name'] ?: elem2['declaredName'] ?: id.take(8)
                String type = elem2['@type'] ?: 'Unknown'

                // Simple comparison - could be enhanced
                if (elem1.toString() != elem2.toString()) {
                    modified << "${name} (${type})"
                } else {
                    unchanged << "${name} (${type})"
                }
            }
        }

        // Build diff report
        StringBuilder diff = new StringBuilder()
        diff.append("═══ COMMIT DIFF VIEWER ═══\n\n")
        diff.append("Base Commit:    ${commit1.timestamp}\n")
        diff.append("                ${commit1.id}\n")
        diff.append("\n")
        diff.append("Compare Commit: ${commit2.timestamp}\n")
        diff.append("                ${commit2.id}\n")
        diff.append("\n")
        diff.append("─── Summary ───\n")
        diff.append("Added:     ${added.size()} elements\n")
        diff.append("Removed:   ${removed.size()} elements\n")
        diff.append("Modified:  ${modified.size()} elements\n")
        diff.append("Unchanged: ${unchanged.size()} elements\n")
        diff.append("Total:     ${allIds.size()} elements\n")
        diff.append("\n")

        if (added.size() > 0) {
            diff.append("─── Added Elements ───\n")
            added.sort().each { diff.append("+ ${it}\n") }
            diff.append("\n")
        }

        if (removed.size() > 0) {
            diff.append("─── Removed Elements ───\n")
            removed.sort().each { diff.append("- ${it}\n") }
            diff.append("\n")
        }

        if (modified.size() > 0) {
            diff.append("─── Modified Elements ───\n")
            int showCount = Math.min(50, modified.size())
            modified.sort().take(showCount).each { diff.append("~ ${it}\n") }
            if (modified.size() > showCount) {
                diff.append("\n... and ${modified.size() - showCount} more modified elements\n")
            }
            diff.append("\n")
        }

        // Create display dialog
        JTextArea textArea = new JTextArea(diff.toString())
        textArea.editable = false
        textArea.font = new Font("Monospaced", Font.PLAIN, 12)
        textArea.caretPosition = 0

        JScrollPane scrollPane = new JScrollPane(textArea)
        scrollPane.preferredSize = new Dimension(700, 600)

        JOptionPane.showMessageDialog(this, scrollPane,
            "Commit Diff: ${commit1.timestamp} → ${commit2.timestamp}",
            JOptionPane.INFORMATION_MESSAGE)

        setStatus("Commit comparison complete")
        logDiagnostic("Commit diff shown: ${added.size()} added, ${removed.size()} removed, ${modified.size()} modified")
    }

    void exportToHTML() {
        if (!currentProjectId) {
            JOptionPane.showMessageDialog(this, "Please select a project first",
                "No Project", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (elementCache.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please load a project first.\nUse 'Load All' button to load all elements for complete export.",
                "No Elements Loaded", JOptionPane.WARNING_MESSAGE)
            return
        }

        // Choose file location
        JFileChooser fileChooser = new JFileChooser()
        fileChooser.dialogTitle = "Export Project to HTML"
        fileChooser.selectedFile = new File("sysml_export_${currentProjectId.take(8)}.html")

        int result = fileChooser.showSaveDialog(this)
        if (result != JFileChooser.APPROVE_OPTION) {
            return
        }

        File outputFile = fileChooser.selectedFile
        setStatus("Exporting to HTML...")
        logDiagnostic("Exporting ${elementCache.size()} elements to ${outputFile.absolutePath}")

        SwingWorker worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    generateHTMLExport(outputFile)
                    return true
                } catch (Exception e) {
                    logError("exportToHTML", e)
                    return false
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get()
                    if (success) {
                        setStatus("HTML export complete: ${outputFile.name}")
                        int response = JOptionPane.showConfirmDialog(SysMLv2ExplorerFrame.this,
                            "Export completed successfully!\n\nOpen HTML file in browser?",
                            "Export Complete",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE)

                        if (response == JOptionPane.YES_OPTION) {
                            java.awt.Desktop.desktop.browse(outputFile.toURI())
                        }
                    } else {
                        setStatus("HTML export failed")
                        JOptionPane.showMessageDialog(SysMLv2ExplorerFrame.this,
                            "Failed to export HTML. Check diagnostic log for details.",
                            "Export Failed",
                            JOptionPane.ERROR_MESSAGE)
                    }
                } catch (Exception e) {
                    setStatus("Error in HTML export")
                    logError("exportToHTML.done", e)
                }
            }
        }
        worker.execute()
    }

    void generateHTMLExport(File outputFile) {
        // Build HTML with navigation
        StringBuilder html = new StringBuilder()

        html.append("""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SysML v2 Project Export</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; font-size: 14px; line-height: 1.6; }
        .container { display: flex; height: 100vh; }
        .sidebar { width: 300px; background: #2c3e50; color: #ecf0f1; overflow-y: auto; }
        .content { flex: 1; padding: 20px; overflow-y: auto; background: #f5f5f5; }
        .header { background: #34495e; padding: 15px; border-bottom: 2px solid #1abc9c; }
        .header h1 { font-size: 18px; color: #1abc9c; }
        .header p { font-size: 12px; color: #95a5a6; margin-top: 5px; }
        .nav-tree { padding: 10px; }
        .nav-item { padding: 8px 12px; cursor: pointer; border-left: 3px solid transparent; transition: all 0.2s; }
        .nav-item:hover { background: #34495e; border-left-color: #1abc9c; }
        .nav-item.active { background: #34495e; border-left-color: #e74c3c; }
        .nav-item-label { display: block; color: #ecf0f1; text-decoration: none; }
        .nav-item-type { font-size: 11px; color: #95a5a6; margin-left: 5px; }
        .element-card { background: white; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .element-title { font-size: 24px; color: #2c3e50; margin-bottom: 5px; }
        .element-type { display: inline-block; background: #3498db; color: white; padding: 4px 12px; border-radius: 4px; font-size: 12px; margin-bottom: 15px; }
        .element-id { font-size: 12px; color: #7f8c8d; font-family: monospace; margin-bottom: 15px; }
        .property-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        .property-table th { background: #ecf0f1; padding: 10px; text-align: left; border-bottom: 2px solid #bdc3c7; }
        .property-table td { padding: 10px; border-bottom: 1px solid #ecf0f1; }
        .property-table tr:hover { background: #f8f9fa; }
        .property-key { font-weight: 600; color: #2c3e50; width: 30%; }
        .property-value { color: #34495e; font-family: 'Courier New', monospace; font-size: 13px; }
        .search-box { padding: 10px; background: #34495e; }
        .search-box input { width: 100%; padding: 8px; border: none; border-radius: 4px; background: #ecf0f1; }
        .stats { background: #e8f4f8; border-left: 4px solid #3498db; padding: 15px; border-radius: 4px; margin-bottom: 20px; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
        .stat-item { text-align: center; }
        .stat-value { font-size: 24px; font-weight: bold; color: #3498db; }
        .stat-label { font-size: 12px; color: #7f8c8d; text-transform: uppercase; }
    </style>
</head>
<body>
    <div class="container">
        <div class="sidebar">
            <div class="header">
                <h1>SysML v2 Export</h1>
                <p>Project: ${currentProjectId.take(8)}...</p>
                <p>Commit: ${currentCommitId.take(8)}...</p>
            </div>
            <div class="search-box">
                <input type="text" id="searchBox" placeholder="Search elements..." onkeyup="filterElements()">
            </div>
            <div class="nav-tree" id="navTree">
""")

        // Add navigation items
        elementCache.each { id, element ->
            String name = element['name'] ?: element['declaredName'] ?: id.take(8)
            String type = element['@type'] ?: 'Unknown'
            String shortType = type.tokenize('.')[-1] // Get last part after dot

            html.append("""                <div class="nav-item" onclick="showElement('${id}')">
                    <span class="nav-item-label">${escapeHtml(name)}</span>
                    <span class="nav-item-type">${escapeHtml(shortType)}</span>
                </div>
""")
        }

        html.append("""            </div>
        </div>
        <div class="content" id="content">
            <div class="stats">
                <h2 style="margin-bottom: 15px; color: #2c3e50;">Project Statistics</h2>
                <div class="stats-grid">
""")

        // Calculate statistics
        Map<String, Integer> typeCounts = [:]
        elementCache.each { id, element ->
            String type = element['@type'] ?: 'Unknown'
            typeCounts[type] = (typeCounts[type] ?: 0) + 1
        }

        html.append("""                    <div class="stat-item">
                        <div class="stat-value">${elementCache.size()}</div>
                        <div class="stat-label">Total Elements</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value">${typeCounts.size()}</div>
                        <div class="stat-label">Element Types</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value">${new java.text.SimpleDateFormat('yyyy-MM-dd').format(new Date())}</div>
                        <div class="stat-label">Export Date</div>
                    </div>
""")

        html.append("""                </div>
            </div>
            <p style="color: #7f8c8d; text-align: center; margin-top: 50px;">Select an element from the sidebar to view details</p>
        </div>
    </div>

    <script>
        const elements = {
""")

        // Add element data as JavaScript
        elementCache.eachWithIndex { entry, index ->
            String id = entry.key
            Map element = entry.value

            String name = element['name'] ?: element['declaredName'] ?: id.take(8)
            String type = element['@type'] ?: 'Unknown'

            html.append("            '${id}': ${groovy.json.JsonOutput.toJson(element)}")
            if (index < elementCache.size() - 1) {
                html.append(",\n")
            } else {
                html.append("\n")
            }
        }

        html.append("""        };

        function showElement(id) {
            const element = elements[id];
            if (!element) return;

            const name = element.name || element.declaredName || id.substring(0, 8);
            const type = element['@type'] || 'Unknown';
            const shortType = type.split('.').pop();

            let html = '<div class="element-card">';
            html += '<h1 class="element-title">' + escapeHtml(name) + '</h1>';
            html += '<span class="element-type">' + escapeHtml(shortType) + '</span>';
            html += '<div class="element-id">ID: ' + escapeHtml(id) + '</div>';
            html += '<table class="property-table"><thead><tr><th>Property</th><th>Value</th></tr></thead><tbody>';

            for (const [key, value] of Object.entries(element)) {
                if (key === '@type' || key === '@id') continue;
                const valueStr = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
                html += '<tr><td class="property-key">' + escapeHtml(key) + '</td>';
                html += '<td class="property-value">' + escapeHtml(valueStr) + '</td></tr>';
            }

            html += '</tbody></table></div>';
            document.getElementById('content').innerHTML = html;

            // Update active nav item
            document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
            event.currentTarget.classList.add('active');
        }

        function filterElements() {
            const searchText = document.getElementById('searchBox').value.toLowerCase();
            const navItems = document.querySelectorAll('.nav-item');

            navItems.forEach(item => {
                const text = item.textContent.toLowerCase();
                item.style.display = text.includes(searchText) ? 'block' : 'none';
            });
        }

        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
""")

        // Write to file
        outputFile.text = html.toString()
        logDiagnostic("HTML export complete: ${outputFile.absolutePath}, ${elementCache.size()} elements")
    }

    String escapeHtml(String text) {
        if (!text) return ''
        return text.replace('&', '&amp;')
                   .replace('<', '&lt;')
                   .replace('>', '&gt;')
                   .replace('"', '&quot;')
                   .replace("'", '&#39;')
    }

    void showAboutDialog() {
        String message = """SysML v2 Explorer
Version 1.0

A GUI tool for exploring SysML v2 projects
via the Dassault SysMLv2 API.

Features:
- Tree view of project elements
- Properties panel
- SysML v2 textual notation preview
- Export to .sysml files
"""
        JOptionPane.showMessageDialog(this, message, "About", JOptionPane.INFORMATION_MESSAGE)
    }

    // Inner classes
    static class ProjectItem {
        String id
        String name
        boolean accessible = true  // Assume accessible until proven otherwise

        ProjectItem(String id, String name, boolean accessible = true) {
            this.id = id
            this.name = name
            this.accessible = accessible
        }

        @Override
        String toString() {
            return "${name} (${id.take(8)}...)"
        }
    }

    static class CommitItem {
        String id
        String timestamp
        boolean isLatest = false

        CommitItem(String id, String timestamp, boolean isLatest = false) {
            this.id = id
            this.timestamp = timestamp
            this.isLatest = isLatest
        }

        @Override
        String toString() {
            String label = "${timestamp} (${id.take(8)}...)"
            return isLatest ? "${label} [LATEST]" : label
        }
    }

    /**
     * Custom renderer for project combo box that shows red indicator for inaccessible projects
     */
    static class ProjectComboRenderer extends DefaultListCellRenderer {
        @Override
        Component getListCellRendererComponent(JList list, Object value, int index,
                                                boolean isSelected, boolean cellHasFocus) {
            Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (value instanceof ProjectItem) {
                ProjectItem item = (ProjectItem) value
                if (!item.accessible) {
                    // Red text for inaccessible projects
                    comp.foreground = Color.RED
                    if (comp instanceof JLabel) {
                        ((JLabel) comp).text = "\u26D4 ${item.toString()}"  // Add red circle icon
                    }
                }
            }

            return comp
        }
    }

    static class ElementTreeNode extends DefaultMutableTreeNode {
        Map element

        ElementTreeNode(Map element) {
            super(getDisplayName(element))
            this.element = element
        }

        static String getDisplayName(Map element) {
            String type = element['@type'] ?: 'Unknown'
            String name = element['name'] ?: element['declaredName'] ?: '(unnamed)'
            return "[${type}] ${name}"
        }
    }

    static class ElementTreeCellRenderer extends DefaultTreeCellRenderer {
        // Icons for different element types
        static Map<String, Color> typeColors = [
            'Package': new Color(0x4A90D9),
            'PartDefinition': new Color(0x7B68EE),
            'PartUsage': new Color(0x9370DB),
            'PortUsage': new Color(0x20B2AA),
            'InterfaceDefinition': new Color(0x3CB371),
            'ConnectionUsage': new Color(0xFFA500),
            'RequirementDefinition': new Color(0xDC143C),
            'RequirementUsage': new Color(0xFF6347),
            'ViewUsage': new Color(0x708090),
            'ActionDefinition': new Color(0x4682B4),
            'ActionUsage': new Color(0x5F9EA0),
            'StateDefinition': new Color(0xDAA520),
            'StateUsage': new Color(0xF0E68C),
            'AttributeUsage': new Color(0x808080)
        ]

        @Override
        Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

            if (value instanceof ElementTreeNode) {
                Map element = value.element
                String type = element['@type']

                Color color = typeColors[type] ?: Color.BLACK
                if (!sel) {
                    foreground = color
                }

                // Set icon based on type
                if (type?.endsWith('Definition')) {
                    setIcon(UIManager.getIcon("Tree.closedIcon"))
                } else if (type?.endsWith('Usage')) {
                    setIcon(UIManager.getIcon("Tree.leafIcon"))
                }
            }

            return this
        }
    }
}

// Helper to load credentials from various sources
def loadCredentialsFromSources() {
    String username = null
    String password = null

    // 1. Try command-line arguments
    if (args.length >= 2) {
        username = args[0]
        password = args[1]
    }

    // 2. Try environment variables
    if (!username || !password) {
        String envUser = System.getenv("SYSMLV2_USERNAME")
        String envPass = System.getenv("SYSMLV2_PASSWORD")
        if (envUser && envPass) {
            username = envUser
            password = envPass
        }
    }

    // 3. Try credentials.properties file
    if (!username || !password) {
        def propsFile = new File("credentials.properties")
        if (propsFile.exists()) {
            Properties props = new Properties()
            propsFile.withInputStream { props.load(it) }
            if (!username) username = props.getProperty("SYSMLV2_USERNAME")
            if (!password) password = props.getProperty("SYSMLV2_PASSWORD")
        }
    }

    return [username: username, password: password]
}

// Main execution
SwingUtilities.invokeLater {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (Exception e) {
        // Use default look and feel
    }

    def creds = loadCredentialsFromSources()

    def explorer = new SysMLv2ExplorerFrame(creds.username, creds.password)
    explorer.visible = true
}
