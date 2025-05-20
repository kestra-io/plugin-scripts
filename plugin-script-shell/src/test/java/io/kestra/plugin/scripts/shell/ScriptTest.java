package io.kestra.plugin.scripts.shell;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Credentials;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ScriptTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

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
            .script(Property.of("""
                echo 0
                echo 1
                >&2 echo 2
                >&2 echo 3
            """))
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
        Script bash = Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .docker(dockerOptions)
            .interpreter(Property.of(List.of("/bin/bash", "-c")))
            .runner(runner)
            .script(Property.of("""
                for i in {1..2000}
                do
                   echo "$i"
                done
            """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(2000));
        assertThat(run.getStdErrLineCount(), is(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void overwrite() throws Exception {
        Function<String, Script> function = (String username) -> Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .docker(DockerOptions.builder()
                .image("ubuntu")
                .credentials(Credentials.builder()
                    .registry(Property.of("own.registry"))
                    .username(Property.of(username))
                    .password(Property.of("doe"))
                    .build()
                )
                .build()
            )
            .script(Property.of("""
                    echo '::{"outputs":{"config":'$(cat config.json)'}}::'
                """))
            .build();


        Script bash = function.apply("john");
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());

        ScriptOutput run = bash.run(runContext);
        assertThat(run.getExitCode(), is(0));
        assertThat(((Map<String, Map<String, Map<String, Object>>>)run.getVars().get("config")).get("auths").get("own.registry").get("username"), is("john"));
        assertThat(run.getExitCode(), is(0));

        bash = function.apply("jane");
        run = bash.run(runContext);
        assertThat(run.getExitCode(), is(0));
        assertThat(((Map<String, Map<String, Map<String, Object>>>)run.getVars().get("config")).get("auths").get("own.registry").get("username"), is("jane"));
        assertThat(run.getExitCode(), is(0));
    }

    @Test
    void inputOutputFiles() throws Exception {
        Script bash = Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .inputFiles(Map.of(
                "test/application.yml", internalFiles("/test/" + IdUtils.create() + ".yml").toString()
            ))
            .outputFiles(Property.of(
                List.of("out/**")))
            .script(Property.of("""
                mkdir out
                cat test/application.yml > out/bla.yml
            """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getOutputFiles().get("out/bla.yml").toString(), startsWith("kestra://"));
        assertThat(
            new String(storageInterface.get(null, null, run.getOutputFiles().get("out/bla.yml")).readAllBytes()),
            containsString("base-path: /tmp/unittest")
        );
    }

    private URI internalFiles(String path) throws IOException, URISyntaxException {
        var resource = ScriptTest.class.getClassLoader().getResource("application.yml");

        return storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI(path),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }
}
