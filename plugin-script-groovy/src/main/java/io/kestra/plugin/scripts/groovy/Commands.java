package io.kestra.plugin.scripts.groovy;

import java.util.List;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;

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
    title = "Execute Groovy files and commands",
    description = """
        Runs Groovy commands or files in the JVM and captures their output.

        On the Docker task runner, the container runs as `root` unless `taskRunner.user` is set explicitly, so the task can always write into its working directory (which `outputFiles` requires). Set `taskRunner.user` to keep the image's own default user instead."""
)
@Plugin(
    examples = {
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
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "groovy";

    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run"
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
        // Same root default as run(), for the deprecated `docker` property path - see #411.
        if (original.getUser() == null) {
            builder.user("root");
        }
        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        CommandsWrapper commandsWrapper = this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(commands)
            .withTargetOS(os);

        // A non-root image user may not own the working directory, breaking outputFiles - see #411.
        TaskRunner<?> taskRunner = commandsWrapper.getTaskRunner();
        if (taskRunner instanceof Docker docker && docker.getUser() == null) {
            commandsWrapper = commandsWrapper.withTaskRunner(docker.toBuilder().user("root").build());
        }

        return commandsWrapper.run();
    }
}