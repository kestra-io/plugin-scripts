package io.kestra.plugin.scripts.jbang;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
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
    title = "Execute one or more JBang commands."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute JBang command to execute a JAR file.",
            full = true,
            code = {
                """
                    id: jbang
                    namespace: company.team
                    
                    tasks:
                    - id: hello-jar
                       type: io.kestra.plugin.scripts.jbang.Commands
                       commands:
                         - jbang --quiet --main picocli.codegen.aot.graalvm.ReflectionConfigGenerator info.picocli:picocli-codegen:4.6.3"""
            }
        )
    }
)
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "jbangdev/jbang-action";

    @Builder.Default
    private String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "JBangs commands to run."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private List<String> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            this.commands,
            this.targetOS
        );

        return this.commands(runContext)
            .withCommands(commandsArgs)
            .run();
    }
}
