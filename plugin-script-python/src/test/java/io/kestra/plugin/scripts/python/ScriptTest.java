package io.kestra.plugin.scripts.python;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(RunnerType.DOCKER, DockerOptions.builder().image("python").build()),
            Arguments.of(RunnerType.PROCESS, DockerOptions.builder().build())
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void task(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("print('::{\"outputs\": {\"extract\":\"hello world\"}}::')"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("hello world"));
    }

    @ParameterizedTest
    @MethodSource("source")
    void failed(RunnerType runner, DockerOptions dockerOptions) {
        Script python = Script.builder()
            .id("python-script-failed-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("import sys; sys.exit(1)"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        RunnableTaskException pythonException = assertThrows(RunnableTaskException.class, () -> {
            python.run(runContext);
        });

        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) pythonException.getOutput()).getExitCode(), is(1));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) pythonException.getOutput()).getStdOutLineCount(), is(0));
    }

    @ParameterizedTest
    @MethodSource("source")
    void requirements(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-script-requirements-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("import requests;" +
                "print('::{\"outputs\": {\"extract\":\"' + str(requests.get('https://google.com').status_code) + '\"}}::')"
            ))
            .beforeCommands(Property.of(List.of(
                "python3 -m venv venv",
                ". venv/bin/activate",
                "pip install requests > /dev/null"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
    }

    @ParameterizedTest
    @MethodSource("source")
    void shouldExecScriptGivenDependency(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-script-deps-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .dependencies(Property.of(List.of("kestra", "pandas")))
            .pythonVersion(Property.of("3"))
            .script(Property.of("""
                  from kestra import Kestra
                  import pandas as pd
                  data = {
                      'Name': ['Alice', 'Bob', 'Charlie'],
                      'Age': [25, 30, 35]
                  }
                  df = pd.DataFrame(data)
                  print(df)
                  print("Average age:", df['Age'].mean())
                  Kestra.outputs({"average_age": df['Age'].mean()})
                  """
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("average_age"), is(30.0));
    }

    @ParameterizedTest
    @MethodSource("source")
    void inputs(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/tests/scripts/get.yml"),
            IOUtils.toInputStream(
                "hello there!",
                StandardCharsets.UTF_8
            )
        );

        Script python = Script.builder()
            .id("python-script-inputs-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("import os\n" +
                "\n" +
                "file_size = os.path.getsize(\"" + put.toString() + "\")\n" +
                "print('::{\"outputs\": {\"extract\":' + str(file_size) + '}}::')"
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is(12));
    }

    @ParameterizedTest
    @MethodSource("source")
    void multiline(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-script-multiline-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("from kestra import Kestra\n" +
                "print(\"1234\\n\\n\")\n" +
                "Kestra.outputs({'secrets': \"test string\"})"
            ))
            .beforeCommands(Property.of(List.of(
                "python -m venv venv",
                ". venv/bin/activate",
                "pip install kestra > /dev/null"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("secrets"), is("test string"));
    }

    @ParameterizedTest
    @MethodSource("source")
    void emptyScript(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-empty-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .beforeCommands(Property.of(List.of(
                "python -m venv venv",
                ". venv/bin/activate",
                "pip install kestra > /dev/null"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of("script", "efzfe"));

        ConstraintViolationException exception = assertThrows(ConstraintViolationException.class, () -> python.run(runContext));
        assertThat(exception.getMessage(), containsString("script: must not be null"));
    }

    @ParameterizedTest
    @MethodSource("source")
    void kestraLibs(RunnerType runner, DockerOptions dockerOptions) throws Exception {
        Script python = Script.builder()
            .id("python-kestra-libs-" + UUID.randomUUID())
            .type(Script.class.getName())
            .docker(dockerOptions)
            .runner(runner)
            .script(Property.of("from kestra import Kestra\n" +
                "import time\n" +
                "Kestra.outputs({'test': 'value', 'int': 2, 'bool': True, 'float': 3.65})\n" +
                "Kestra.counter('count', 1, {'tag1': 'i', 'tag2': 'win'})\n" +
                "Kestra.counter('count2', 2)\n" +
                "Kestra.timer('timer1', lambda: time.sleep(1), {'tag1': 'i', 'tag2': 'lost'})\n" +
                "Kestra.timer('timer2', 2.12, {'tag1': 'i', 'tag2': 'destroy'})\n"
            ))
            .beforeCommands(Property.of(List.of(
                "python -m venv venv",
                ". venv/bin/activate",
                "pip install kestra > /dev/null"
            )))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, python, ImmutableMap.of());
        ScriptOutput run = python.run(runContext);

        assertThat(run.getExitCode(), is(0));

        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        assertThat(getMetrics(runContext, "count").getValue(), is(1D));
        assertThat(getMetrics(runContext, "count2").getValue(), is(2D));
        assertThat(getMetrics(runContext, "count2").getTags().size(), is(0));
        assertThat(getMetrics(runContext, "count").getTags().size(), is(2));
        assertThat(getMetrics(runContext, "count").getTags().get("tag1"), is("i"));
        assertThat(getMetrics(runContext, "count").getTags().get("tag2"), is("win"));

        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer1").getValue().getNano(), greaterThan(0));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer1").getTags().size(), is(2));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag1"), is("i"));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag2"), is("lost"));

        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer2").getValue().getNano(), greaterThan(100000000));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer2").getTags().size(), is(2));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag1"), is("i"));
        assertThat(ScriptTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag2"), is("destroy"));
    }

    @SuppressWarnings("unchecked")
    static <T> AbstractMetricEntry<T> getMetrics(RunContext runContext, String name) {
        return (AbstractMetricEntry<T>) runContext.metrics()
            .stream()
            .filter(abstractMetricEntry -> abstractMetricEntry.getName().equals(name))
            .findFirst()
            .orElseThrow();
    }
}
