package io.kestra.plugin.scripts.r;

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

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute R files and commands.",
    description = "Executes provided R commands in order using the default 'r-base' image unless overridden. Supports inputFiles and beforeCommands to stage scripts and install packages; enable namespaceFiles if pointing to stored files."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create an R script, install required packages and execute it. Note that instead of defining the script inline, you could create the script as a dedicated R script in the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the `enabled` flag of the `namespaceFiles` property to `true`.",
        code = """
            id: r_commands
            namespace: company.team

            tasks:
              - id: r
                type: io.kestra.plugin.scripts.r.Commands
                inputFiles:
                  main.R: |
                    library(lubridate)
                    ymd("20100604");
                    mdy("06-04-2011");
                    dmy("04/06/2012")
                beforeCommands:
                  - Rscript -e 'install.packages("lubridate")'
                commands:
                  - Rscript main.R
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "r-base";

    @Schema(
        title = "Container image for R runtime",
        description = "Docker image used to run the commands; defaults to 'r-base'. Include required packages or install them in beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = "List of R commands executed in order inside the container; combine with beforeCommands for package installs and inputFiles/namespaceFiles to stage scripts."
    )
    @NotNull
    protected Property<List<String>> commands;

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
