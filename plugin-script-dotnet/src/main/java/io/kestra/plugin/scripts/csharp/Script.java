package io.kestra.plugin.scripts.csharp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.enums.MonacoLanguages;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.dotnet.AbstractDotnetScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Run an inline C# script using dotnet-script",
    description = """
        Executes a multi-line C# script (`.csx` format) inside a .NET SDK container using `dotnet-script`.
        The script is written to a temporary `.csx` file and run as a shell command (`dotnet-script ./script.csx`).
        NuGet package references via `#r "nuget:PackageName,Version"` work out of the box.

        `dotnet-script` is installed automatically on first run via `dotnet tool install -g dotnet-script`.
        The initial NuGet restore on first use may take 30–60 seconds depending on network conditions.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Run a simple C# Hello World script.",
            full = true,
            code = """
                id: dotnet_hello_world
                namespace: company.team

                tasks:
                  - id: hello_csharp
                    type: io.kestra.plugin.scripts.csharp.Script
                    script: |
                      Console.WriteLine("Hello from Kestra!");
                """
        ),
        @Example(
            title = "Run an inline C# script with a NuGet dependency.",
            full = true,
            code = """
                id: dotnet_inline_script
                namespace: company.team

                tasks:
                  - id: hello_csharp
                    type: io.kestra.plugin.scripts.csharp.Script
                    script: |
                      #r "nuget:Newtonsoft.Json,13.0.3"
                      using Newtonsoft.Json;
                      var data = new { message = "Hello from Kestra", timestamp = DateTime.UtcNow };
                      Console.WriteLine(JsonConvert.SerializeObject(data));
                """
        ),

        @Example(
            full = true,
            title = """
        Generate output files from a C# script. Files declared in `outputFiles` are persisted \
        in Kestra's internal storage and accessible to downstream tasks via \
        `{{ outputs.write_file.outputFiles['hello.txt'] }}`.
        """,
            code = """
        id: dotnet_generate_files
        namespace: company.team

        tasks:
          - id: write_file
            type: io.kestra.plugin.scripts.csharp.Script
            outputFiles:
              - hello.txt
            script: |
              File.WriteAllText("hello.txt", "Hello from dotnet-script!");
        """
        )
    },
    aliases = "io.kestra.plugin.scripts.dotnet.Script"
)
public class Script extends AbstractDotnetScript {

    private static final List<String> DOTNET_SCRIPT_INSTALL = List.of(
        "dotnet tool install -g dotnet-script --ignore-failed-sources || true",
        "export PATH=\"$PATH:$HOME/.dotnet/tools\""
    );

    @Schema(
        title = "Inline C# script to execute",
        description = """
            C# script body in `.csx` format. Written to a temporary `.csx` file and executed with `dotnet-script`.
            NuGet packages can be referenced with `#r "nuget:PackageName,Version"` at the top of the script.
            """
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.CSHARP, group = "main")
    protected Property<String> script;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var commands = this.commands(runContext);

        var inputFiles = FilesService.inputFiles(
            runContext,
            commands.getTaskRunner().additionalVars(runContext, commands),
            this.getInputFiles()
        );

        Path relativeScriptPath = runContext.workingDir().path()
            .relativize(runContext.workingDir().createTempFile(".csx"));

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
            .withBeforeCommands(buildBeforeCommands(runContext))
            .withBeforeCommandsWithOptions(true)
            .withCommands(
                Property.ofValue(
                    List.of(
                        "dotnet-script "
                            + commands.getTaskRunner().toAbsolutePath(
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

    private Property<List<String>> buildBeforeCommands(
        RunContext runContext
    ) throws IllegalVariableEvaluationException {

        var userBefore =
            runContext.render(this.getBeforeCommands()).asList(String.class);

        var combined = new ArrayList<>(DOTNET_SCRIPT_INSTALL);
        combined.addAll(userBefore);

        return Property.ofValue(combined);
    }
}