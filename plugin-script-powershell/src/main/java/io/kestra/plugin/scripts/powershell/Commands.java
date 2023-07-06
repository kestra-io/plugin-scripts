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

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more PowerShell commands."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a PowerShell script and execute it",
        code = """
            id: "local-files"
            namespace: "io.kestra.tests"

            tasks:
              - id: workingDir
                type: io.kestra.core.tasks.flows.WorkingDirectory
                tasks:
                - id: inputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  inputs:
                    main.ps1: |
                      Get-ChildItem | Format-List
                - id: bash
                  type: io.kestra.plugin.scripts.powershell.Commands
                  commands:
                    - pwsh main.ps1
            """
    )
})
public class Commands extends AbstractExecScript {
    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected List<String> commands;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use"
    )
    @PluginProperty
    @NotNull
    @NotEmpty
    protected List<String> interpreter = List.of("/bin/sh", "-c");

    @Override
    protected DockerOptions defaultDockerOptions() {
        return DockerOptions.builder()
            .image("mcr.microsoft.com/powershell")
            .entryPoint(List.of())
            .build();
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
