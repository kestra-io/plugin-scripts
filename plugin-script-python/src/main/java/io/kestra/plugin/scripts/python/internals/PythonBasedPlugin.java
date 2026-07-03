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
        title = "Python package dependencies",
        description = """
            List of pip-compatible package specifiers (e.g. `pandas==2.0.0`, `requests>=2.28`) installed via the configured package manager before script execution.
            """
    )
    @PluginProperty(group = "advanced")
    Property<List<String>> getDependencies();

    @Schema(
        title = "The version of Python to use for the script",
        description = "If no version is explicitly specified, the task will attempt to extract the version from the configured container image or from the local Python installation depending on the configured task runner.\n" +
            "The version is parsed from `containerImage` only when it matches the pattern `python:<numeric-version>` (e.g. `python:3.12`, `" + DEFAULT_IMAGE + "`). Tags like `latest` or custom images (e.g. `ghcr.io/kestra-io/pydata:latest`) will not be detected.\n" +
            "If it cannot determine the version, the task will default to Python " + DEFAULT_PYTHON_VERSION + " for dependency resolution and cache key computation, while the interpreter available in the container may differ.\n" +
            "Set this property explicitly or use a versioned Python image tag to avoid version mismatches."
    )
    @PluginProperty(group = "advanced")
    Property<String> getPythonVersion();

    @Schema(
        title = "Enable Python dependency caching",
        description = "When enabled, Python dependencies will be cached across task executions. This locks dependency versions and speeds up subsequent runs by avoiding redundant installations."
    )
    @PluginProperty(group = "advanced")
    Property<Boolean> getDependencyCacheEnabled();

    @Schema(
        title = "Package manager for Python dependencies",
        description = "Package manager to use for installing Python dependencies. " +
            "Options: 'UV' (default), 'PIP'. " +
            "UV automatically falls back to PIP if not available.",
        allowableValues = { "PIP", "UV" }
    )
    @PluginProperty(group = "advanced")
    Property<PackageManagerType> getPackageManager();

    @Schema(
        title = "Whether to automatically download and install 'uv' when it is missing from the worker",
        description = """
            When enabled (default), if 'uv' cannot be found on the worker, it is downloaded from a pinned, checksum-verified \
            installer and installed automatically. Disable this on locked-down or air-gapped workers where remote downloads are not allowed; \
            in that case, 'uv' must be pre-installed on the worker (or exposed via the 'UV_PATH' environment variable). When disabled and \
            'uv' is absent, the task fails fast with an actionable error instead of silently falling back to PIP.
            """
    )
    @PluginProperty(group = "advanced")
    Property<Boolean> getUvAutoInstallEnabled();

    @Schema(
        title = "The pinned version of 'uv' to download when it is missing from the worker",
        description = """
            Only used when 'uvAutoInstallEnabled' is true and 'uv' cannot be found on the worker. \
            Defaults to a version bundled with this plugin. Override this to bump the auto-installed 'uv' version \
            without waiting for a plugin release; when doing so, also set 'uvInstallerSha256' to the matching checksum.
            """
    )
    @PluginProperty(group = "advanced")
    Property<String> getUvInstallerVersion();

    @Schema(
        title = "The expected SHA-256 checksum of the pinned 'uv' installer script",
        description = """
            Used to verify the integrity of the installer downloaded from `https://astral.sh/uv/<uvInstallerVersion>/install.sh` \
            before executing it. Defaults to the checksum matching the bundled 'uvInstallerVersion'. Override this together with \
            'uvInstallerVersion' when bumping the pinned 'uv' version. The download is rejected and the task fails if the checksums \
            do not match — it never falls back to executing an unverified installer or degrades to PIP.
            """
    )
    @PluginProperty(group = "advanced")
    Property<String> getUvInstallerSha256();
}
