package io.kestra.plugin.scripts.groovy;

import java.util.List;

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
    title = "Trigger a flow when Groovy commands match a condition",
    description = "Runs its own Groovy commands on an interval through the Commands task and starts a flow when exitCondition matches. Defaults to the groovy container image and a 60-second interval."
)
@Plugin(
    examples = {
        @Example(title = "Trigger when a Groovy command exits with code 1.", full = true, code = """
            id: groovy_commands_trigger
            namespace: company.team

            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "Triggered with exitCode={{ trigger.exitCode }}"

            triggers:
              - id: poll
                type: io.kestra.plugin.scripts.groovy.CommandsTrigger
                interval: PT60S
                exitCondition: "exit 1"
                commands:
                  - groovy -e "System.exit(1)"
            """)
    }
)
public class CommandsTrigger extends AbstractGroovyTrigger {

    @Schema(
        title = "Groovy commands",
        description = "Executed on each poll with the same command rendering semantics as the Groovy Commands task, including runner variables such as workingDir. beforeCommands, env and taskRunner are not exposed by this trigger. Supply dependencies and configuration in the commands (for example @Grab in Groovy code, which may require network access) or container image."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    @Override
    protected ScriptOutput executeTask(RunContext runContext) throws Exception {
        if (this.commands == null) {
            throw new IllegalArgumentException("commands is required; supply the Groovy commands to run on each poll");
        }
        var rImage = runContext.render(this.containerImage).as(String.class).orElse(DEFAULT_IMAGE);
        var task = Commands.builder()
            .id(this.getId())
            .type(Commands.class.getName())
            .containerImage(Property.ofValue(rImage))
            .commands(this.commands)
            .build();
        return task.run(TriggerRunContext.forEmbeddedTask(runContext, task));
    }
}
