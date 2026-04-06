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
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on Python script condition.",
    description = """
        Polls by running an inline Python script (default image python:3.13-slim) and emits when exitCondition matches.
        Supports edge mode to emit only on transitions and polls every 60s by default.
        Accepts 'exit N' or a regex (fallback substring) matched against emitted vars and failure logs.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the Python script fails with exit code 1.",
            full = true,
            code = """
                id: python_script_trigger
                namespace: company.team

                triggers:
                  - id: script_failure
                    type: io.kestra.plugin.scripts.python.ScriptTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    script: |
                      raise Exception("boom")

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
public class ScriptTrigger extends AbstractPythonTrigger {

    @Schema(
        title = "Container image for script execution.",
        description = "Image used by the Script task to run the inline Python script; defaults to '" + DEFAULT_IMAGE + "'."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline Python script.",
        description = "Multi-line script executed on each poll."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> script;

    @Override
    protected ScriptOutput executeTask(RunContext runContext) throws Exception {
        Script task = Script.builder()
            .taskRunner(Process.instance())
            .containerImage(this.containerImage)
            .script(this.script)
            .build();

        return task.run(runContext);
    }
}
