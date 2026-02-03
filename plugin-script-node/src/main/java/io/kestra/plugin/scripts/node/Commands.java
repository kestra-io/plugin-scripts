package io.kestra.plugin.scripts.node;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Node.js files and commands.",
    description = "Executes provided Node.js commands in order using the default 'node' image unless overridden. Supports inputFiles and beforeCommands to stage sources and install npm packages; enable namespaceFiles if referencing files stored in the Namespace — useful for running existing scripts rather than inline code."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Install required npm packages, create a Node.js script and execute it.",
        code = """
            id: nodejs_commands
            namespace: company.team

            tasks:
              - id: commands
                type: io.kestra.plugin.scripts.node.Commands
                inputFiles:
                  main.js: |
                    const colors = require("colors");
                    console.log(colors.red("Hello"));
                beforeCommands:
                  - npm install colors
                commands:
                  - node main.js
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "node";

    @Schema(
        title = "Container image for Node.js runtime",
        description = "Docker image used to run the commands; defaults to 'node'. Include required tooling or install via beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = "List of Node.js commands executed in order inside the container; combine with beforeCommands for npm install and inputFiles/namespaceFiles to stage code."
    )
    @NotNull
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
