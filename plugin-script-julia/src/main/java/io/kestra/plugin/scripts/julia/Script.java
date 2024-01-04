package io.kestra.plugin.scripts.julia;

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
import java.util.Map;
import jakarta.validation.constraints.NotNull;

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
            id: "script"
            namespace: "dev"
            tasks:
              - id: bash
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

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command such as `julia myscript.jl`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected String script;

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
            ".js"
        );

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "julia", path.toAbsolutePath().toString())
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
