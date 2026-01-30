package io.kestra.plugin.scripts.powershell;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run inline PowerShell script",
    description = "Executes a multi-line PowerShell script in the default 'ghcr.io/kestra-io/powershell:latest' image unless overridden. Script is saved to a temp .ps1 and run with pwsh -NoProfile -NonInteractive; outputDir/outputFiles work as usual."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a PowerShell script.",
            full = true,
            code = """
                id: execute_powershell_script
                namespace: company.team

                tasks:
                  - id: powershell
                    type: io.kestra.plugin.scripts.powershell.Script
                    script: |
                      'Hello, World!' | Write-Output
                """
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{ outputDir }}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{ outputs.yourTaskId.outputFiles['yourFileName.fileExtension'] }}`.
            """,
            code = """
                id: powershell_generate_files
                namespace: company.team

                tasks:
                  - id: powershell
                    type: io.kestra.plugin.scripts.powershell.Script
                    script: |
                      Set-Content -Path {{ outputDir }}\\hello.txt -Value "Hello World"
                """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/powershell:latest";

    @Schema(
        title = "Container image for PowerShell runtime",
        description = "Docker image used to run the script; defaults to 'ghcr.io/kestra-io/powershell:latest'. Include required modules or install them in beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline PowerShell script",
        description = "PowerShell script body as a multi-line string; written to a temporary .ps1 file and executed with pwsh. For existing files, use the Commands task."
    )
    @NotNull
    protected Property<String> script;

    @Builder.Default
    @Schema(
        title = "Which interpreter to use."
    )
    protected Property<List<String>> interpreter = Property.ofValue(List.of("pwsh", "-NoProfile", "-NonInteractive", "-Command"));

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".ps1"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);
        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(Property.ofValue(getBeforeCommandsWithOptions(runContext)))
            .withCommands(Property.ofValue((List.of(
                commands.getTaskRunner().toAbsolutePath(runContext, commands, ".\\" + relativeScriptPath, os)
            ))))
            .withTargetOS(os)
            .run();
    }

    @Override
    protected List<String> getExitOnErrorCommands(RunContext runContext) {
        return List.of("$ErrorActionPreference = 'Stop'");
    }
}
