package io.kestra.plugin.scripts.shell;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more Shell commands."
)
@Plugin(
    examples = {
        @Example(
            title = "Single shell command",
            code = {
                "commands:",
                "- 'echo \"The current execution is : {{ execution.id }}\"'"
            }
        ),
        @Example(
            title = "Shell command that generate file in storage accessible through outputs",
            code = {
                "commands:",
                "- echo \"1\" >> {{ outputDir }}/first.txt",
                "- echo \"2\" >> {{ outputDir }}/second.txt"
            }
        ),
        @Example(
            title = "Shell with an input file from Kestra's local storage created by a previous task.",
            code = {
                "commands:",
                "  - cat {{ outputs.previousTaskId.uri }}"
            }
        ),
        @Example(
            title = "Run a command on a docker image",
            code = {
                "runner: DOCKER",
                "docker:",
                "  image: php",
                "commands:",
                "- 'php -r 'print(phpversion() . \"\\n\");'",
            }
        ),
        @Example(
            title = "Set outputs from bash standard output",
            code = {
                "commands:",
                "  - echo '::{\"outputs\":{\"test\":\"value\",\"int\":2,\"bool\":true,\"float\":3.65}}::'",
            }
        ),
        @Example(
            title = "Send a counter metric from bash standard output",
            code = {
                "commands:",
                "  - echo '::{\"metrics\":[{\"name\":\"count\",\"type\":\"counter\",\"value\":1,\"tags\":{\"tag1\":\"i\",\"tag2\":\"win\"}}]}::'",
            }
        )
    }
)
public class Commands extends AbstractExecScript {
    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected List<String> commands;

    @Override
    protected DockerOptions defaultDockerOptions() {
        return DockerOptions.builder()
            .image("ubuntu")
            .build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            this.commands
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
