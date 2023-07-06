package io.kestra.plugin.scripts.python;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Python script."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a python script",
            code = {
                "script: |",
                "  from kestra import Kestra",
                "  import requests",
                "",
                "  response = requests.get('https://google.com')",
                "  print(response.status_code)",
                "",
                "  Kestra.outputs({'status': response.status_code, 'text': response.text})",
                "beforeCommands:",
                "  - pip install requests kestra"
            }
        ),
        @Example(
            title = "Execute a python script with an input file from Kestra's local storage created by a previous task.",
            code = {
                "script:",
                "  with open('{{ outputs.previousTaskId.uri }}', 'r') as f:",
                "    print(f.read())"
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
        .image("python")
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
            ".py"
        );

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "python", path.toAbsolutePath().toString())
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
