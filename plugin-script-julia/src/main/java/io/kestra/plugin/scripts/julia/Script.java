package io.kestra.plugin.scripts.julia;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
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
        full = true,
        title = "Create a Julia script, install required packages and execute it. Note that instead of defining the script inline, you could create the Julia script in the embedded VS Code editor and read its content using the `{{ read('your_script.jl') }}` function.",
        code = """
            id: julia_script
            namespace: company.team
            
            tasks:
              - id: script
                type: io.kestra.plugin.scripts.julia.Script
                warningOnStdErr: false
                script: |
                  using DataFrames, CSV
                  df = DataFrame(Name = ["Alice", "Bob", "Charlie"], Age = [25, 30, 35])
                  CSV.write("output.csv", df)
                outputFiles:
                  - output.csv
                beforeCommands:
                  - julia -e 'using Pkg; Pkg.add("DataFrames"); Pkg.add("CSV")'
            """
    )
})
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "julia";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command such as `julia myscript.jl`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String script;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        List<String> internalToLocalFiles = new ArrayList<>();
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".jl"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script, internalToLocalFiles)
        );
        commands = commands.withInputFiles(inputFiles);

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            String.join(" ", "julia", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), this.targetOS)),
            this.targetOS
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
