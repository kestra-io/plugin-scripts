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
            title = "Run a shell inline script.",
            code = {
                "script: |",
                "  echo \"The current execution is : {{ execution.id }}\"",
                "  echo \"1\" >> {{ outputDir }}/first.txt",
                "  cat {{ outputs.previousTaskId.uri }}"
            }
        )
    }
)
public class Script extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("ubuntu")
        .build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    protected String script;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            this.script
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
