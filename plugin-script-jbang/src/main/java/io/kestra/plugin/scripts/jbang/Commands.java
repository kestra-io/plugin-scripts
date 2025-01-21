package io.kestra.plugin.scripts.jbang;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.ScriptService;
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
import jakarta.validation.constraints.NotEmpty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more JBang commands."
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
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "jbangdev/jbang-action";

    @Builder.Default
    private Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "JBangs commands to run."
    )
    @NotNull
    private Property<List<String>> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage().toString());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var renderedCommands = runContext.render(this.commands).asList(String.class);

        List<String> commandsArgs = ScriptService.scriptCommands(
            runContext.render(this.interpreter).asList(String.class),
            getBeforeCommandsWithOptions(runContext),
            renderedCommands,
            runContext.render(this.targetOS).as(TargetOS.class).orElse(null)
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
