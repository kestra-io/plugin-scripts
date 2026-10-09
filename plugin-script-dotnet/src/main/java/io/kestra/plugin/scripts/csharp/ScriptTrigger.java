package io.kestra.plugin.scripts.csharp;

import java.time.Instant;
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
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on C# script condition",
    description = """
        Polls by running an inline C# script in a .NET SDK container
        and emits when exitCondition matches.

        The common polling, edge-state, condition matching, and failure
        handling behavior is provided by AbstractDotnetTrigger.
        """
)
@Plugin(
    aliases = "io.kestra.plugin.scripts.dotnet.ScriptTrigger",
    examples = {
        @Example(
            title = "Trigger when the C# script fails with an implicit error (exit 1).",
            full = true,
            code = """
                id: script_trigger
                namespace: company.team

                triggers:
                  - id: script_failure
                    type: io.kestra.plugin.scripts.csharp.ScriptTrigger
                    interval: PT60S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: mcr.microsoft.com/dotnet/sdk:10.0
                    script: |
                      System.Environment.Exit(1);

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
public class ScriptTrigger
    extends AbstractDotnetTrigger<ScriptTrigger.Output> {

    @Schema(
        title = "Inline C# script",
        description = """
            Multi-line C# script executed on each poll.
            The script is executed using the C# Script task.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> script;

    @Override
    protected ScriptOutput runTask(
        RunContext runContext
    ) throws Exception {

        Script task = Script.builder()
            .id(this.getId())
            .type(Script.class.getName())
            .containerImage(this.containerImage)
            .targetOS(this.targetOS)
            .script(this.script)
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
            super(timestamp, condition, exitCode, vars);
        }
    }
}