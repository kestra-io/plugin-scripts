package io.kestra.plugin.scripts.go;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Go scripts from the CLI."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a Go script, install required packages and execute it. Note that instead of defining the script inline, you could create the Go script in the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the `enabled` flag of the `namespaceFiles` property to `true`.",
        code = """
            id: go_commands
            namespace: company.team

            tasks:
              - id: commands
                type: io.kestra.plugin.scripts.go.Commands
                allowWarning: true # cause golang redirect ALL to stderr even false positives
                inputFiles:
                    go_script.go: |
                        package main
                        import (
                            "os"
                            "github.com/go-gota/gota/dataframe"
                            "github.com/go-gota/gota/series"
                        )
                        func main() {
                            names := series.New([]string{"Alice", "Bob", "Charlie"}, series.String, "Name")
                            ages := series.New([]int{25, 30, 35}, series.Int, "Age")
                            df := dataframe.New(names, ages)
                            file, _ := os.Create("output.csv")
                            df.WriteCSV(file)
                            defer file.Close()
                        }
                outputFiles:
                  - output.csv
                beforeCommands:
                  - go mod init go_commands
                  - go get github.com/go-gota/gota/dataframe
                  - go mod tidy
                commands:
                  - go run go_script.go
            """
    )
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "golang";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, @NotNull DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(commands)
            .withTargetOS(os)
            .run();
    }
}
