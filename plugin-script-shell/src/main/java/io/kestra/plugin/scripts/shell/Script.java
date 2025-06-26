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
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Shell script."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an inline Shell script and execute it.",
            full = true,
            code = """
                id: shell_script_example
                namespace: company.team

                tasks:
                  - id: http_download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: shell_script_task
                    type: io.kestra.plugin.scripts.shell.Script
                    outputFiles:
                      - first.txt
                    script: |
                      echo "The current execution is : {{ execution.id }}"
                      echo "1" >> first.txt
                      cat {{ outputs.http_download.uri }}"""
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{ outputDir }}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{ outputs.yourTaskId.outputFiles['yourFileName.fileExtension'] }}`.
            """,
            code = """
                id: shell_script_example
                namespace: company.team

                tasks:
                  - id: hello
                    type: io.kestra.plugin.scripts.shell.Script
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    outputFiles:
                      - hello.txt
                    script: |
                      echo "Hello world!" > hello.txt"""
        ),
        @Example(
            full = true,
            title = """
            If you want to use an input file's absolute path within the current task's working directory, \
            you can leverage the `{{ workingDir }}` variable.
            """,
            code = """
                   id: shell_script_example
                   namespace: company.team

                   tasks:
                     - id: generator_shell_script_task
                       type: io.kestra.plugin.scripts.shell.Script
                       outputFiles:
                         - out.txt
                       script: |
                         echo "Test" > out.txt

                     - id: reader_shell_script_task
                       type: io.kestra.plugin.scripts.shell.Script
                       inputFiles:
                         generated.txt: "{{ outputs.generator_shell_script_task.outputFiles['out.txt'] }}"
                       script: |
                         echo "Input's absolute path: '{{ workingDir }}/generated.txt'"
                   """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ubuntu";

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @NotNull
    protected Property<String> script;

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
        CommandsWrapper commandsWrapper = this.commands(runContext);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return commandsWrapper
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.of(List.of(commandsWrapper.render(runContext, this.script))))
            .withTargetOS(os)
            .run();
    }
}
