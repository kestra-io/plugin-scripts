package io.kestra.plugin.scripts.julia;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run inline Julia script",
    description = "Executes a multi-line Julia script inside the default 'julia' image unless overridden. Script is written to a temp .jl file and run with 'julia'; install required packages via beforeCommands. Use the Commands task to run existing files."
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
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "julia";

    @Schema(
        title = "Container image for Julia runtime",
        description = "Docker image used to run the script; defaults to 'julia'. Include dependencies or install them in beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline Julia script",
        description = "Julia source as a multi-line string; saved to a temporary .jl file and executed with 'julia'. For existing files, use the Commands task."
    )
    @NotNull
    protected Property<String> script;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".jl"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return commands
            .withTargetOS(os)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                String.join(" ", "julia", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .run();
    }
}
