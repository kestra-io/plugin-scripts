package io.kestra.plugin.scripts.groovy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.kestra.plugin.scripts.runner.docker.PullPolicy;

import groovy.lang.GroovyShell;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static io.kestra.plugin.scripts.groovy.GroovyTestImages.buildNonRootTestImage;
import static io.kestra.plugin.scripts.groovy.GroovyTestImages.removeTestImage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

@KestraTest
public class ScriptTest {

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

        var groovyScript = Script.builder()
            .id("groovy-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .allowWarning(true)
            .script(Property.ofValue("println(\"Kestra is amazing!\");"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, groovyScript, ImmutableMap.of());
        var run = groovyScript.run(runContext);

        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Kestra is amazing!"));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("Kestra is amazing!")), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void outputFilesOnNonRootImage(boolean legacyDocker) throws Exception {
        runOnNonRootImage(legacyDocker, null, "0:0");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preservesExplicitDockerUser(boolean legacyDocker) throws Exception {
        // Keep root's access to the mounted script, but use a distinct group to detect an overridden user.
        runOnNonRootImage(legacyDocker, "root:5000", "0:5000");
    }

    private void runOnNonRootImage(boolean legacyDocker, String user, String expectedIdentity) throws Exception {
        String image = "kestra-test/groovy-script-non-root:" + UUID.randomUUID();
        var builder = Script.builder()
            .id("groovy-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .allowWarning(true)
            .containerImage(Property.ofValue(image))
            .outputFiles(Property.ofValue(List.of("out.txt")))
            .script(Property.ofValue("""
                assert NetworkInterface.getByName('eth0') == null
                def uid = ['id', '-u'].execute().text.trim()
                def gid = ['id', '-g'].execute().text.trim()
                new File('out.txt').text = uid + ':' + gid
                """));
        if (legacyDocker) {
            builder.docker(DockerOptions.builder().image(image).user(user).networkMode("none").build());
        } else {
            builder.taskRunner(
                Docker.builder()
                    .type(Docker.class.getName())
                    .user(user)
                    .networkMode("none")
                    .pullPolicy(Property.ofValue(PullPolicy.NEVER))
                    .build()
            );
        }
        var script = builder.build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, Map.of());

        buildNonRootTestImage(runContext, image);
        try {
            assertOutput(script.run(runContext), expectedIdentity);
        } finally {
            removeTestImage(runContext, image);
        }
    }

    @Test
    void preservesProcessRunner() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String groovy = Path.of(GroovyShell.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var script = Script.builder()
            .id("groovy-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .taskRunner(Process.builder().type(Process.class.getName()).build())
            .beforeCommands(
                Property.ofValue(
                    List.of(
                        "groovy() { '" + java.replace("'", "'\"'\"'") + "' -cp '" + groovy.replace("'", "'\"'\"'") + "' groovy.ui.GroovyMain \"$@\"; }"
                    )
                )
            )
            .outputFiles(Property.ofValue(List.of("out.txt")))
            .script(Property.ofValue("new File('out.txt').text = 'hello'"))
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, Map.of());

        assertOutput(script.run(runContext), "hello");
    }

    private void assertOutput(ScriptOutput output, String expected) throws Exception {
        assertThat(output.getExitCode(), is(0));
        assertThat(output.getOutputFiles(), hasKey("out.txt"));
        try (InputStream input = storageInterface.get(TenantService.MAIN_TENANT, null, output.getOutputFiles().get("out.txt"))) {
            assertThat(CharStreams.toString(new InputStreamReader(input)), is(expected));
        }
    }
}
