package io.kestra.plugin.scripts.perl;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
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
    title = "Run Perl commands",
    description = "Executes provided Perl commands in order using the default 'perl' image unless overridden. Supports inputFiles and beforeCommands to stage sources and install modules."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Execute one or multiple Perl commands.",
        code = """
            id: perl_commands
            namespace: company.team
            tasks:
              - id: perl
                type: io.kestra.plugin.scripts.perl.Commands
                commands:
                  - perl -e 'print "Hello from Kestra!\\n";'
            """
    )
})
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "perl";

    @Schema(
        title = "Container image for Perl runtime",
        description = "Docker image used to run the commands; defaults to 'perl'. Include needed modules or install them in beforeCommands."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Commands to execute",
        description = "List of Perl commands executed in order inside the container; combine with beforeCommands for CPAN installs and inputFiles to stage scripts."
    )
    protected Property<List<String>> commands;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return this.commands(runContext)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(this.beforeCommands)
            .withCommands(this.commands)
            .withTargetOS(os)
            .run();
    }
}
