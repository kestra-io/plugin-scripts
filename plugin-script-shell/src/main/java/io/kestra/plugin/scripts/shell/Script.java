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
import jakarta.validation.constraints.NotEmpty;
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
            code = {
                "script: |",
                "  echo \"The current execution is : {{ execution.id }}\"",
                "  echo \"1\" >> {{ outputDir }}/first.txt",
                "  cat {{ outputs.previousTaskId.uri }}"
            }
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{ outputDir }}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{ outputs.yourTaskId.outputFiles['yourFileName.fileExtension'] }}`.
            """,
            code = """
                id: shell
                namespace: company.team
                tasks:
                  - id: hello
                    type: io.kestra.plugin.scripts.shell.Script
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    script: |
                      echo "Hello world!" > {{ outputDir }}/hello.txt"""
        )        
    }
)
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "ubuntu";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String script;

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
            getBeforeCommandsWithOptions(),
            this.script,
            this.targetOS
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
