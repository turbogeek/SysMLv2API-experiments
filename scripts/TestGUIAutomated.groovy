#!/usr/bin/env groovy
/**
 * TestGUIAutomated.groovy
 *
 * Launches the GUI and automatically validates it by monitoring the diagnostic log
 */

println "=== Automated GUI Test ==="
println "Launching SysMLv2Explorer..."

def logFile = new File("../diagnostics/explorer_diagnostic.log")
if (logFile.exists()) {
    logFile.text = "" // Clear previous log
}

// Launch GUI in background
def proc = "groovy SysMLv2Explorer.groovy".execute(null, new File("."))

// Monitor diagnostic log
println "Waiting for GUI to initialize..."
Thread.sleep(3000)

def tests = [
    [name: "GUI Initialization", pattern: "Initializing SysMLv2ExplorerFrame", timeout: 5000],
    [name: "UI Creation", pattern: "UI initialized successfully", timeout: 3000],
    [name: "Credentials Loaded", pattern: "Credentials provided", timeout: 2000],
    [name: "API Connection", pattern: "API Response: status=200", timeout: 10000],
    [name: "Projects Loaded", pattern: "Projects loaded successfully", timeout: 5000]
]

def results = []
tests.each { test ->
    print "Testing ${test.name}... "
    def found = false
    def startTime = System.currentTimeMillis()

    while (!found && (System.currentTimeMillis() - startTime < test.timeout)) {
        if (logFile.exists()) {
            def content = logFile.text
            if (content.contains(test.pattern)) {
                found = true
                println "PASS"
                results << [test: test.name, status: "PASS"]
            }
        }
        if (!found) Thread.sleep(500)
    }

    if (!found) {
        println "FAIL (timeout)"
        results << [test: test.name, status: "FAIL - timeout"]
    }
}

println ""
println "=== Test Results ==="
results.each { r ->
    println "  ${r.status.padRight(15)} - ${r.test}"
}

def passCount = results.count { it.status == "PASS" }
def failCount = results.size() - passCount

println ""
println "Total: ${results.size()} tests, ${passCount} passed, ${failCount} failed"

if (failCount == 0) {
    println "\nGUI IS WORKING CORRECTLY!"
    println "\nThe GUI window should be visible on your screen."
    println "Use the dropdown to select a project like 'Standard Libraries1'"
} else {
    println "\nSome tests failed. Check ../diagnostics/explorer_diagnostic.log for details"
}

println ""
println "Note: The GUI is still running. Close the window when done."
println "Diagnostic log: ../diagnostics/explorer_diagnostic.log"
