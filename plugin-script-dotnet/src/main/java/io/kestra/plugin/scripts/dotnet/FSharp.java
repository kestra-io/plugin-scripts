package io.kestra.plugin.scripts.dotnet;

import java.util.List;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.enums.MonacoLanguages;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.FilesService;
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
    title = "Run an inline F# script",
    description = """
        Executes a multi-line F# script (`.fsx`) inside a .NET SDK container using `dotnet fsi`.
        The script is written to a temporary `.fsx` file and executed with F# Interactive.
        NuGet package references via `#r "nuget:PackageName,Version"` are supported.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Run a simple F# Hello World script.",
            full = true,
            code = """
                id: fsharp_hello_world
                namespace: company.team

                tasks:
                  - id: hello_fsharp
                    type: io.kestra.plugin.scripts.dotnet.FSharp
                    script: |
                      printfn "Hello from Kestra!"
                """
        ),
        @Example(
            title = "Run an inline F# script with a NuGet dependency.",
            full = true,
            code = """
                id: fsharp_nuget
                namespace: company.team

                tasks:
                  - id: hello_fsharp
                    type: io.kestra.plugin.scripts.dotnet.FSharp
                    script: |
                      #r "nuget: Newtonsoft.Json, 13.0.3"

                      open Newtonsoft.Json

                      let data = {| message = "Hello from Kestra" |}
                      printfn "%s" (JsonConvert.SerializeObject(data))
                """
        )
    }
)
public class FSharp extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/dotnet/sdk:10.0";

    @Schema(
        title = "Container image for the .NET runtime",
        description = "Docker image used to run the F# script. Defaults to `mcr.microsoft.com/dotnet/sdk:10.0`."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline F# script to execute",
        description = """
            F# script body in `.fsx` format.
            The script is written to a temporary `.fsx` file and executed with `dotnet fsi`.
            NuGet packages can be referenced with `#r "nuget:PackageName,Version"`.
            """
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.FSHARP, group = "main")
    protected Property<String> script;

    @Override
    protected DockerOptions injectDefaults(
        RunContext runContext,
        DockerOptions original
    ) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();

        if (original.getImage() == null) {
            builder.image(
                runContext.render(this.getContainerImage())
                    .as(String.class)
                    .orElse(DEFAULT_IMAGE)
            );
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var commands = this.commands(runContext);

        var inputFiles = FilesService.inputFiles(
            runContext,
            commands.getTaskRunner().additionalVars(runContext, commands),
            this.getInputFiles()
        );

        var relativeScriptPath = runContext.workingDir()
            .path()
            .relativize(runContext.workingDir().createTempFile(".fsx"));

        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );

        commands = commands.withInputFiles(inputFiles);

        var os = runContext.render(this.targetOS)
            .as(TargetOS.class)
            .orElse(null);

        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(this.beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(
                Property.ofValue(
                    List.of(
                        "dotnet fsi " +
                            commands.getTaskRunner().toAbsolutePath(
                                runContext,
                                commands,
                                "./" + relativeScriptPath,
                                os
                            )
                    )
                )
            )
            .withTargetOS(os)
            .run();
    }
}