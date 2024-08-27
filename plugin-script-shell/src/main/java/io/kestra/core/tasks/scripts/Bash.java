package io.kestra.core.tasks.scripts;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwSupplier;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Bash script, command or set of commands.",
    description = "This task is deprecated, please use the [io.kestra.plugin.scripts.shell.Script](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.shell.script) or [io.kestra.plugin.scripts.shell.Commands](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.shell.commands) task instead.",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            title = "Single bash command.",
            full = true, 
            code = """
                   id: bash_single_command
                   namespace: company.team

                   tasks:
                     - id: bash
                       type: io.kestra.core.tasks.scripts.Bash
                       commands:
                         - 'echo "The current execution is : {{ execution.id }}"'
                   """
        ),
        @Example(
            title = "Bash command that generate file in storage accessible through outputs.",
            full = true,
            code = """
                id: bash_generate_files
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    outputFiles:
                      - first
                      - second
                    commands:
                      - echo "1" >> {{ outputFiles.first }}
                      - echo "2" >> {{ outputFiles.second }}
                """
        ),
        @Example(
            title = "Bash with some inputs files.",
            full = true,
            code = """
                id: bash_input_files
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    inputFiles:
                      script.sh: |
                        echo {{ workingDir }}
                    commands:
                      - /bin/bash script.sh
                """
        ),
        @Example(
            title = "Bash with an input file from Kestra's local storage created by a previous task.",
            full = true,
            code = """
                id: bash_use_input_files                
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    inputFiles:
                      data.csv: {{ outputs.previousTaskId.uri }}
                    commands:
                      - cat data.csv
                """
        ),
        @Example(
            title = "Run a command on a Docker image.",
            full = true,
            code = """
                id: bash_run_php_code
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    runner: DOCKER
                    dockerOptions:
                      image: php
                    commands:
                      - php -r 'print(phpversion() . "\n");'
                """
        ),
        @Example(
            title = "Execute cmd on Windows.",
            full = true,
            code = """
                id: bash_run_cmd_on_windows
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    commands:
                      - 'echo "The current execution is : {{ execution.id }}"'
                    exitOnFailed: false
                    interpreter: cmd
                    interpreterArgs:
                      - /c
                """
        ),
        @Example(
            title = "Set outputs from bash standard output.",
            full = true,
            code = """
                id: bash_set_outputs
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    commands:
                      - echo '::{"outputs":{"test":"value","int":2,"bool":true,"float":3.65}}::'
                """
        ),
        @Example(
            title = "Send a counter metric from bash standard output.",
            full = true,
            code = """
                id: bash_set_metrics
                namespace: company.team
                
                tasks:
                  - id: bash
                    type: io.kestra.core.tasks.scripts.Bash
                    commands:
                      - echo '::{"metrics":[{"name":"count","type":"counter","value":1,"tags":{"tag1":"i","tag2":"win"}}]}::' 
                """
        )
    }
)
@Deprecated
public class Bash extends AbstractBash implements RunnableTask<io.kestra.core.tasks.scripts.ScriptOutput> {
    @Schema(
        title = "The commands to run.",
        description = "Default command will be launched with `/bin/sh -c \"commands\"`."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String[] commands;


    @Override
    public io.kestra.core.tasks.scripts.ScriptOutput run(RunContext runContext) throws Exception {
        return run(runContext, throwSupplier(() -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            // renderer command
            for (String command : this.commands) {
                renderer.add(runContext.render(command, additionalVars));
            }

            return String.join("\n", renderer);
        }));
    }
}
