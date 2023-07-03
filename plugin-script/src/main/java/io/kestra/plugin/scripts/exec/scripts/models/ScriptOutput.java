package io.kestra.plugin.scripts.exec.scripts.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@Builder
@Getter
public class ScriptOutput implements Output {
    @Schema(
        title = "The value extracted from the output of the executed `commands`"
    )
    private final Map<String, Object> vars;

    @Schema(
        title = "The standard output line count"
    )
    private final int stdOutLineCount;

    @Schema(
        title = "The standard error line count"
    )
    private final int stdErrLineCount;

    @Schema(
        title = "The exit code of the entire Flow Execution"
    )
    @NotNull
    private final int exitCode;

    @Schema(
        title = "Deprecated output files",
        description = "use `outputFiles`",
        deprecated = true
    )
    @Deprecated
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> files;

    @Schema(
        title = "The output files URI in Kestra internal storage"
    )
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> outputFiles;

    @JsonIgnore
    private Boolean warningOnStdErr;

    @Override
    public Optional<State.Type> finalState() {
        return this.warningOnStdErr != null && this.warningOnStdErr && this.stdErrLineCount > 0 ? Optional.of(State.Type.WARNING) : Output.super.finalState();
    }
}
