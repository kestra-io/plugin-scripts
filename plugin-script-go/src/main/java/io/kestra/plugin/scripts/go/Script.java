package io.kestra.plugin.scripts.go;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Go script."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a Go script, install required packages and execute it. Note that instead of defining the script inline, you could create the Go script in the embedded VS Code editor and read its content using the `{{ read('go_script.go') }}` function.",
        code = """
            id: go_script
            namespace: company.team

            tasks:
              - id: script
                type: io.kestra.plugin.scripts.go.Script
                allowWarning: true # cause golang redirect ALL to stderr even false positives
                script: |
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
                  - go mod init go_script
                  - go get github.com/go-gota/gota/dataframe
                  - go mod tidy
            """
    )
})
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "golang";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command such as `go run go_script.go`, use the `Commands` task instead."
    )
    @NotNull
    protected Property<String> script;

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
        var commands = this.commands(runContext);

        var inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        var relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".go"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, script)
        );
        commands = commands.withInputFiles(inputFiles);

        var os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return commands
            .withTargetOS(os)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                String.join(" ", "go run", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .run();
    }
}
