package io.kestra.plugin.scripts.r;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.ScriptService;
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
import jakarta.validation.constraints.NotEmpty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute R scripts from the Command Line Interface."
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
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "r-base";

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The commands to run"
    )
    @NotNull
    protected Property<List<String>> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage().toString());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        var renderedCommands = runContext.render(this.commands).asList(String.class);

        List<String> commandsArgs = ScriptService.scriptCommands(
            runContext.render(this.interpreter).asList(String.class),
            getBeforeCommandsWithOptions(runContext),
            renderedCommands,
            runContext.render(this.targetOS).as(TargetOS.class).orElse(null)
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
