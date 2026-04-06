package io.kestra.plugin.scripts.python;

import java.util.List;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.python.internals.PackageManagerType;
import io.kestra.plugin.scripts.python.internals.PythonBasedPlugin;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonExecScript extends AbstractExecScript implements PythonBasedPlugin {

    @Schema(
        title = "The container image to use for the script.",
        description = "Defaults to `" + DEFAULT_IMAGE + "`. The Python version is auto-detected from the image tag when it matches the pattern `python:<version>` (e.g. `python:3.12`, `" + DEFAULT_IMAGE + "`). Tags like `latest` or custom images will not be detected.\n" +
            "If version inference fails, Kestra uses Python " + DEFAULT_PYTHON_VERSION + " for dependency resolution and cache key computation, while the interpreter available in the container may differ. Set `pythonVersion` explicitly or use a versioned Python image tag to avoid mismatches."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    protected Property<List<String>> dependencies;

    protected Property<String> pythonVersion;

    @Builder.Default
    protected Property<Boolean> dependencyCacheEnabled = Property.ofValue(true);

    @Builder.Default
    protected Property<PackageManagerType> packageManager = Property.ofValue(PackageManagerType.UV);
}
