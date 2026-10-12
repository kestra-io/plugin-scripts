package io.kestra.plugin.scripts.fsharp;

import java.time.Instant;
import java.util.Map;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.enums.MonacoLanguages;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.dotnet.AbstractDotnetTrigger;
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
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
    title = "Trigger on F# script condition",
    description = """
        Polls by running an inline F# script in a .NET SDK container
        and emits when the configured exit condition matches.

        F# scripts use the `.fsx` format and are executed with `dotnet fsi`.

        Edge mode and condition evaluation are handled by the shared
        .NET trigger implementation.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the F# script fails with exit code 1.",
            full = true,
            code = """
                id: fsharp_script_trigger
                namespace: company.team

                triggers:
                  - id: fsharp_script_failure
                    type: io.kestra.plugin.scripts.fsharp.ScriptTrigger
                    interval: PT60S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: mcr.microsoft.com/dotnet/sdk:10.0
                    script: |
                      System.Environment.Exit(1)

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
        title = "Inline F# script",
        description = """
            Multi-line F# script executed on each poll.

            The script uses `.fsx` format and is executed with `dotnet fsi`.
            """
    )
    @NotNull
    @PluginProperty(
        language = MonacoLanguages.FSHARP,
        group = "main"
    )
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
            super(
                timestamp,
                condition,
                exitCode,
                vars
            );
        }
    }
}