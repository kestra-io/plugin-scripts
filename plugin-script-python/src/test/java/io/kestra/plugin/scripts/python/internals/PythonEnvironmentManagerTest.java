package io.kestra.plugin.scripts.python.internals;

import java.util.List;
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
import static org.hamcrest.Matchers.startsWith;

@KestraTest
class PythonEnvironmentManagerTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldResolveTheManagedInterpreterForProcessRunnerWithDependenciesAndNoExplicitPythonVersion() throws Exception {
        // Regression test for #397: with dependencies declared but no explicit pythonVersion, the
        // interpreter used to be resolved via a hardcoded local PATH probe (PackageManagerType.PIP),
        // regardless of the package manager that actually installed the dependencies. On a worker whose
        // only Python is 'uv'-managed (no local python/python3 binary), that probe fails to find any
        // interpreter at all, causing the script execution to fail ("python: not found", exit code 127).
        // The interpreter must instead be resolved through the same package manager (UV here) that
        // installed the dependencies, which returns the absolute path to the managed interpreter
        // rather than a bare command name looked up on the local PATH.
        Script task = Script.builder()
            .id("python-env-manager-test-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("print('hello')"))
            .packageManager(Property.ofValue(PackageManagerType.UV))
            .dependencies(Property.ofValue(List.of("six")))
            .build();
        RunContext runContext = mockRunContext(runContextFactory, task, Map.of());

        PythonEnvironmentManager manager = new PythonEnvironmentManager(runContext, task);
        PythonEnvironmentManager.ResolvedPythonEnvironment environment = manager.setup(task.getContainerImage(), null, RunnerType.PROCESS);

        assertThat(environment.interpreter(), is(not("python")));
        // A bare PATH-looked-up command name (e.g. "python3") is relative; the UV-managed interpreter is
        // always resolved to an absolute path, proving the resolution went through the package manager.
        assertThat(environment.interpreter(), startsWith("/"));

        Process process = new ProcessBuilder(environment.interpreter(), "--version").start();
        assertThat(process.waitFor(), is(0));
    }
}
