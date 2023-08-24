package io.kestra.plugin.scripts.shell;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

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
    void script(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script bash = Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script("""
                echo 0
                echo 1
                >&2 echo 2
                >&2 echo 3
            """)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(2));
        assertThat(run.getStdErrLineCount(), is(2));
    }

    @ParameterizedTest
    @MethodSource("source")
    void massLog(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(logs::add);

        Script bash = Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .docker(dockerOptions)
            .interpreter(List.of("/bin/bash", "-c"))
            .runner(runner)
            .script("""
                for i in {1..2000}
                do
                   echo "$i"
                done
            """)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(2000));
        assertThat(run.getStdErrLineCount(), is(0));
    }
}
