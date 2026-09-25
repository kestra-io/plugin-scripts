package io.kestra.plugin.scripts.groovy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.kestra.plugin.scripts.runner.docker.PullPolicy;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@KestraTest
class ScriptTaskRunnerTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void preservesDockerOptionsWhenDefaultingToRoot() throws Exception {
        Docker runner = Docker.builder().type(Docker.class.getName())
            .host("unix:///run/user/1000/podman/podman.sock")
            .networkMode("host")
            .pullPolicy(Property.ofValue(PullPolicy.NEVER))
            .build();

        Docker effective = (Docker) runWith(runner);

        assertThat(effective.getHost(), is(runner.getHost()));
        assertThat(effective.getNetworkMode(), is(runner.getNetworkMode()));
        assertThat(effective.getPullPolicy(), is(runner.getPullPolicy()));
        assertThat(effective.getUser(), is("root"));
        assertThat(runner.getUser(), nullValue());
    }

    @Test
    void defaultsToRootWithoutTaskRunner() throws Exception {
        assertThat(((Docker) runWith(null)).getUser(), is("root"));
    }

    @Test
    void preservesExplicitDockerUser() throws Exception {
        Docker runner = Docker.builder().type(Docker.class.getName()).user("1000:1000").build();

        assertThat(runWith(runner), sameInstance(runner));
    }

    @Test
    void preservesProcessRunner() throws Exception {
        Process runner = Process.builder().type(Process.class.getName()).build();

        assertThat(runWith(runner), sameInstance(runner));
    }

    @Test
    void defaultsLegacyDockerUserWithoutOverridingExplicitUser() throws Exception {
        Script script = Script.builder().id("groovy-script-" + UUID.randomUUID()).type(Script.class.getName()).script(Property.ofValue("println 'hello'")).build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, Map.of());

        assertThat(script.injectDefaults(runContext, DockerOptions.builder().build()).getUser(), is("root"));
        assertThat(script.injectDefaults(runContext, DockerOptions.builder().user("1000:1000").build()).getUser(), is("1000:1000"));
    }

    private TaskRunner<?> runWith(TaskRunner<?> runner) throws Exception {
        Script script = Script.builder()
            .id("groovy-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("println 'hello'"))
            .taskRunner(runner)
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, Map.of());
        AtomicReference<TaskRunner<?>> effective = new AtomicReference<>(runner);
        try (var construction = mockConstruction(CommandsWrapper.class, withSettings().defaultAnswer(RETURNS_SELF), (commands, context) ->
        {
            when(commands.getWorkingDirectory()).thenReturn(runContext.workingDir().path());
            when(commands.getTaskRunner()).thenAnswer(invocation -> effective.get());
            when(commands.withTaskRunner(any())).thenAnswer(invocation ->
            {
                effective.set(invocation.getArgument(0));
                return commands;
            });
            when(commands.render(any(), any())).thenReturn("println 'hello'");
        })) {
            script.run(runContext);
            verify(construction.constructed().getFirst()).run();
        }
        return effective.get();
    }
}
