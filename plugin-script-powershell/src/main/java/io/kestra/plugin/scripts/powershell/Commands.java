package io.kestra.plugin.scripts.powershell;

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

import java.util.Collections;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more PowerShell commands. Note that instead of adding the script using the `inputFiles` property, you could also add the script from the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the `enabled` flag of the `namespaceFiles` property to `true`."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a PowerShell script and execute it.",
        code = """
            id: powershell
            namespace: dev
            tasks:
              - id: powershell_script
                type: io.kestra.plugin.scripts.powershell.Commands
                inputFiles:
                  main.ps1: |
                    Get-ChildItem | Format-List
                commands:
                  - pwsh main.ps1
            """
    )
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/powershell";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use"
    )
    @PluginProperty
    @NotEmpty
    protected List<String> interpreter = List.of("/bin/sh", "-c");

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(DEFAULT_IMAGE);
        }
        if (original.getEntryPoint() == null) {
            builder.entryPoint(Collections.emptyList());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            this.commands
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
