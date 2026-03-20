package io.kestra.plugin.scripts.python.internals;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonVersionParser {

    private static final Pattern PYTHON_DOCKER_IMAGE_PATTERN = Pattern.compile("python:([0-9]+(?:\\.[0-9]+){0,2})");

    public static Optional<String> parsePyVersionFromDockerImage(String imageName) {
        if (imageName == null) {
            return Optional.empty();
        }
        var matcher = PYTHON_DOCKER_IMAGE_PATTERN.matcher(imageName);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
