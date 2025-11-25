#!/usr/bin/env groovy
/**
 * LaunchGUIWithProject.groovy
 *
 * Launches the SysMLv2Explorer GUI and automatically selects a specified project.
 * Usage: groovy LaunchGUIWithProject.groovy [projectId]
 */

@Grab('org.apache.httpcomponents:httpclient:4.5.14')
@Grab('org.apache.httpcomponents:httpcore:4.4.16')
@Grab('com.google.code.gson:gson:2.10.1')

import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import org.apache.http.auth.*
import org.apache.http.conn.ssl.*
import org.apache.http.ssl.*
import javax.net.ssl.*
import java.security.cert.X509Certificate
import com.google.gson.*

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
import java.util.List

// Load the main SysMLv2ExplorerFrame class
evaluate(new File("SysMLv2Explorer.groovy"))

// Get target project ID from command line or use default (Standard Libraries1 - largest)
String targetProjectId = args.length > 0 ? args[0] : "801d74ac-fd00-4922-91c4-402402ad1976"

println "Launching GUI with target project: ${targetProjectId}"

// Launch GUI
SwingUtilities.invokeLater {
    def creds = CredentialsHelper.getCredentials(args.length > 1 ? args[1..-1] as String[] : null)
    def frame = new SysMLv2ExplorerFrame(creds.username, creds.password)

    // Wait for projects to load, then select the target
    Timer timer = new Timer(2000, { event ->
        try {
            // Find and select the target project in the combo box
            JComboBox projectCombo = findComponentByName(frame, JComboBox.class, "projectCombo")
            if (projectCombo && projectCombo.itemCount > 0) {
                for (int i = 0; i < projectCombo.itemCount; i++) {
                    def item = projectCombo.getItemAt(i)
                    if (item instanceof Map && item['@id'] == targetProjectId) {
                        projectCombo.setSelectedIndex(i)
                        println "Selected project: ${item['name']}"
                        ((Timer)event.source).stop()
                        return
                    }
                }
                println "Project ${targetProjectId} not found in combo box"
                ((Timer)event.source).stop()
            }
        } catch (Exception e) {
            println "Error selecting project: ${e.message}"
            ((Timer)event.source).stop()
        }
    } as ActionListener)
    timer.setRepeats(true)
    timer.start()
}

// Helper to find component by name
def findComponentByName(Container container, Class<?> clazz, String name) {
    for (Component comp : container.components) {
        if (clazz.isInstance(comp)) {
            return comp
        }
        if (comp instanceof Container) {
            def found = findComponentByName(comp as Container, clazz, name)
            if (found) return found
        }
    }
    return null
}
