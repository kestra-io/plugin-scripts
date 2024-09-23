package io.kestra.plugin.scripts.shell;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

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
            namespace: company.team

            tasks:
              - id: rust
                type: io.kestra.plugin.scripts.shell.Commands
                commands:
                  - etl
                containerImage: ghcr.io/kestra-io/rust:latest
                outputFiles:
                  - "*.csv"
            """
        ),        
        @Example(
            title = "Execute a single Shell command.",
            full = true,
            code = """
                   id: shell_single_command
                   namespace: company.team

                   tasks:
                     - id: command
                       type: io.kestra.plugin.scripts.shell.Commands
                       commands:
                         - 'echo "The current execution is: {{ execution.id }}"'
                   """
        ),
        @Example(
            title = "Include only specific namespace files.",
            full = true,
            code = """
                   id: include_files
                   namespace: company.team

                   tasks:
                     - id: command
                       type: io.kestra.plugin.scripts.shell.Commands
                       description: "Only the included `namespaceFiles` get listed"
                       namespaceFiles:
                         enabled: true
                         include:
                           - test1.txt
                           - test2.yaml
                       commands:
                         - ls
                   """
        ),
        @Example(
            title = "Exclude specific namespace files.",
            full = true,
            code = """
                   id: exclude_files
                   namespace: company.team

                   tasks:
                     - id: command
                       type: io.kestra.plugin.scripts.shell.Commands
                       description: "All `namespaceFiles` except those that are excluded will be injected into the task's working directory"
                       namespaceFiles:
                         enabled: true
                         exclude:
                           - test1.txt
                           - test2.yaml
                       commands:
                         - ls
                   """
        ),
        @Example(
            title = "Execute Shell commands that generate files accessible by other tasks and available for download in the UI's Output tab.",
            full = true,
            code = """
                   id: shell_generate_files
                   namespace: company.team

                   tasks:
                     - id: commands
                       type: io.kestra.plugin.scripts.shell.Commands
                       outputFiles:
                         - first.txt
                         - second.txt
                       commands:
                         - echo "1" >> first.txt
                         - echo "2" >> second.txt
                   """
        ),
        @Example(
            title = "Execute a Shell command using an input file generated in a previous task.",
            full = true,
            code = """
                   id: use_input_file
                   namespace: company.team

                   tasks:
                     - id: http_download
                      type: io.kestra.plugin.core.http.Download
                      uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/products.csv
                  
                    - id: commands
                      type: io.kestra.plugin.scripts.shell.Commands
                      commands:
                        - cat {{ outputs.http_download.uri }}
                   """
        ),
        @Example(
            title = "Run a PHP Docker container and execute a command.",
            full = true,
            code = """
                   id: run_php_code
                   namespace: company.team

                   tasks:
                     - id: commands
                       type: io.kestra.plugin.scripts.shell.Commands
                       taskRunner:
                         type: io.kestra.plugin.scripts.runner.docker.Docker
                       containerImage: php
                       commands:
                         - php -r 'print(phpversion());'
                   """
        ),
        @Example(
            title = "Create output variables from a standard output.",
            full = true,
            code = """
                   id: create_output_variables
                   namespace: company.team

                   tasks:
                     - id: commands
                       type: io.kestra.plugin.scripts.shell.Commands
                       commands:
                         - echo '::{"outputs":{"test":"value","int":2,"bool":true,"float":3.65}}::'
                   """
        ),
        @Example(
            title = "Send a counter metric from a standard output.",
            full = true,
            code = """
                   id: create_counter_metric
                   namespace: company.team

                   tasks:
                     - id: commands
                       type: io.kestra.plugin.scripts.shell.Commands
                       commands:
                         - echo '::{"metrics":[{"name":"count","type":"counter","value":1,"tags":{"tag1":"i","tag2":"win"}}]}::' 
                   """
        )
    }
)
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "ubuntu";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "Shell commands to run."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            this.commands,
            this.targetOS
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
