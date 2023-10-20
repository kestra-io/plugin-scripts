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
import lombok.*;
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
            full = true,
            title = "Execute ETL in Rust in a Docker container and output CSV files generated as a result of the script.",
            code = """     
id: rustFlow
namespace: dev
tasks:
  - id: wdir
    type: io.kestra.core.tasks.flows.WorkingDirectory
    tasks:
      - id: rust
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - etl
        docker:
          image: ghcr.io/kestra-io/rust:latest

      - id: downloadFiles
        type: io.kestra.core.tasks.storages.LocalFiles
        outputs:
          - "*.csv"
"""
        ),        
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
    private static final String DEFAULT_IMAGE = "ubuntu";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(DEFAULT_IMAGE);
        }

        return builder.build();
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
