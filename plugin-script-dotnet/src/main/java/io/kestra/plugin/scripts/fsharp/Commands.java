package io.kestra.plugin.scripts.fsharp;

import java.util.Collections;
import java.util.List;

import io.kestra.plugin.scripts.dotnet.AbstractDotnetCommands;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run commands inside a .NET SDK container",
    description = """
        Executes arbitrary shell commands sequentially inside a .NET SDK container
        (`mcr.microsoft.com/dotnet/sdk:10.0` by default).

        Use this task when you need to run `dotnet` CLI commands directly or execute
        an existing F# `.fsx` script file with `dotnet fsi`.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Run an F# script file using dotnet fsi.",
            code = """
                id: fsharp_commands
                namespace: company.team

                tasks:
                  - id: run_fsharp
                    type: io.kestra.plugin.scripts.fsharp.Commands
                    inputFiles:
                      analyze.fsx: |
                        printfn "Analyzing data..."
                        printfn "Hello from F# commands!"
                    commands:
                      - dotnet fsi analyze.fsx
                """
        ),
        @Example(
            full = true,
            title = "Run a .NET CLI command.",
            code = """
                id: dotnet_version
                namespace: company.team

                tasks:
                  - id: version
                    type: io.kestra.plugin.scripts.fsharp.Commands
                    commands:
                      - dotnet --version
                """
        )
    }
)
public class Commands extends AbstractDotnetCommands {

    @Schema(
        title = "Shell commands to execute",
        description = """
            List of shell commands executed in order inside the .NET SDK container.
            Use `dotnet fsi` to execute existing F# `.fsx` files.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var os = runContext.render(this.targetOS)
            .as(TargetOS.class)
            .orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(this.beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(this.commands)
            .withTargetOS(os)
            .run();
    }
}