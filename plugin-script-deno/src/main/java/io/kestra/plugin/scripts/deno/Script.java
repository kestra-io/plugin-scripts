package io.kestra.plugin.scripts.deno;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;

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
    title = "Run inline Deno script",
    description = "Executes a multi-line Deno script inside the default 'denoland/deno' image unless overridden. Script is written to a temp .ts file and run with 'deno run'; the `permissions` property controls which --allow-* flags are granted."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a simple inline Deno script.",
            full = true,
            code = """
                id: deno_inline
                namespace: company.team
                tasks:
                  - id: deno_script
                    type: io.kestra.plugin.scripts.deno.Script
                    script: |
                      console.log("Hello from kestra!");
                """
        ),
        @Example(
            title = "Read an environment variable and write an output file from an inline Deno script.",
            full = true,
            code = """
                id: deno_env_and_output_file
                namespace: company.team
                tasks:
                  - id: deno_script
                    type: io.kestra.plugin.scripts.deno.Script
                    env:
                      MY_VAR: hello
                    outputFiles:
                      - out.txt
                    script: |
                      const value = Deno.env.get("MY_VAR");
                      console.log("ENV=" + value);
                      Deno.writeTextFileSync("out.txt", value ?? "");
                """
        ),
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "denoland/deno";

    @Schema(
        title = "Container image for Deno runtime",
        description = "Docker image used to run the script; defaults to 'denoland/deno'. Include dependencies or install them in beforeCommands."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline Deno script",
        description = "Script content written inline; saved as a temporary .ts file and executed with 'deno run'."
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.TYPESCRIPT, group = "main")
    protected Property<String> script;

    @Schema(
        title = "Deno permission flags",
        description = """
            Flags passed to `deno run` to grant the script access to the outside world, e.g. `--allow-net`, `--allow-all`.
            Defaults to `--allow-env`, `--allow-read` and `--allow-write` so that the `env`, `inputFiles` and `outputFiles` \
            properties work the same way as on every other script task. Set to an empty list to run under Deno's \
            secure-by-default sandbox with no permissions at all."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<List<String>> permissions = Property.ofValue(List.of("--allow-env", "--allow-read", "--allow-write"));

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

        List<String> rPermissions = runContext.render(this.permissions).asList(String.class);
        List<String> denoCommand = new ArrayList<>(List.of("deno", "run"));
        denoCommand.addAll(rPermissions);
        denoCommand.add(commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os));

        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(
                Property.ofValue(
                    List.of(String.join(" ", denoCommand))
                )
            )
            .withTargetOS(os)
            .run();
    }
}
