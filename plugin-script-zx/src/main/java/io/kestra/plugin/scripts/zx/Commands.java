package io.kestra.plugin.scripts.zx;

import java.util.List;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
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
    title = "Execute zx files and commands",
    description = """
        Runs JavaScript shell automation with zx. The default image includes zx at /zx/build/cli.js; custom
        images and Process runners require Node.js, zx and a supported shell.
        """
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Run shell commands with zx",
            code = """
                id: zx_commands
                namespace: company.team

                tasks:
                  - id: zx
                    type: io.kestra.plugin.scripts.zx.Commands
                    inputFiles:
                      main.mjs: |
                        await $`echo Hello Kestra`;
                    commands:
                      - node /zx/build/cli.js main.mjs
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ghcr.io/google/zx:8.5.0";

    @Schema(
        title = "Container image for zx runtime",
        description = """
            Docker image used to run the commands; defaults to 'ghcr.io/google/zx:8.5.0'. Custom images must
            provide Node.js, zx and a supported shell.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = """
            List of shell commands, such as `node /zx/build/cli.js main.mjs` in the default image, executed in
            order inside the container; combine with beforeCommands for npm install and
            inputFiles/namespaceFiles to stage code.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

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
        var rTargetOS = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(commands)
            .withTargetOS(rTargetOS)
            .run();
    }
}
