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

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonExecScript extends AbstractExecScript implements PythonBasedPlugin {

    @Schema(
        title = "The container image to use for the script.",
        description = "Defaults to `python:3.13-slim`. The Python version is auto-detected from the " +
            "image tag when it matches the pattern `python:<version>` (e.g. `python:3.12`, " +
            "`python:3.13-slim`). For other images, set `pythonVersion` explicitly."
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    protected Property<List<String>> dependencies;

    protected Property<String> pythonVersion;

    @Builder.Default
    protected Property<Boolean> dependencyCacheEnabled = Property.ofValue(true);

    @Builder.Default
    protected Property<PackageManagerType> packageManager = Property.ofValue(PackageManagerType.UV);
}
