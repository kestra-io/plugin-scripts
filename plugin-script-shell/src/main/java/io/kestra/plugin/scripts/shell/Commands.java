package io.kestra.plugin.scripts.shell;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Shell commands and files."
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
            full = true,
            title = "Execute a single Shell command.",
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
            full = true,
            title = "Include only specific namespace files.",
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
            full = true,
            title = "Exclude specific namespace files.",
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
            full = true,
            title = "Execute Shell commands that generate files accessible by other tasks and available for download in the UI's Output tab.",
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
            full = true,
            title = "Execute a Shell command using an input file generated in a previous task.",
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
            full = true,
            title = "Read a file from inputs",
            code = """
                id: input_file
                namespace: company.team

                inputs:
                  - id: text_file
                    type: FILE

                tasks:
                  - id: read_file
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - cat "{{ inputs.text_file }}"
                """
        ),
        @Example(
            full = true,
            title = "Run a PHP Docker container and execute a command.",
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
            full = true,
            title = "Create output variables from a standard output.",
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
            full = true,
            title = "Send a counter metric from a standard output.",
            code = """
                   id: create_counter_metric
                   namespace: company.team

                   tasks:
                     - id: commands
                       type: io.kestra.plugin.scripts.shell.Commands
                       commands:
                         - echo '::{"metrics":[{"name":"count","type":"counter","value":1,"tags":{"tag1":"i","tag2":"win"}}]}::'
                   """
        ),
        @Example(
            full = true,
            title = "Run C code inside of a Shell environment",
            code = """
                   id: shell_execute_code
                   namespace: company.team

                   inputs:
                     - id: dataset_url
                       type: STRING
                       defaults: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                   tasks:
                     - id: download_dataset
                       type: io.kestra.plugin.core.http.Download
                       uri: "{{ inputs.dataset_url }}"

                     - id: c_code
                       type: io.kestra.plugin.scripts.shell.Commands
                       taskRunner:
                         type: io.kestra.plugin.scripts.runner.docker.Docker
                       containerImage: gcc:latest
                       commands:
                         - gcc example.c
                         - ./a.out
                       inputFiles:
                         orders.csv: "{{ outputs.download_dataset.uri }}"
                         example.c: |
                           #include <stdio.h>
                           #include <stdlib.h>
                           #include <string.h>

                           int main() {
                               FILE *file = fopen("orders.csv", "r");
                               if (!file) {
                                   printf("Error opening file!\\n");
                                   return 1;
                               }

                               char line[1024];
                               double total_revenue = 0.0;

                               fgets(line, 1024, file);
                               while (fgets(line, 1024, file)) {
                                   char *token = strtok(line, ",");
                                   int i = 0;
                                   double total = 0.0;

                                   while (token) {
                                       if (i == 6) {
                                           total = atof(token);
                                           total_revenue += total;
                                       }
                                       token = strtok(NULL, ",");
                                       i++;
                                   }
                               }

                               fclose(file);
                               printf("Total Revenue: $%.2f\\n", total_revenue);

                               return 0;
                           }
                   """
        ),
        @Example(
            full = true,
            title = """
            If you want to use an input file's absolute path within the current task's working directory, \
            you can leverage the `{{ workingDir }}` variable.
            """,
            code = """
                   id: shell_commands_example
                   namespace: company.team

                   tasks:
                     - id: generator_shell_commands_task
                       type: io.kestra.plugin.scripts.shell.Commands
                       outputFiles:
                         - out.txt
                       commands:
                         - echo "Test" > out.txt

                     - id: reader_shell_commands_task
                       type: io.kestra.plugin.scripts.shell.Commands
                       inputFiles:
                         generated.txt: "{{ outputs.generator_shell_commands_task.outputFiles['out.txt'] }}"
                       commands:
                         - >
                           echo "Input's absolute path: '{{ workingDir }}/generated.txt'"
                   """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ubuntu";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Shell commands to run."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(commands)
            .withTargetOS(os)
            .run();
    }
}
