package io.kestra.core.tasks.scripts;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.Map;

@Builder
@Getter
@Deprecated
public class ScriptOutput implements io.kestra.core.models.tasks.Output {
    @Schema(
        title = "The value extracted from the output of the commands."
    )
    private final Map<String, Object> vars;

    @Schema(
        title = "The standard output line count."
    )
    private final int stdOutLineCount;

    @Schema(
        title = "The standard error line count."
    )
    private final int stdErrLineCount;

    @Schema(
        title = "The exit code of the whole execution."
    )
    @NotNull
    private final int exitCode;

    @Schema(
        title = "[Deprecated] Output files.",
        description = "Use `outputFiles` instead.",
        deprecated = true
    )
    @Deprecated
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> files;

    @Schema(
        title = "The output files' URIs in Kestra's internal storage."
    )
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> outputFiles;
}
