package org.example.scip.runner;

import org.example.scip.ScipException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java-specific SCIP runner using scip-java CLI.
 * 
 * <p>Supports Maven and Gradle projects. Features:</p>
 * <ul>
 *   <li>Automatic CRLF line ending fix for mvnw scripts</li>
 *   <li>Automatic Java version detection from pom.xml/build.gradle</li>
 *   <li>Sets JAVA_HOME based on project's required Java version</li>
 * </ul>
 */
public class ScipJavaRunner extends AbstractScipRunner {

    // Java version paths in Docker (Dockerfile.multijava)
    private static final Map<Integer, String> JAVA_HOME_PATHS = Map.of(
        8, "/opt/java/jdk8",
        11, "/opt/java/jdk11",
        17, "/usr/lib/jvm/temurin-17-jdk-amd64",  // Default in scip-java image
        21, "/opt/java/jdk21"
    );

    // Detected Java version for this project
    private Integer detectedJavaVersion;

    public ScipJavaRunner(Path workingDirectory) {
        super(workingDirectory);
    }

    public ScipJavaRunner(Path workingDirectory, int timeoutMinutes) {
        super(workingDirectory, timeoutMinutes);
    }

    @Override
    protected String getToolCommand() {
        return "scip-java";
    }

    @Override
    protected List<String> getIndexArgs(Path outputPath) {
        return List.of(
            "index",
            "--output",
            outputPath.toAbsolutePath().toString()
        );
    }

    @Override
    public boolean isValidProject() {
        return anyFileExists("pom.xml", "build.gradle", "build.gradle.kts");
    }

    @Override
    public String detectBuildTool() {
        if (fileExists("pom.xml")) {
            return "maven";
        } else if (fileExists("build.gradle") || fileExists("build.gradle.kts")) {
            return "gradle";
        }
        return "unknown";
    }

    /**
     * Pre-process: Fix CRLF line endings and detect Java version.
     */
    @Override
    protected void preProcess() throws ScipException {
        fixMvnwLineEndings();
        detectJavaVersion();
    }

    /**
     * Get custom environment with JAVA_HOME set based on detected version.
     */
    @Override
    protected Map<String, String> getEnvironment() {
        Map<String, String> env = new HashMap<>();
        
        if (detectedJavaVersion != null) {
            String javaHome = getJavaHomeForVersion(detectedJavaVersion);
            if (javaHome != null) {
                env.put("JAVA_HOME", javaHome);
                logger.info("Setting JAVA_HOME={} for Java {}", javaHome, detectedJavaVersion);
            }
        }
        
        return env;
    }

    /**
     * Get JAVA_HOME path for a specific Java version.
     */
    private String getJavaHomeForVersion(int version) {
        // Check exact match first
        if (JAVA_HOME_PATHS.containsKey(version)) {
            return JAVA_HOME_PATHS.get(version);
        }
        
        // Find closest higher version
        return JAVA_HOME_PATHS.entrySet().stream()
            .filter(e -> e.getKey() >= version)
            .min(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    /**
     * Detect Java version from pom.xml or build.gradle.
     */
    private void detectJavaVersion() {
        if (fileExists("pom.xml")) {
            detectedJavaVersion = detectJavaVersionFromPom();
        } else if (fileExists("build.gradle") || fileExists("build.gradle.kts")) {
            detectedJavaVersion = detectJavaVersionFromGradle();
        }
        
        if (detectedJavaVersion != null) {
            logger.info("Detected Java version {} from build file", detectedJavaVersion);
        } else {
            logger.info("Could not detect Java version, using default");
        }
    }

    /**
     * Extract Java version from pom.xml.
     * Looks for maven.compiler.source, maven.compiler.target, or java.version properties.
     */
    private Integer detectJavaVersionFromPom() {
        try {
            String content = Files.readString(workingDirectory.resolve("pom.xml"));
            
            // Pattern to match maven.compiler.source, maven.compiler.target, or java.version
            String[] propertyNames = {
                "maven.compiler.source",
                "maven.compiler.target", 
                "java.version"
            };
            
            for (String prop : propertyNames) {
                // Try <maven.compiler.source>8</maven.compiler.source>
                Pattern pattern = Pattern.compile("<" + prop.replace(".", "\\.") + ">([^<]+)<");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return parseJavaVersion(matcher.group(1).trim());
                }
            }
            
            // Try <source>8</source> and <target>8</target> inside maven-compiler-plugin
            Pattern sourcePattern = Pattern.compile("<source>([^<]+)</source>");
            Matcher sourceMatcher = sourcePattern.matcher(content);
            if (sourceMatcher.find()) {
                return parseJavaVersion(sourceMatcher.group(1).trim());
            }
            
            // Try <release>11</release> inside maven-compiler-plugin
            Pattern releasePattern = Pattern.compile("<release>([^<]+)</release>");
            Matcher releaseMatcher = releasePattern.matcher(content);
            if (releaseMatcher.find()) {
                return parseJavaVersion(releaseMatcher.group(1).trim());
            }
            
        } catch (IOException e) {
            logger.warn("Failed to read pom.xml: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Extract Java version from build.gradle.
     */
    private Integer detectJavaVersionFromGradle() {
        String gradleFile = fileExists("build.gradle.kts") ? "build.gradle.kts" : "build.gradle";
        
        try {
            String content = Files.readString(workingDirectory.resolve(gradleFile));
            
            // Pattern: sourceCompatibility = '1.8' or sourceCompatibility = JavaVersion.VERSION_11
            Pattern pattern = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?([^'\"\\s]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return parseJavaVersion(matcher.group(1));
            }
            
            // Pattern: java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
            Pattern toolchainPattern = Pattern.compile("languageVersion\\.set\\(JavaLanguageVersion\\.of\\((\\d+)\\)\\)");
            Matcher toolchainMatcher = toolchainPattern.matcher(content);
            if (toolchainMatcher.find()) {
                return Integer.parseInt(toolchainMatcher.group(1));
            }
            
        } catch (IOException e) {
            logger.warn("Failed to read {}: {}", gradleFile, e.getMessage());
        }
        
        return null;
    }

    /**
     * Parse Java version string to integer.
     * Handles: "8", "1.8", "11", "17", "JavaVersion.VERSION_11"
     */
    private Integer parseJavaVersion(String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        
        // Handle JavaVersion.VERSION_XX
        if (version.contains("VERSION_")) {
            Pattern pattern = Pattern.compile("VERSION_(\\d+)");
            Matcher matcher = pattern.matcher(version);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        
        // Handle 1.8 -> 8
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }
        
        // Handle ${java.version} or other property references
        if (version.startsWith("$")) {
            return null;  // Cannot resolve property references
        }
        
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void fixMvnwLineEndings() {
        Path mvnwPath = workingDirectory.resolve("mvnw");
        if (Files.exists(mvnwPath)) {
            try {
                String content = Files.readString(mvnwPath);
                if (content.contains("\r\n")) {
                    logger.info("Detected CRLF line endings in mvnw. Converting to LF...");
                    content = content.replace("\r\n", "\n");
                    Files.writeString(mvnwPath, content);
                    logger.info("Successfully converted mvnw line endings to LF.");
                }
            } catch (IOException e) {
                logger.warn("Failed to check/fix mvnw line endings: {}", e.getMessage());
            }
        }
    }

    // Static convenience methods for backward compatibility

    /**
     * Check if scip-java is installed.
     */
    public static boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-java", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Get scip-java version.
     */
    public static String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-java", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : "unknown";
            }
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
