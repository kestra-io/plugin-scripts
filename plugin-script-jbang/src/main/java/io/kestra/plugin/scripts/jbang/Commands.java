package io.kestra.plugin.scripts.jbang;

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
    title = "Execute JBang files and commands.",
    description = "Executes provided JBang commands (e.g., scripts, JARs) in order using the default 'jbangdev/jbang-action' image unless overridden. Supports beforeCommands for setup and uses inputFiles for staging sources; choose this task to run existing JBang files instead of inline scripts."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute JBang command to execute a JAR file.",
            full = true,
            code = """
                id: jbang_commands
                namespace: company.team

                tasks:
                  - id: commands
                    type: io.kestra.plugin.scripts.jbang.Commands
                    commands:
                      - jbang --quiet --main picocli.codegen.aot.graalvm.ReflectionConfigGenerator info.picocli:picocli-codegen:4.6.3
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "jbangdev/jbang-action";

    @Schema(
        title = "Container image for JBang runtime",
        description = "Docker image used to run the commands; defaults to 'jbangdev/jbang-action'. Override only with an image that includes JBang."
    )
    @Builder.Default
    private Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = "List of JBang commands executed in order inside the container."
    )
    @NotNull
    private Property<List<String>> commands;

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
            .withCommands(commands)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withTargetOS(os)
            .run();
    }
}
