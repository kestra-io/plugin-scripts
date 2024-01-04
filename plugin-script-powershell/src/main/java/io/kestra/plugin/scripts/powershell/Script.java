package io.kestra.plugin.scripts.powershell;

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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a PowerShell script."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "script: |",
                "  'Hello, World!' | Write-Output"
            }
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{outputDir}}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{outputs.yourTaskId.outputFiles['yourFileName.fileExtension']}}`.
            """,
            code = """
                id: powershell
                namespace: dev
                tasks:
                  - id: hello
                    type: io.kestra.plugin.scripts.powershell.Script
                    script: |
                      Set-Content -Path {{outputDir}}\\hello.txt -Value "Hello World"
                """
        )           
    }
)
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/powershell";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected String script;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use"
    )
    @PluginProperty
    @NotEmpty
    protected List<String> interpreter = List.of("pwsh", "-NoProfile", "-NonInteractive", "-Command");

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
        CommandsWrapper commands = this.commands(runContext);

        Path path = runContext.tempFile(
            ScriptService.replaceInternalStorage(runContext, runContext.render(this.script, commands.getAdditionalVars())).getBytes(StandardCharsets.UTF_8),
            ".ps1"
        );

        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            path.toAbsolutePath().toString()
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
