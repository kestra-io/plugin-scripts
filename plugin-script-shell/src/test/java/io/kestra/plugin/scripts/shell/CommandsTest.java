package io.kestra.plugin.scripts.shell;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.script.ScriptException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.PullPolicy;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(RunnerType.DOCKER, DockerOptions.builder().image("ubuntu").build()),
            Arguments.of(RunnerType.PROCESS, DockerOptions.builder().build())
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void task(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .commands(List.of("echo 0", "echo 1", ">&2 echo 2", ">&2 echo 3"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(2));
        assertThat(run.getStdErrLineCount(), is(2));
    }

    @ParameterizedTest
    @MethodSource("source")
    void failed(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .commands(List.of("echo 1 1>&2", "exit 66", "echo 2"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptException scriptException = assertThrows(ScriptException.class, () -> {
            bash.run(runContext);
        });

        assertThat(scriptException.getExitCode(), is(66));
        assertThat(scriptException.getStdOutSize(), is(0));
        assertThat(scriptException.getStdErrSize(), is(1));
    }

    @ParameterizedTest
    @MethodSource("source")
    void stopOnFirstFailed(RunnerType runner, DockerOptions dockerOptions) {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .beforeCommands(List.of("set -e"))
            .commands(List.of("unknown", "echo 1"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptException scriptException = assertThrows(ScriptException.class, () -> {
            bash.run(runContext);
        });

        assertThat(scriptException.getExitCode(), is(127));
        assertThat(scriptException.getStdOutSize(), is(0));
        assertThat(scriptException.getStdErrSize(), is(1));
    }

    @ParameterizedTest
    @MethodSource("source")
    void dontStopOnFirstFailed(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .commands(List.of("unknown", "echo 1"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(1));
    }

    @ParameterizedTest
    @MethodSource("source")
    void files(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            IOUtils.toInputStream("I'm here", StandardCharsets.UTF_8)
        );

        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .commands(List.of(
                "mkdir -p {{ outputDir}}/sub/dir/",
                "echo '::{\"outputs\": {\"extract\":\"'$(cat " + put.toString() + ")'\"}}::'",
                "echo 1 >> {{ outputDir}}/file.xml",
                "echo 2 >> {{ outputDir}}/sub/dir/file.csv",
                "echo 3 >> {{ outputDir}}/file.xml"
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErrLineCount(), is(0));

        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("I'm here"));
        assertThat(run.getOutputFiles().size(), is(2));

        InputStream get = storageInterface.get(null, run.getOutputFiles().get("file.xml"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("1\n3\n")
        );

        get = storageInterface.get(null, run.getOutputFiles().get("sub/dir/file.csv"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("2\n")
        );
    }


    @ParameterizedTest
    @MethodSource("source")
    void nullOutputs(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .commands(List.of(
                "echo '::{\"outputs\": {\"extract\":null}}::'"
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErrLineCount(), is(0));

        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is(nullValue()));
        assertThat(run.getVars().containsKey("extract"), is(true));
    }

    @Test
    void pull() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(l -> logs.add(l.getLeft()));

        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(DockerOptions.builder()
                .pullPolicy(PullPolicy.IF_NOT_PRESENT)
                .image("alpine:3.15.6")
                .build()
            )
            .runner(RunnerType.DOCKER)
            .commands(List.of("pwd"))
            .build();


        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);
        assertThat(run.getExitCode(), is(0));

        run = bash.run(runContext);
        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Image pulled"));

        assertThat(logs.stream().filter(m -> m.getMessage().contains("pulled")).count(), is(1L));
    }

    @Test
    void invalidImage() {
        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Commands.class.getName())
            .docker(DockerOptions.builder()
                .pullPolicy(PullPolicy.IF_NOT_PRESENT)
                .image("alpine:999.15.6")
                .build()
            )
            .runner(RunnerType.DOCKER)
            .commands(List.of("pwd"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        Exception exception = assertThrows(Exception.class, () -> bash.run(runContext));

        assertThat(exception.getMessage(), containsString("manifest for alpine:999.15.6 not found"));
    }
}
