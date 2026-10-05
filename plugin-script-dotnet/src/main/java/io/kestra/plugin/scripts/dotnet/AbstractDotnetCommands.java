package io.kestra.plugin.scripts.dotnet;

import java.util.Collections;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDotnetCommands
    extends AbstractExecScript
    implements RunnableTask<ScriptOutput> {

    protected static final String DEFAULT_IMAGE =
        "mcr.microsoft.com/dotnet/sdk:10.0";

    @Schema(
        title = "Container image for the .NET runtime",
        description = """
            Docker image used to run the commands.
            Defaults to 'mcr.microsoft.com/dotnet/sdk:10.0'.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage =
        Property.ofValue(DEFAULT_IMAGE);

    @Override
    protected DockerOptions injectDefaults(
        RunContext runContext,
        DockerOptions original
    ) throws IllegalVariableEvaluationException {

        var builder = original.toBuilder();

        if (original.getImage() == null) {
            builder.image(
                runContext
                    .render(this.getContainerImage())
                    .as(String.class)
                    .orElse(DEFAULT_IMAGE)
            );
        }

        if (original.getEntryPoint() == null) {
            builder.entryPoint(Collections.emptyList());
        }

        return builder.build();
    }
}