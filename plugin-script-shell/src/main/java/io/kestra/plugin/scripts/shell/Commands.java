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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
id: rust_flow
namespace: dev
tasks:
  - id: rust
    type: io.kestra.plugin.scripts.shell.Commands
    commands:
      - etl
    docker:
      image: ghcr.io/kestra-io/rust:latest
    outputFiles:
      - "*.csv"
"""
        ),        
        @Example(
            title = "Execute a single Shell command",
            code = {
                "commands:",
                "- 'echo \"The current execution is: {{ execution.id }}\"'"
            }
        ),
        @Example(
            title = "Execute Shell commands that generate files accessible by other tasks and available for download in the UI's Output tab.",
            code = {
                "commands:",
                "- echo \"1\" >> {{ outputDir }}/first.txt",
                "- echo \"2\" >> {{ outputDir }}/second.txt"
            }
        ),
        @Example(
            title = "Execute a Shell command using an input file generated in a previous task.",
            code = {
                "commands:",
                "  - cat {{ outputs.previousTaskId.uri }}"
            }
        ),
        @Example(
            title = "Run a PHP Docker container and execute a command",
            code = {
                "runner: DOCKER",
                "docker:",
                "  image: php",
                "commands:",
                "- 'php -r 'print(phpversion() . \"\\n\");'",
            }
        ),
        @Example(
            title = "Create output variables from a standard output",
            code = {
                "commands:",
                "  - echo '::{\"outputs\":{\"test\":\"value\",\"int\":2,\"bool\":true,\"float\":3.65}}::'",
            }
        ),
        @Example(
            title = "Send a counter metric from a standard output",
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
