package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.PluginProperty;
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

import java.util.List;

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonExecScript extends AbstractExecScript implements PythonBasedPlugin {

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    protected Property<List<String>> dependencies;

    protected Property<String> pythonVersion;

    protected Property<Boolean> dependencyCacheEnabled = Property.of(true);

    @Schema(
        title = "Package manager for Python dependencies",
        description = "Package manager to use for installing Python dependencies. " +
            "Options: 'UV' (default), 'PIP'. ",
        allowableValues = {"PIP", "UV"}
    )
    @Builder.Default
    protected Property<PackageManagerType> packageManager = Property.ofValue(PackageManagerType.UV);
}
