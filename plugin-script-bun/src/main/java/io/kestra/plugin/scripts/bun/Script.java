package io.kestra.plugin.scripts.bun;

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
    title = "Execute a Bun script."
)
@Plugin(examples = {
    @Example(
        title = "Create a Bun script and execute it.",
        full = true,
        code = """
            id: bun_script
            namespace: company.team

            tasks:
              - id: script
                type: io.kestra.plugin.scripts.bun.Script
                script: |
                  console.log("Hello, World!");
            """
    ),
    @Example(
        title = "Fetch data from an API and save it to a file.",
        full = true,
        code = """
            id: bun_api_fetch
            namespace: company.team

            tasks:
              - id: bun_script
                type: io.kestra.plugin.scripts.bun.Script
                outputFiles:
                  - users.json
                script: |
                  import { writeFileSync } from "fs";

                  const response = await fetch("https://jsonplaceholder.typicode.com/users");
                  const data = await response.json();

                  writeFileSync("users.json", JSON.stringify(data, null, 2));
                  console.log("Successfully fetched users and created users.json");
            """
    ),

})
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "oven/bun";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file."
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
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".ts"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);
        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                String.join(" ", "bun", "run", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .withTargetOS(os)
            .run();
    }
}