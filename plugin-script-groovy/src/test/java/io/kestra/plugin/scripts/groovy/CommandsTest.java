package io.kestra.plugin.scripts.groovy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.github.dockerjava.api.DockerClient;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.DockerService;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

@KestraTest
public class CommandsTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        var groovyCommands = Commands.builder()
            .id("groovy-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .allowWarning(true)
            .commands(Property.ofValue(List.of("groovy -e \"println 'I love Kestra!'\"")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, groovyCommands, ImmutableMap.of());
        var run = groovyCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("I love Kestra!"));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("I love Kestra!")), is(true));
    }

    // The Docker runner can create the working directory owned by a user other than the container's
    // default image user, so a `Commands` task running as that non-root user can't write its own
    // outputFiles there (e.g. `groovy` on `groovy:jdk21`, see https://github.com/kestra-io/plugin-scripts/issues/411).
    // The test image forces a non-root user whose uid never matches the current host user, so the
    // reproduction doesn't depend on the host uid coincidentally matching the container's default user.
    @Test
    void outputFilesOnNonRootImage() throws Exception {
        String nonRootImage = "kestra-test/groovy-non-root:" + UUID.randomUUID();

        var groovyCommands = Commands.builder()
            .id("groovy-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .allowWarning(true)
            .containerImage(Property.ofValue(nonRootImage))
            .outputFiles(Property.ofValue(List.of("out.txt")))
            .commands(Property.ofValue(List.of(
                "id",
                "ls -ld .",
                "groovy -e 'new File(\"out.txt\").text = \"hello\"'"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, groovyCommands, ImmutableMap.of());

        buildNonRootTestImage(runContext, nonRootImage);
        try {
            ScriptOutput run = groovyCommands.run(runContext);

            assertThat(run.getExitCode(), is(0));
            assertThat(run.getOutputFiles(), hasKey("out.txt"));

            try (InputStream is = storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("out.txt"))) {
                assertThat(CharStreams.toString(new InputStreamReader(is)), is("hello"));
            }
        } finally {
            removeTestImage(runContext, nonRootImage);
        }
    }

    // Regression test for the deprecated `docker` property path: `CommandsWrapper.getTaskRunner()` rebuilds
    // the runner from `DockerOptions` on every call (via `Docker.from(...)`), so the root default must be
    // applied in `injectDefaults`, not only on the `taskRunner` used by the modern `taskRunner` property.
    @Test
    void outputFilesOnNonRootImageLegacyDockerProperty() throws Exception {
        String nonRootImage = "kestra-test/groovy-non-root-legacy:" + UUID.randomUUID();

        var groovyCommands = Commands.builder()
            .id("groovy-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .allowWarning(true)
            .docker(DockerOptions.builder().image(nonRootImage).build())
            .outputFiles(Property.ofValue(List.of("out.txt")))
            .commands(Property.ofValue(List.of(
                "id",
                "ls -ld .",
                "groovy -e 'new File(\"out.txt\").text = \"hello\"'"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, groovyCommands, ImmutableMap.of());

        buildNonRootTestImage(runContext, nonRootImage);
        try {
            ScriptOutput run = groovyCommands.run(runContext);

            assertThat(run.getExitCode(), is(0));
            assertThat(run.getOutputFiles(), hasKey("out.txt"));

            try (InputStream is = storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("out.txt"))) {
                assertThat(CharStreams.toString(new InputStreamReader(is)), is("hello"));
            }
        } finally {
            removeTestImage(runContext, nonRootImage);
        }
    }

    private void buildNonRootTestImage(RunContext runContext, String tag) throws Exception {
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
                .awaitImageId();
        } finally {
            Files.deleteIfExists(buildContext.resolve("Dockerfile"));
            Files.deleteIfExists(buildContext);
        }
    }

    private void removeTestImage(RunContext runContext, String tag) {
        try (DockerClient dockerClient = DockerService.client(runContext, null, null, null, null)) {
            dockerClient.removeImageCmd(tag).withForce(true).exec();
        } catch (Exception ignored) {
            // best-effort cleanup of the throwaway test image
        }
    }
}