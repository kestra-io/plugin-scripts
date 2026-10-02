package io.kestra.plugin.scripts.groovy;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

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
    title = "Trigger a flow when a Groovy script matches a condition",
    description = "Runs its own Groovy script on an interval through the Script task and starts a flow when exitCondition matches. Defaults to the groovy container image and a 60-second interval."
)
@Plugin(
    examples = {
        @Example(title = "Trigger when structured vars report ready.", full = true, code = """
            id: groovy_script_trigger
            namespace: company.team

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "Triggered with exitCode={{ trigger.exitCode }} (vars={{ trigger.vars }})"

            triggers:
              - id: poll
                type: io.kestra.plugin.scripts.groovy.ScriptTrigger
                interval: PT60S
                exitCondition: "ready"
                script: |
                  println '::{"outputs":{"status":"ready"}}::'
            """)
    }
)
public class ScriptTrigger extends AbstractGroovyTrigger {

    @Schema(title = "Groovy script", description = "Executed on each poll with the same semantics as the Groovy Script task.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> script;

    @Override
    protected ScriptOutput executeTask(RunContext runContext) throws Exception {
        var rInput = runContext.render(this.script).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("script is required; supply the Groovy script to run on each poll"));
        var rImage = runContext.render(this.containerImage).as(String.class).orElse(DEFAULT_IMAGE);
        var task = Script.builder()
            .id(this.getId())
            .type(Script.class.getName())
            .containerImage(Property.ofValue(rImage))
            .script(Property.ofValue(rInput))
            .build();
        return task.run(TriggerRunContext.forEmbeddedTask(runContext, task));
    }
}
