package io.kestra.plugin.scripts.groovy;

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
    title = "Execute Groovy commands from the CLI."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Execute a Groovy command.",
        code = """
            id: groovy_commands
            namespace: company.team

            tasks:
              - id: commands
                type: io.kestra.plugin.scripts.groovy.Commands
                commands:
                  - groovy --version
            """
    ),
    @Example(
        full = true,
        title = "Run a Groovy script with dependencies managed by Grape.",
        code = """
            id: groovy_commands_with_dependencies
            namespace: company.team

            tasks:
              - id: groovy_commands
                type: io.kestra.plugin.scripts.groovy.Commands
                commands:
                  - |
                    groovy -e '
                      @Grab("info.picocli:picocli:4.7.5")
                      import picocli.CommandLine
                      @CommandLine.Command(name = "hello")
                      class HelloWorld implements Runnable {
                        @CommandLine.Parameters(paramLabel = "NAME", defaultValue = "Kestra")
                        String name
                        void run() {
                           println "Hello, $name!"
                        }
                      }

                      new CommandLine(new HelloWorld()).execute("Kestra")
                    '
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "groovy";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run."
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