package io.kestra.plugin.scripts.dotnet;

import java.util.Collections;
import java.util.List;

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
    title = "Run shell commands inside a .NET SDK container.",
    description = """
        Executes arbitrary shell commands sequentially inside a .NET SDK container (`mcr.microsoft.com/dotnet/sdk:10.0` by default).
        Use this task when you have existing `.csx` script files stored in namespace files or cloned from a Git repository,
        or when you need to run `dotnet` CLI commands directly (e.g., `dotnet build`, `dotnet test`).
        For inline C# scripts, prefer the `Script` task instead.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Run a .csx script file using dotnet-script inside a .NET SDK container.",
            code = """
                id: dotnet_commands
                namespace: company.team

                tasks:
                  - id: run_dotnet
                    type: io.kestra.plugin.scripts.dotnet.Commands
                    inputFiles:
                      analyze.csx: |
                        Console.WriteLine("Analyzing data...");
                        Console.WriteLine($"Current time: {DateTime.UtcNow}");
                    beforeCommands:
                      - dotnet tool install -g dotnet-script --ignore-failed-sources || true
                      - export PATH="$PATH:$HOME/.dotnet/tools"
                    commands:
                      - dotnet-script analyze.csx
                """
        ),
        @Example(
            full = true,
            title = "Run dotnet CLI commands to inspect the .NET SDK version.",
            code = """
                id: dotnet_build_and_test
                namespace: company.team

                tasks:
                  - id: build
                    type: io.kestra.plugin.scripts.dotnet.Commands
                    commands:
                      - dotnet --version
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/dotnet/sdk:10.0";

    @Schema(
        title = "Container image for the .NET runtime.",
        description = "Docker image used to run the commands. Defaults to `mcr.microsoft.com/dotnet/sdk:10.0`. Use a custom image pre-installed with `dotnet-script` or other tools to skip manual setup in `beforeCommands`."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Shell commands to execute.",
        description = """
            List of shell commands executed in order inside the .NET SDK container.
            Combine with `beforeCommands` for environment setup (e.g., installing `dotnet-script`)
            and with `inputFiles` or `namespaceFiles` to stage `.csx` or other script files.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(DEFAULT_IMAGE));
        }
        if (original.getEntryPoint() == null) {
            builder.entryPoint(Collections.emptyList());
        }
        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(commands)
            .withTargetOS(os)
            .run();
    }
}
