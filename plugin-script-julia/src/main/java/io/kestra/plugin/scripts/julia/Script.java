package io.kestra.plugin.scripts.julia;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Julia script."
)
@Plugin(examples = {
    @Example(
        title = "Create a julia script and execute it",
        code = {
            "script: |",
            "  const colors = require(\"colors\");",
            "  console.log(colors.red(\"Hello\"));",
        }
    )
})
public class Script extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("julia")
        .build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    protected String script;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Path path = runContext.tempFile(
            runContext.render(this.script, commands.getAdditionalVars()).getBytes(StandardCharsets.UTF_8),
            ".js"
        );

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "julia", path.toAbsolutePath().toString())
        );

        return commands
            .addEnv(Map.of("PYTHONUNBUFFERED", "true"))
            .withCommands(commandsArgs)
            .run();
    }
}
