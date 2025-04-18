package io.kestra.plugin.scripts.python;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.python.internals.PythonDependenciesResolver;
import io.kestra.plugin.scripts.python.internals.PythonVersionParser;
import io.kestra.plugin.scripts.python.internals.ResolvedPythonPackages;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonExecScript extends AbstractExecScript {

    public static final String DEFAULT_PYTHON_VERSION = "3.13";
    public static final String DEFAULT_IMAGE = "python:" + DEFAULT_PYTHON_VERSION + "-slim";
    public static final List<String> DEFAULT_PYTHON_PACKAGES = List.of("kestra", "amazon-ion");

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The script dependencies."
    )
    @PluginProperty
    protected Property<List<String>> dependencies;

    @Schema(
        title = "The version of Python to use for the script.",
        description = "If no version is explicitly specified, the task will attempt to extract the version from the configured container image. If it cannot determine the version from the image, the task will default to Python '"+ DEFAULT_PYTHON_VERSION +" '"
    )
    @PluginProperty
    protected Property<String> pythonVersion;

    @Schema(
        title = "Enable Python dependency caching",
        description = "When enabled, Python dependencies will be cached across task executions. This locks dependency versions and speeds up subsequent runs by avoiding redundant installations."
    )
    @PluginProperty
    protected Property<Boolean> dependencyCacheEnabled = Property.of(true);

    protected ResolvedPythonEnvironment setupPythonEnvironment(final RunContext runContext, final boolean isCacheEnabled) throws IllegalVariableEvaluationException, IOException {
        List<String> requirements = new ArrayList<>(runContext.render(dependencies).asList(String.class));
        DEFAULT_PYTHON_PACKAGES.forEach(pkg -> addPackageIfNoneMatch(requirements, pkg));

        final String targetPythonVersion = getTargetPythonVersion(runContext);

        final Path localCacheDir = getLocalCacheDir(runContext);
        final PythonDependenciesResolver resolver = new PythonDependenciesResolver(runContext.logger(), runContext.workingDir(), localCacheDir);

        final String hash = resolver.getRequirementsHashKey(targetPythonVersion, requirements);

        final long metricCacheDownloadStart = System.currentTimeMillis();

        Optional<InputStream> cacheFile = isCacheEnabled ? runContext.storage().getCacheFile("python-" + getType(), hash) : Optional.empty();

        final ResolvedPythonPackages resolvedPythonPackages;
        final boolean cached;
        if (cacheFile.isPresent()) {
            runContext.logger().debug("Restoring python dependencies cache for key: {}", hash);
            resolvedPythonPackages = resolver.getPythonLibs(targetPythonVersion, hash, cacheFile.get());
            runContext.logger().debug("Cache restored successfully");
            runContext.metric(Timer.of("task.pythondeps.cache.download.duration", Duration.ofMillis(System.currentTimeMillis() - metricCacheDownloadStart)));
            cached = true;
        } else {
            if (isCacheEnabled) {
                runContext.logger().debug("Could not find python dependencies cache for key: {}", hash);
            }
            resolvedPythonPackages = resolver.getPythonLibs(targetPythonVersion, hash, requirements);
            cached = false;
        }

        runContext.logger().debug("Installed dependencies: {}", resolvedPythonPackages.packagesToString());

        String pythonInterpreter = "python";
        if (taskRunner instanceof Process || RunnerType.PROCESS.equals(runner)) {
            pythonInterpreter = resolver.getPythonPath(targetPythonVersion);
        }

        return new ResolvedPythonEnvironment(cached, resolvedPythonPackages, pythonInterpreter);
    }

    private static Path getLocalCacheDir(RunContext runContext) {
        return ((DefaultRunContext)runContext).getApplicationContext().getEnvironment().getProperty("kestra.tasks.tmp-dir.path", String.class)
            .map(Path::of)
            .orElse(Path.of(System.getProperty("java.io.tmpdir")));
    }

    protected Boolean isCacheEnabled(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.dependencyCacheEnabled).as(Boolean.class).orElse(true);
    }

    protected void uploadCache(final RunContext runContext, final ResolvedPythonPackages resolvedPythonPackages) throws IOException {
        final long start = System.currentTimeMillis();
        runContext.logger().debug("Uploading python dependencies cache for key: {}", resolvedPythonPackages.hash());
        File cache = resolvedPythonPackages.toZippedArchive(runContext.workingDir());
        runContext.storage().putCacheFile(cache, "python-" + getType(), resolvedPythonPackages.hash());
        runContext.logger().debug("Cache uploaded successfully (size: {} bytes)", cache.length());
        runContext.metric(Timer.of("task.pythondeps.cache.upload.duration", Duration.ofMillis(System.currentTimeMillis() - start)));
    }

    private static void addPackageIfNoneMatch(final List<String> requirements, final String pythonPackage) {
        if (requirements.stream().noneMatch(s -> s.startsWith(pythonPackage))) {
            requirements.add(pythonPackage);
        }
    }

    private String getTargetPythonVersion(final RunContext runContext) throws IllegalVariableEvaluationException {
        String pyVersion;
        if (this.pythonVersion != null) {
            pyVersion = runContext.render(this.pythonVersion).as(String.class).orElse(null);
        } else {
            String container = runContext.render(this.containerImage).as(String.class).orElse(null);
            pyVersion = PythonVersionParser.parsePyVersionFromDockerImage(container).orElse(null);
        }
        if (pyVersion == null) {
            runContext.logger().warn("No Python Version found. Using default: '{}'", DEFAULT_IMAGE);
        }
        return Optional.ofNullable(pyVersion).orElse(DEFAULT_PYTHON_VERSION);
    }

    /**
     * Resolved Python Environment with Interpreter and Packages.
     *
     * @param cached        whether the python packages was resolved from cache.
     * @param packages      the python packages
     * @param interpreter   the python interpreter.
     */
    public record ResolvedPythonEnvironment(
        boolean cached,
        ResolvedPythonPackages packages,
        String interpreter
    ) {}
}
