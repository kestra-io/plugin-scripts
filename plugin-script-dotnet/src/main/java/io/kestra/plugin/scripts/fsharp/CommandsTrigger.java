package io.kestra.plugin.scripts.fsharp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.dotnet.AbstractDotnetTrigger;
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when F# commands match a condition",
    description = """
        Polls by running F# commands in a .NET SDK container and emits when
        the configured exit condition matches.

        Edge mode and condition evaluation are handled by the shared
        .NET trigger implementation.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when F# commands fail with an implicit error (exit 1).",
            full = true,
            code = """
                id: fsharp_commands_trigger
                namespace: company.team

                triggers:
                  - id: fsharp_commands_failure
                    type: io.kestra.plugin.scripts.fsharp.CommandsTrigger
                    interval: PT60S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: mcr.microsoft.com/dotnet/sdk:10.0
                    commands:
                      - dotnet fsi --exec "System.Environment.Exit(1)"

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
public class CommandsTrigger
    extends AbstractDotnetTrigger<CommandsTrigger.Output> {

    @Schema(
        title = "F# commands to execute",
        description = "Commands executed on each poll (same semantics as the F# Commands task)."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    @Override
    protected ScriptOutput runTask(
        RunContext runContext
    ) throws Exception {

        Commands task = Commands.builder()
            .id(this.getId())
            .type(Commands.class.getName())
            .containerImage(this.containerImage)
            .targetOS(this.targetOS)
            .commands(this.commands)
            .build();

        return task.run(
            TriggerRunContext.forEmbeddedTask(
                runContext,
                task
            )
        );
    }

    @Override
    protected Output createOutput(
        Instant timestamp,
        String condition,
        Integer exitCode,
        Map<String, Object> vars
    ) {
        return new Output(
            timestamp,
            condition,
            exitCode,
            vars
        );
    }

    @EqualsAndHashCode(callSuper = true)
    public static class Output
        extends AbstractDotnetTrigger.Output {

        public Output(
            Instant timestamp,
            String condition,
            Integer exitCode,
            Map<String, Object> vars
        ) {
            super(
                timestamp,
                condition,
                exitCode,
                vars
            );
        }
    }
}