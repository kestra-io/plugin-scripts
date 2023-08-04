package io.kestra.plugin.scripts.r;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute an R script."
)
@Plugin(
    examples = {
        @Example(
            title = "Install a package and execute a R script",
            code = {
                "script: |",
                "  library(lubridate)",
                "  ymd(\"20100604\");",
                "  mdy(\"06-04-2011\");",
                "  dmy(\"04/06/2012\")",
                "beforeCommands:",
                "  - Rscript -e 'install.packages(\"lubridate\")'"
            }
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{outputDir}}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{outputs.yourTaskId.outputFiles['yourFileName.fileExtension']}}`.
            """,
            code = """
                id: rCars
                namespace: dev

                tasks:
                  - id: r
                    type: io.kestra.plugin.scripts.r.Script
                    warningOnStdErr: false
                    docker:
                    image: ghcr.io/kestra-io/rdata:latest
                    script: |
                    library(dplyr)
                    library(arrow)

                    data(mtcars) # Load mtcars data
                    print(head(mtcars))

                    final <- mtcars %>%
                        summarise(
                        avg_mpg = mean(mpg),
                        avg_disp = mean(disp),
                        avg_hp = mean(hp),
                        avg_drat = mean(drat),
                        avg_wt = mean(wt),
                        avg_qsec = mean(qsec),
                        avg_vs = mean(vs),
                        avg_am = mean(am),
                        avg_gear = mean(gear),
                        avg_carb = mean(carb)
                        ) 
                    final %>% print()
                    write.csv(final, "{{outputDir}}/final.csv")

                    mtcars_clean <- na.omit(mtcars) # remove rows with NA values
                    write_parquet(mtcars_clean, "{{outputDir}}/mtcars_clean.parquet")          
                """
        )        
    }
)
public class Script extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("r-base")
        .build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    protected String script;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Path path = runContext.tempFile(
            ScriptService.replaceInternalStorage(runContext, runContext.render(this.script, commands.getAdditionalVars())).getBytes(StandardCharsets.UTF_8),
            ".R"
        );

        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "Rscript", path.toAbsolutePath().toString())
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
