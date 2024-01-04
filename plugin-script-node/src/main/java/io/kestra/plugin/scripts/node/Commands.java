package io.kestra.plugin.scripts.node;

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
import jakarta.validation.constraints.NotEmpty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more Node commands from the Command Line Interface. Note that instead of adding the script using the `inputFiles` property, you could also add the script from the embedded VS Code editor and point to its location by path. If you do so, make sure to enable namespace files by setting the `enabled` flag of the `namespaceFiles` property to `true`."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Install required npm packages, create a Node script and execute it.",
        code = """
            id: node
            namespace: dev
            tasks:
              - id: node_script
                type: io.kestra.plugin.scripts.node.Commands
                inputFiles:
                  main.js: |
                    const colors = require("colors");
                    console.log(colors.red("Hello"));
                beforeCommands:
                  - npm install colors
                commands:
                  - node main.js
            """
    )
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "node";

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
