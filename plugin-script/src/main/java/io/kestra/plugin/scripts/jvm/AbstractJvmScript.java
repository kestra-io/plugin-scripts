package io.kestra.plugin.scripts.jvm;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractJvmScript extends Task {
    @Schema(
        title = "A full script."
    )
    @PluginProperty(dynamic = true)
    protected String script;

    protected String generateScript(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.script);
    }
}
