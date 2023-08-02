package io.kestra.plugin.scripts.r;

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
    title = "Execute an R script."
)
@Plugin(
    examples = {
        @Example(
            title = "Install a package and execute a R script",
            code = {
                "script: |",
                "  library(lubridate)",
                "  ymd(\"20100604\");",
                "  mdy(\"06-04-2011\");",
                "  dmy(\"04/06/2012\")",
                "beforeCommands:",
                "  - Rscript -e 'install.packages(\"lubridate\")'"
            }
        ),
    }
)
public class Script extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("r-base")
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
            ScriptService.replaceInternalStorage(runContext, runContext.render(this.script, commands.getAdditionalVars())).getBytes(StandardCharsets.UTF_8),
            ".R"
        );

        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "Rscript", path.toAbsolutePath().toString())
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
