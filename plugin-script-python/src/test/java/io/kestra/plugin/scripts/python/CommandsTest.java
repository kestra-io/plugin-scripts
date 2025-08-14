package io.kestra.plugin.scripts.python;

import com.google.common.collect.ImmutableMap;
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
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.python.internals.PackageManagerType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
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
            .commands(Property.ofValue(List.of("python " + put.toString())))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("hello there!"));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }

    @Test
    void testPipPackageManager() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        String script = "import requests, idna; print(f'requests: {requests.__version__}, idna: {idna.__version__}')";
        URI put = storageInterface.put(TenantService.MAIN_TENANT, null, new URI("/file/storage/pip_test.py"), IOUtils.toInputStream(script, StandardCharsets.UTF_8));

        Commands task = Commands.builder()
            .id("test-pip")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("python " + put.toString())))
            .packageManager(Property.ofValue(PackageManagerType.PIP))
            .dependencies(Property.ofValue(List.of("requests", "idna")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);

        assertThat(run.getExitCode(), is(0));
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("requests:"));
        receive.blockLast();
    }

    @Test
    void testUvPackageManager() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        String script = "import requests; print(f'requests (UV): {requests.__version__}')";
        URI put = storageInterface.put(TenantService.MAIN_TENANT, null, new URI("/file/storage/uv_test.py"), IOUtils.toInputStream(script, StandardCharsets.UTF_8));

        Commands task = Commands.builder()
            .id("test-uv")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("python " + put.toString())))
            .dependencies(Property.ofValue(List.of("requests")))
            .packageManager(Property.ofValue(PackageManagerType.UV))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);

        assertThat(run.getExitCode(), is(0));
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("uv test worked"));
        receive.blockLast();
    }

    @Test
    void testBackwardCompatibility() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        String script = "import requests; print('backward compatibility works', requests.__version__)";
        URI put = storageInterface.put(TenantService.MAIN_TENANT, null, new URI("/file/storage/compat_test.py"), IOUtils.toInputStream(script, StandardCharsets.UTF_8));

        Commands task = Commands.builder()
            .id("test-compat")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("python " + put.toString())))
            .dependencies(Property.ofValue(List.of("requests")))
            .dependencyCacheEnabled(Property.ofValue(false))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        ScriptOutput run = task.run(runContext);

        assertThat(run.getExitCode(), is(0));
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("backward compatibility works"));
        receive.blockLast();

        assertThat(logs.stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("backward compatibility works")), is(true));
    }
}
