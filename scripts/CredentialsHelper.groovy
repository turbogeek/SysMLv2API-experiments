/**
 * CredentialsHelper.groovy
 *
 * Provides secure credential loading for SysMLv2 API scripts.
 * Supports multiple sources (in order of precedence):
 *   1. Command-line arguments (hidden in process list on some systems)
 *   2. Environment variables: SYSMLV2_USERNAME, SYSMLV2_PASSWORD
 *   3. credentials.properties file (gitignored)
 *   4. Interactive console prompt (password masked)
 *
 * Usage in other scripts:
 *   def creds = CredentialsHelper.getCredentials(args)
 *   def username = creds.username
 *   def password = creds.password
 */

class CredentialsHelper {

    static final String CREDENTIALS_FILE = "credentials.properties"
    static final String ENV_USERNAME = "SYSMLV2_USERNAME"
    static final String ENV_PASSWORD = "SYSMLV2_PASSWORD"
    static final String ENV_BASE_URL = "SYSMLV2_BASE_URL"
    static final String DEFAULT_BASE_URL = "https://ag-mxg-twc2026x.dsone.3ds.com:8443/sysmlv2-api/api"

    /**
     * Get credentials from various sources
     * @param args Command line arguments [username, password] (optional)
     * @return Map with username, password, baseUrl
     */
    static Map getCredentials(String[] args = null) {
        String username = null
        String password = null
        String baseUrl = DEFAULT_BASE_URL

        // 1. Try command-line arguments
        if (args?.length >= 2) {
            username = args[0]
            password = args[1]
            println "Using credentials from command-line arguments"
        }

        // 2. Try environment variables
        if (!username || !password) {
            String envUser = System.getenv(ENV_USERNAME)
            String envPass = System.getenv(ENV_PASSWORD)

            if (envUser && envPass) {
                username = envUser
                password = envPass
                println "Using credentials from environment variables"
            }
        }

        // Check for base URL in environment
        String envUrl = System.getenv(ENV_BASE_URL)
        if (envUrl) {
            baseUrl = envUrl
        }

        // 3. Try credentials.properties file
        if (!username || !password) {
            def propsFile = new File(CREDENTIALS_FILE)
            if (!propsFile.exists()) {
                // Try in script directory
                def scriptDir = new File(CredentialsHelper.class.protectionDomain.codeSource.location.path).parentFile
                propsFile = new File(scriptDir, CREDENTIALS_FILE)
            }

            if (propsFile.exists()) {
                Properties props = new Properties()
                propsFile.withInputStream { props.load(it) }

                if (!username) username = props.getProperty(ENV_USERNAME)
                if (!password) password = props.getProperty(ENV_PASSWORD)

                String propsUrl = props.getProperty(ENV_BASE_URL)
                if (propsUrl) baseUrl = propsUrl

                if (username && password) {
                    println "Using credentials from ${propsFile.name}"
                }
            }
        }

        // 4. Interactive prompt
        if (!username || !password) {
            println "No credentials found. Please enter:"

            def console = System.console()
            if (console) {
                if (!username) {
                    username = console.readLine("Username: ")
                }
                if (!password) {
                    char[] passwordChars = console.readPassword("Password: ")
                    password = new String(passwordChars)
                }
            } else {
                // Fallback for environments without console (e.g., IDE)
                def scanner = new Scanner(System.in)
                if (!username) {
                    print "Username: "
                    username = scanner.nextLine()
                }
                if (!password) {
                    print "Password (will be visible): "
                    password = scanner.nextLine()
                }
            }
        }

        if (!username || !password) {
            throw new RuntimeException("Could not obtain credentials")
        }

        return [
            username: username,
            password: password,
            baseUrl: baseUrl
        ]
    }

    /**
     * Mask a password for display (show only first and last char)
     */
    static String maskPassword(String password) {
        if (password == null || password.length() <= 2) {
            return "***"
        }
        return password[0] + "*" * (password.length() - 2) + password[-1]
    }

    /**
     * Create a masked curl command for display/logging
     */
    static String maskCurlCommand(String command) {
        // Replace -u "user:password" with masked version
        return command.replaceAll(/-u\s+"([^:]+):([^"]+)"/) { match, user, pass ->
            "-u \"${user}:${maskPassword(pass)}\""
        }.replaceAll(/-u\s+'([^:]+):([^']+)'/) { match, user, pass ->
            "-u '${user}:${maskPassword(pass)}'"
        }.replaceAll(/-u\s+([^:]+):(\S+)/) { match, user, pass ->
            "-u ${user}:${maskPassword(pass)}"
        }
    }

    /**
     * Print setup instructions
     */
    static void printSetupInstructions() {
        println """
================================================================================
CREDENTIAL SETUP OPTIONS
================================================================================

1. ENVIRONMENT VARIABLES (Recommended for demos)
   Set these before running scripts:

   Windows (cmd):
     set SYSMLV2_USERNAME=your_username
     set SYSMLV2_PASSWORD=your_password

   Windows (PowerShell):
     \$env:SYSMLV2_USERNAME="your_username"
     \$env:SYSMLV2_PASSWORD="your_password"

   Linux/Mac:
     export SYSMLV2_USERNAME=your_username
     export SYSMLV2_PASSWORD=your_password

2. CREDENTIALS FILE
   Create 'credentials.properties' in the scripts directory:

     SYSMLV2_USERNAME=your_username
     SYSMLV2_PASSWORD=your_password

   (This file is gitignored and won't be committed)

3. COMMAND LINE (Not recommended for demos)
   groovy Script.groovy username password

================================================================================
"""
    }
}

// If run directly, show setup instructions
if (args.length == 0 || args[0] == '--help') {
    CredentialsHelper.printSetupInstructions()
} else if (args[0] == '--test') {
    println "Testing credential loading..."
    try {
        def creds = CredentialsHelper.getCredentials(args.length > 1 ? args[1..-1] as String[] : null)
        println "Username: ${creds.username}"
        println "Password: ${CredentialsHelper.maskPassword(creds.password)}"
        println "Base URL: ${creds.baseUrl}"
        println "\nCredentials loaded successfully!"
    } catch (Exception e) {
        println "Error: ${e.message}"
        System.exit(1)
    }
}
