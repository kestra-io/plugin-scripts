package io.kestra.plugin.scripts.deno;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Deno files and commands."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Run a Deno script with network permissions.",
        code = """
            id: deno_permissions
            namespace: company.team

            tasks:
              - id: deno_commands
                type: io.kestra.plugin.scripts.deno.Commands
                inputFiles:
                  main.ts: |
                    const response = await fetch("https://httpbin.org/status/200");
                    const data = await response.json();
                    console.log(data);
                commands:
                  - deno run --allow-net main.ts
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "denoland/deno";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run."
    )
    protected Property<List<String>> commands;

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