package io.kestra.plugin.scripts.r;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(logs::add);

        URI put = storageInterface.put(
            new URI("/file/storage/get.yml"),
            IOUtils.toInputStream(
                "aString = \"Hello World!\"\n" +
                    "print (aString)",
                StandardCharsets.UTF_8
            )
        );

        Commands bash = Commands.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .commands(List.of("Rscript " + put.toString()))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Hello World"));
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("[1]")).count(), is(1L));
    }
}
