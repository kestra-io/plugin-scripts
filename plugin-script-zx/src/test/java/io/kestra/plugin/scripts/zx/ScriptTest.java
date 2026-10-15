package io.kestra.plugin.scripts.zx;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void script() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script zxScript = Script.builder()
            .id("zx-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("$.verbose = false; const name = 'hello there!'; console.log((await $`echo ${name}`).stdout.trim());"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, zxScript, ImmutableMap.of());
        ScriptOutput run = zxScript.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("hello there!"));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }

    @Test
    void failedShellCommandFailsTask() {
        Script task = Script.builder()
            .id("zx-failure-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("await $`exit 7`;"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        RunnableTaskException failure = assertThrows(RunnableTaskException.class, () -> task.run(runContext));
        // zx reports the child exit code in its error, but the CLI itself exits with 1.
        assertThat(((ScriptOutput) failure.getOutput()).getExitCode(), is(1));
    }

    @Test
    void rendersScriptAndPreservesFilesAndEnvironment() throws Exception {
        Script task = Script.builder()
            .id("zx-files-" + UUID.randomUUID())
            .type(Script.class.getName())
            .taskRunner(Docker.builder().type(Docker.class.getName()).networkMode("none").build())
            .executable(Property.ofExpression("{{ inputs.zxCommand }}"))
            .inputFiles(Map.of("input.txt", "hello"))
            .env(Property.ofValue(Map.of("ZX_TEST_SUFFIX", "world")))
            .beforeCommands(Property.ofValue(List.of("mkdir -p result")))
            .outputFiles(Property.ofValue(List.of("result/output.txt")))
            .script(Property.ofExpression("""
                import { readFile, writeFile } from 'node:fs/promises';
                import { networkInterfaces } from 'node:os';
                if (Object.values(networkInterfaces()).flat().some(address => !address.internal)) {
                  throw new Error('Configured isolated network was not preserved');
                }
                const input = await readFile('input.txt', 'utf8');
                await writeFile('result/output.txt', `${input} ${process.env.ZX_TEST_SUFFIX} {{ inputs.marker }}`);
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of("marker", "rendered", "zxCommand", "node /zx/build/cli.js"));
        ScriptOutput output = task.run(runContext);

        assertThat(output.getExitCode(), is(0));
        try (var stream = storageInterface.get(TenantService.MAIN_TENANT, null, output.getOutputFiles().get("result/output.txt"))) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8), is("hello world rendered"));
        }
    }
}
