package io.kestra.plugin.scripts.python.internals;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonVersionParser {

    private static final Pattern PYTHON_DOCKER_IMAGE_PATTERN = Pattern.compile("python:([0-9]+(?:\\.[0-9]+){0,2})");
    private static final int MIN_SUPPORTED_MAJOR = 3;
    private static final int MIN_SUPPORTED_MINOR = 7;

    public static Optional<String> parsePyVersionFromDockerImage(String imageName) {
        if (imageName == null) {
            return Optional.empty();
        }
        var matcher = PYTHON_DOCKER_IMAGE_PATTERN.matcher(imageName);
        if (matcher.find()) {
            var version = matcher.group(1);
            if (isSupportedPythonVersion(version)) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the parsed version looks like a supported Python version (>= 3.7).
     * This filters out non-Python version tags (e.g. internal image versions like "0.0.10")
     * that happen to match the numeric pattern.
     */
    private static boolean isSupportedPythonVersion(String version) {
        var parts = version.split("\\.");
        var major = Integer.parseInt(parts[0]);
        if (major > MIN_SUPPORTED_MAJOR) {
            return true;
        }
        if (major < MIN_SUPPORTED_MAJOR) {
            return false;
        }
        // major == 3: if only major is specified (e.g. "python:3"), accept it
        if (parts.length == 1) {
            return true;
        }
        var minor = Integer.parseInt(parts[1]);
        return minor >= MIN_SUPPORTED_MINOR;
    }
}
