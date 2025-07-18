package io.kestra.plugin.scripts.powershell;

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

import java.util.Collections;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute PowerShell commands.",
    description = "Note that instead of adding the script using the inputFiles property, you can also add the script from the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the enabled flag of the namespaceFiles property to true."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Execute PowerShell commands.",
        code = """
            id: execute_powershell_commands
            namespace: company.team

            tasks:
              - id: powershell
                type: io.kestra.plugin.scripts.powershell.Commands
                inputFiles:
                  main.ps1: |
                    'Hello, World!' | Write-Output
                commands:
                  - ./main.ps1
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/powershell:latest";

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use."
    )
    protected Property<List<String>> interpreter = Property.of(List.of("pwsh", "-NoProfile", "-NonInteractive", "-Command"));

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(DEFAULT_IMAGE));
        }
        if (original.getEntryPoint() == null) {
            builder.entryPoint(Collections.emptyList());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(Property.of(getBeforeCommandsWithOptions(runContext)))
            .withCommands(commands)
            .withTargetOS(os)
            .run();
    }

    @Override
    protected List<String> getExitOnErrorCommands(RunContext runContext) throws IllegalVariableEvaluationException {
        return List.of("$ErrorActionPreference = \"Stop\"");
    }
}
