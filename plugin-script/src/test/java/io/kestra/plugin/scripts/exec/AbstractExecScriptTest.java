package io.kestra.plugin.scripts.exec;

import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class AbstractExecScriptTest {
    @Inject
    private ModelValidator modelValidator;

    @SuperBuilder
    @Getter
    public static class AbstractExecScriptCls extends AbstractExecScript {
        @Builder.Default
        protected DockerOptions docker = DockerOptions.builder().build();

        @Override
        public ScriptOutput run(RunContext runContext) throws Exception {
            throw new NotImplementedException();
        }
    }

    @Test
    void validation() {
        final AbstractExecScriptCls defaults = AbstractExecScriptCls.builder()
            .id("unit-test")
            .type(AbstractExecScriptCls.class.getName())
            .build();

        assertThat(modelValidator.isValid(defaults).isEmpty(), is(true));

        final AbstractExecScriptCls validDocker = AbstractExecScriptCls.builder()
            .id("unit-test")
            .type(AbstractExecScriptCls.class.getName())
            .docker(DockerOptions.builder()
                .pullPolicy(DockerOptions.PullPolicy.IF_NOT_PRESENT)
                .build()
            )
            .runner(RunnerType.DOCKER)
            .build();

        assertThat(modelValidator.isValid(validDocker).isEmpty(), is(true));

        final AbstractExecScriptCls validProcess = AbstractExecScriptCls.builder()
            .id("unit-test")
            .type(AbstractExecScriptCls.class.getName())
            .runner(RunnerType.PROCESS)
            .build();

        assertThat(modelValidator.isValid(validProcess).isEmpty(), is(true));

        final AbstractExecScriptCls dockerPolicyWithProcessRunner = AbstractExecScriptCls.builder()
            .id("unit-test")
            .type(AbstractExecScriptCls.class.getName())
            .docker(DockerOptions.builder()
                .pullPolicy(DockerOptions.PullPolicy.IF_NOT_PRESENT)
                .build()
            )
            .runner(RunnerType.PROCESS)
            .build();

        assertThat(modelValidator.isValid(dockerPolicyWithProcessRunner).isPresent(), is(true));
        assertThat(
            modelValidator.isValid(dockerPolicyWithProcessRunner).get().getMessage(),
            containsString(": invalid script: custom Docker options require the Docker runner")
        );
    }
}
