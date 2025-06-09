package io.kestra.plugin.scripts.python;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
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
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            IOUtils.toInputStream(
                "print('hello there!')",
                StandardCharsets.UTF_8
            )
        );

        Commands task = Commands.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .commands(Property.of(List.of("python " + put.toString())))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("hello there!"));
        receive.blockLast();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }

    @Test
    void taskWithUv() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));
    
        String script = "import sys; print('uv test:', sys.version)";
        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/uv_test.py"),
            IOUtils.toInputStream(script, StandardCharsets.UTF_8)
        );
    
        Commands task = Commands.builder()
            .id("unit-test-uv")
            .commands(Property.of(List.of("python " + put.toString())))
            .useUv(true)
            .build();
    
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);
    
        assertThat(run.getExitCode(), is(0));
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("uv test:"));
        receive.blockLast();
        assertThat(logs.stream().anyMatch(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("uv test:")), is(true));
    }
    
    @Test
    void taskWithClassicPip() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));
    
        String script = "import sys; print('classic pip test:', sys.version)";
        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/classic_test.py"),
            IOUtils.toInputStream(script, StandardCharsets.UTF_8)
        );
    
        Commands task = Commands.builder()
            .id("unit-test-classic")
            .commands(Property.of(List.of("python " + put.toString())))
            .useUv(false)
            .build();
    
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);
    
        assertThat(run.getExitCode(), is(0));
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("classic pip test:"));
        receive.blockLast();
        assertThat(logs.stream().anyMatch(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("classic pip test:")), is(true));
    }
    
}
