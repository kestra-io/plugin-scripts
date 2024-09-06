package io.kestra.plugin.scripts.julia;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Julia scripts from the Command Line Interface."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a Julia script, install required packages and execute it. Note that instead of defining the script inline, you could create the Julia script in the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the `enabled` flag of the `namespaceFiles` property to `true`.",
        code = """
            id: julia_commands
            namespace: company.team

            tasks:
              - id: commands
                type: io.kestra.plugin.scripts.julia.Commands
                warningOnStdErr: false
                inputFiles:
                  main.jl: |
                    using DataFrames, CSV
                    df = DataFrame(Name = ["Alice", "Bob", "Charlie"], Age = [25, 30, 35])
                    CSV.write("output.csv", df)
                outputFiles:
                  - output.csv
                beforeCommands:
                  - julia -e 'using Pkg; Pkg.add("DataFrames"); Pkg.add("CSV")'
                commands:
                  - julia main.jl
            """
    )
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "julia";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "The commands to run."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

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
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            this.commands,
            this.targetOS
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
