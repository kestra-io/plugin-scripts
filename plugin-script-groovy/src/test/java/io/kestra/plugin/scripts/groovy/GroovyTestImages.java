package io.kestra.plugin.scripts.groovy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.runner.docker.DockerService;

final class GroovyTestImages {
    private static final long IMAGE_BUILD_TIMEOUT_MINUTES = 5;

    private GroovyTestImages() {
    }

    static void buildNonRootTestImage(RunContext runContext, String tag) throws Exception {
        Path buildContext = Files.createTempDirectory("groovy-non-root-image");
        Files.writeString(buildContext.resolve("Dockerfile"), """
            FROM groovy:jdk21
            USER root
            RUN groupadd -g 5000 kestratest && useradd -u 5000 -g 5000 -m kestratest
            USER kestratest
            """);

        try (DockerClient dockerClient = DockerService.client(runContext, null, null, null, null)) {
            dockerClient.buildImageCmd(buildContext.resolve("Dockerfile").toFile())
                .withTags(Set.of(tag))
                .start()
                .awaitImageId(IMAGE_BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } finally {
            Files.deleteIfExists(buildContext.resolve("Dockerfile"));
            Files.deleteIfExists(buildContext);
        }
    }

    static void removeTestImage(RunContext runContext, String tag) {
        try (DockerClient dockerClient = DockerService.client(runContext, null, null, null, null)) {
            dockerClient.removeImageCmd(tag).withForce(true).exec();
        } catch (Exception ignored) {
            // best-effort cleanup of the throwaway test image
        }
    }
}
