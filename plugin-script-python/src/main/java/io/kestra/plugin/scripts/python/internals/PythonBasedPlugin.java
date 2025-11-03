package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Interface for Python-based plugin.
 */
public interface PythonBasedPlugin extends Plugin {

    String DEFAULT_PYTHON_VERSION = "3.13";
    String DEFAULT_IMAGE = "python:" + DEFAULT_PYTHON_VERSION + "-slim";

    @Schema(
        title = "The script dependencies."
    )
    @PluginProperty
    Property<List<String>> getDependencies();

    @Schema(
        title = "The version of Python to use for the script.",
        description = "If no version is explicitly specified, the task will attempt to extract the version from the configured container image or from the local python installation depending on configured task runner. If it cannot determine the version, the task will default to Python '" + DEFAULT_PYTHON_VERSION + " '"
    )
    @PluginProperty
    Property<String> getPythonVersion();

    @Schema(
        title = "Enable Python dependency caching",
        description = "When enabled, Python dependencies will be cached across task executions. This locks dependency versions and speeds up subsequent runs by avoiding redundant installations."
    )
    @PluginProperty
    Property<Boolean> getDependencyCacheEnabled();

    @Schema(
        title = "Package manager for Python dependencies",
        description = "Package manager to use for installing Python dependencies. " +
            "Options: 'UV' (default), 'PIP'. " +
            "UV automatically falls back to PIP if not available.",
        allowableValues = {"PIP", "UV"}
    )
    @PluginProperty
    Property<PackageManagerType> getPackageManager();
}
