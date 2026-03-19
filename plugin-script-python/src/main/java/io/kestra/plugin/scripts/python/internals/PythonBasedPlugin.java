package io.kestra.plugin.scripts.python.internals;

import java.util.List;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Interface for Python-based plugin.
 */
public interface PythonBasedPlugin extends Plugin {

    String DEFAULT_PYTHON_VERSION = "3.13";
    String DEFAULT_IMAGE = "python:" + DEFAULT_PYTHON_VERSION + "-slim";

    @Schema(
        title = "Python package dependencies.",
        description = "List of pip-compatible package specifiers (e.g. `pandas==2.0.0`, `requests>=2.28`) " +
            "installed via the configured package manager before script execution."
    )
    @PluginProperty
    Property<List<String>> getDependencies();

    @Schema(
        title = "The version of Python to use for the script.",
        description = "If no version is explicitly specified, the task will attempt to extract the version " +
            "from the configured container image or from the local Python installation depending " +
            "on the configured task runner. The version is parsed from `containerImage` only when " +
            "it matches the pattern `python:<numeric-version>` (e.g. `python:3.12`, " +
            "`python:3.13-slim`). Tags like `latest` or custom images (e.g. " +
            "`ghcr.io/kestra-io/pydata:latest`) will not be detected. If it cannot determine the " +
            "version, the task will default to Python " + DEFAULT_PYTHON_VERSION +
            ". It is recommended to set this property explicitly when using non-standard images."
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
        allowableValues = { "PIP", "UV" }
    )
    @PluginProperty
    Property<PackageManagerType> getPackageManager();
}
