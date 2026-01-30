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
    title = "Execute PowerShell files and commands.",
    description = "Executes provided PowerShell commands in order using the default 'ghcr.io/kestra-io/powershell:latest' image unless overridden. Supports inputFiles and beforeCommands to stage scripts/modules; enable namespaceFiles if referencing files stored in the Namespace."
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

    @Schema(
        title = "Container image for PowerShell runtime",
        description = "Docker image used to run the commands; defaults to 'ghcr.io/kestra-io/powershell:latest'. Include required modules or install them in beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = "List of PowerShell commands executed in order; combine with beforeCommands for setup and inputFiles/namespaceFiles to stage scripts."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use."
    )
    protected Property<List<String>> interpreter = Property.ofValue(List.of("pwsh", "-NoProfile", "-NonInteractive", "-Command"));

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
            .withBeforeCommands(Property.ofValue(getBeforeCommandsWithOptions(runContext)))
            .withCommands(commands)
            .withTargetOS(os)
            .run();
    }

    @Override
    protected List<String> getExitOnErrorCommands(RunContext runContext) {
        return List.of("$ErrorActionPreference = 'Stop'");
    }
}
