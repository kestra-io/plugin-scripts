package io.kestra.plugin.scripts;

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
@AllArgsConstructor
public abstract class AbstractScript extends Task {
    @Schema(
        title = "A full script"
    )
    @PluginProperty(dynamic = false)
    protected String script;

    protected String generateScript(RunContext runContext) {
        return this.script;
    }
}
