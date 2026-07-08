package io.kestra.plugin.scripts.python.internals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.python.Script;

import jakarta.inject.Inject;

import static io.kestra.core.utils.TestsUtils.mockRunContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@KestraTest
class PythonEnvironmentManagerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldResolveARunnableInterpreterForProcessRunnerWithoutExplicitPythonVersion() throws Exception {
        // Regression test: without an explicit pythonVersion, the Process runner used to default the
        // interpreter to the literal "python", which doesn't exist on python3-only hosts (Debian/Ubuntu,
        // the standard Kestra worker image), causing "/bin/sh: python: not found" (exit code 127).
        Script task = Script.builder()
            .id("python-env-manager-test-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("print('hello')"))
            .build();
        RunContext runContext = mockRunContext(runContextFactory, task, Map.of());

        PythonEnvironmentManager manager = new PythonEnvironmentManager(runContext, task);
        PythonEnvironmentManager.ResolvedPythonEnvironment environment = manager.setup(task.getContainerImage(), null, RunnerType.PROCESS);

        assertThat(environment.interpreter(), is(not("python")));

        Process process = new ProcessBuilder(environment.interpreter(), "--version").start();
        assertThat(process.waitFor(), is(0));
    }
}
