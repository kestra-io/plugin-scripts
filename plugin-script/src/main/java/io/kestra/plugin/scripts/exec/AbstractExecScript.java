package io.kestra.plugin.scripts.exec;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractExecScript extends Task implements RunnableTask<ScriptOutput> {
    @Builder.Default
    @Schema(
        title = "Runner to use"
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected RunnerType runner = RunnerType.DOCKER;

    @Schema(
        title = "A list of commands that will run before `commands`, allowing to set up the environment e.g. `pip install -r requirements.txt`"
    )
    @PluginProperty(dynamic = true)
    protected List<String> beforeCommands;

    @Schema(
        title = "Additional environment variables for the current process."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Map<String, String> env;

    @Builder.Default
    @Schema(
        title = "Set the task state to `WARNING`if any `stdErr` is emitted"
    )
    @PluginProperty
    @NotNull
    protected Boolean warningOnStdErr = true;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use"
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected List<String> interpreter = List.of("/bin/sh", "-c");

    abstract public DockerOptions getDocker();

    protected CommandsWrapper commands(RunContext runContext) throws IllegalVariableEvaluationException {
        CommandsWrapper commands = new CommandsWrapper(runContext)
            .withEnv(this.env)
            .withWarningOnStdErr(this.warningOnStdErr)
            .withRunnerType(this.runner)
            .withDockerOptions(this.getDocker());

        return commands;
    }
}
