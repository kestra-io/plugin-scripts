package io.kestra.plugin.scripts.zx;

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
    title = "Run inline zx script",
    description = "Runs JavaScript shell automation with zx. The default image includes zx; custom images and Process runners require Node.js, zx and a supported shell."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Run shell commands with zx",
            code = """
                id: zx_script
                namespace: company.team

                tasks:
                  - id: zx
                    type: io.kestra.plugin.scripts.zx.Script
                    script: |
                      const name = 'Kestra';
                      await $`echo Hello ${name}`;
                """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ghcr.io/google/zx:8.5.0";
    private static final String DEFAULT_EXECUTABLE = "node /zx/build/cli.js";

    @Schema(
        title = "Container image for zx runtime",
        description = "Docker image used to run the script; defaults to 'ghcr.io/google/zx:8.5.0'. Custom images must provide Node.js, zx and a supported shell."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline zx script",
        description = "JavaScript source as a multi-line string; saved to a temporary .mjs file and executed with the configured zx command. For existing files, use the Commands task."
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.JAVASCRIPT, group = "main")
    protected Property<String> script;

    @Schema(
        title = "Command used to launch zx",
        description = "Defaults to `node /zx/build/cli.js` for the bundled CLI in the default image. Set to `zx` when using a custom image or Process runner with zx installed on PATH."
    )
    @Builder.Default
    @NotNull
    @PluginProperty(group = "execution")
    protected Property<String> executable = Property.ofValue(DEFAULT_EXECUTABLE);

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
        var commands = this.commands(runContext);

        var inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        var relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".mjs"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        var rTargetOS = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);
        var rExecutable = runContext.render(this.executable).as(String.class).orElse(DEFAULT_EXECUTABLE);
        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(
                Property.ofValue(
                    List.of(
                        String.join(
                            " ", rExecutable,
                            commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), rTargetOS)
                        )
                    )
                )
            )
            .withTargetOS(rTargetOS)
            .run();
    }
}
