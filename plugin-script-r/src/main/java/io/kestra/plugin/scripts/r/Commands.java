package io.kestra.plugin.scripts.r;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute R from the Command Line Interface."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a R script, install a package and execute it",
        code = """
            id: "local-files"
            namespace: "io.kestra.tests"

            tasks:
              - id: workingDir
                type: io.kestra.core.tasks.flows.WorkingDirectory
                tasks:
                - id: inputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  inputs:
                    main.R: |
                      library(lubridate)
                      ymd("20100604");
                      mdy("06-04-2011");
                      dmy("04/06/2012")
                - id: bash
                  type: io.kestra.plugin.scripts.powershell.Commands
                  beforeCommands:
                    - Rscript -e 'install.packages("lubridate")'
                  commands:
                    - Rscript main.R
            """
    )
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "r-base";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(DEFAULT_IMAGE);
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            this.commands
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
