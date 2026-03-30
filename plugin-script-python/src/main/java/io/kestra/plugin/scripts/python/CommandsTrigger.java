package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
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
    title = "Trigger on Python commands condition.",
    description = """
        Polls by running Python commands (default image python:3.13-slim) and emits when exitCondition matches.
        Supports edge mode to emit only on transitions and polls every 60s by default.
        Accepts 'exit N' or a regex (fallback substring) matched against emitted vars and failure logs.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the Python command fails with exit code 1.",
            full = true,
            code = """
                id: python_commands_trigger
                namespace: company.team

                triggers:
                  - id: on_fail
                    type: io.kestra.plugin.scripts.python.CommandsTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: python:3.13-slim
                    commands:
                      - python3 -c "raise Exception('boom')"

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
public class CommandsTrigger extends AbstractPythonTrigger {

    @Schema(
        title = "Docker image used to execute the commands.",
        description = "Container image used by the underlying Commands task to run Python commands.\n" +
            "Defaults to '" + DEFAULT_IMAGE + "'."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Python commands to execute.",
        description = "Commands executed on each poll (same semantics as the Python Commands task)."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Override
    protected ScriptOutput executeTask(RunContext runContext) throws Exception {
        Commands task = Commands.builder()
            .taskRunner(Process.instance())
            .containerImage(this.containerImage)
            .commands(this.commands)
            .build();

        return task.run(runContext);
    }
}
