package io.kestra.plugin.scripts.python.internals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PythonVersionParserTest {

    @Test
    void shouldParseStandardPythonImage() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:3.11");
        assertThat(result, is(Optional.of("3.11")));
    }

    @Test
    void shouldParseSlimPythonImage() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:3.13-slim");
        assertThat(result, is(Optional.of("3.13")));
    }

    @Test
    void shouldParsePythonImageWithFullVersion() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:3.11.4");
        assertThat(result, is(Optional.of("3.11.4")));
    }

    @Test
    void shouldReturnEmptyForLatestTag() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:latest");
        assertThat(result, is(Optional.empty()));
    }

    @Test
    void shouldReturnEmptyForNonPythonImage() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("ubuntu:22.04");
        assertThat(result, is(Optional.empty()));
    }

    @Test
    void shouldParseCustomEcrImageWithNumericTag() {
        // The parser extracts the version; filtering happens in PythonEnvironmentManager
        var result = PythonVersionParser.parsePyVersionFromDockerImage(
            "123456789.dkr.ecr.ap-southeast-2.amazonaws.com/python:0.0.10"
        );
        assertThat(result, is(Optional.of("0.0.10")));
    }

    @Test
    void shouldParsePython27() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:2.7");
        assertThat(result, is(Optional.of("2.7")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "python:3.6",
        "python:3.7",
        "python:3.8",
        "python:3.9",
        "python:3.10",
        "python:3.11",
        "python:3.12",
        "python:3.13",
        "python:3.14"
    })
    void shouldParseValidPythonVersions(String image) {
        var result = PythonVersionParser.parsePyVersionFromDockerImage(image);
        assertThat(result.isPresent(), is(true));
    }

    @Test
    void shouldHandleNullInput() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage(null);
        assertThat(result, is(Optional.empty()));
    }

    @Test
    void shouldParseMajorOnlyVersion() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage("python:3");
        assertThat(result, is(Optional.of("3")));
    }

    @Test
    void shouldParseRegistryPrefixedImage() {
        var result = PythonVersionParser.parsePyVersionFromDockerImage(
            "my-registry.example.com/python:3.11"
        );
        assertThat(result, is(Optional.of("3.11")));
    }

    @Test
    void looksLikePythonVersion_acceptsPython2() {
        assertThat(PythonEnvironmentManager.looksLikePythonVersion("2.7"), is(true));
    }

    @Test
    void looksLikePythonVersion_acceptsPython3() {
        assertThat(PythonEnvironmentManager.looksLikePythonVersion("3.11"), is(true));
    }

    @Test
    void looksLikePythonVersion_rejectsVersion0() {
        assertThat(PythonEnvironmentManager.looksLikePythonVersion("0.0.10"), is(false));
    }

    @Test
    void looksLikePythonVersion_rejectsVersion1() {
        assertThat(PythonEnvironmentManager.looksLikePythonVersion("1.5.2"), is(false));
    }
}
