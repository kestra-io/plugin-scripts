package io.kestra.plugin.scripts.malloy;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more Malloy commands from the Command Line Interface."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Create a malloy script and run the malloy-cli run command",
        code = """
               id: malloy
               namespace: dev
               
               tasks:
               
                 - id: working_dir
                   type: io.kestra.core.tasks.flows.WorkingDirectory
                   tasks:
                     - id: local_file
                       type: io.kestra.core.tasks.storages.LocalFiles
                       inputs:
                         model.malloy: |
                           source: my_model is table('duckdb:https://raw.githubusercontent.com/kestra-io/datasets/main/csv/Iris.csv')
               
                           run: my_model -> {
                               group_by: variety
                               aggregate:
                                   avg_petal_width is avg(petal_width)
                                   avg_petal_length is avg(petal_length)
                                   avg_sepal_width is avg(sepal_width)
                                   avg_sepal_length is avg(sepal_length)
                           }
               
                     - id: run_malloy
                       type: io.kestra.plugin.scripts.malloy.CLI
                       commands:
                         - malloy-cli run model.malloy
                """
    )
})
public class CLI extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("ghcr.io/kestra-io/malloy")
        .build();

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    protected List<String> commands;

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
