package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.kestra.plugin.scripts.python.internals.PythonBasedPlugin.DEFAULT_IMAGE;
import static io.kestra.plugin.scripts.python.internals.PythonBasedPlugin.DEFAULT_PYTHON_VERSION;

public class PythonEnvironmentManager {

    /**
     * 0: TAR+GZIP
     */
    private static final int CACHE_FORMAT_VERSION = 0; // Increment the value when changing the cache format to avoid cache restoration error

    private final PythonBasedPlugin plugin;
    private final RunContext runContext;
    private final boolean isDependencyCacheEnabled;
    private final String pythonVersion;
    private final boolean useUv;

    public PythonEnvironmentManager(final RunContext runContext,
                                final PythonBasedPlugin plugin) throws IllegalVariableEvaluationException {
        this(runContext, plugin, false); // default useUv to false
    }


    /**
     * Creates a new {@link PythonBasedPlugin} instance.
     *
     * @param plugin The plugin for which the environment will be managed.
     */
    public PythonEnvironmentManager(final RunContext runContext,
                                    final PythonBasedPlugin plugin, final boolean useUv) throws IllegalVariableEvaluationException {
        this.plugin = plugin;
        this.runContext = runContext;
        this.isDependencyCacheEnabled = runContext.render(this.plugin.getDependencyCacheEnabled()).as(Boolean.class).orElse(true);
        this.pythonVersion = runContext.render(this.plugin.getPythonVersion()).as(String.class).orElse(null);
        this.useUv = useUv;
    }

    public ResolvedPythonEnvironment setup(final Property<String> containerImage, final TaskRunner<?> taskRunner, final RunnerType runnerType) throws IllegalVariableEvaluationException, IOException {
        List<String> requirements = new ArrayList<>(runContext.render(plugin.getDependencies()).asList(String.class));

        final Path localCacheDir = getLocalCacheDir();
        final PythonDependenciesResolver resolver = new PythonDependenciesResolver(runContext.logger(), runContext.workingDir(), localCacheDir,useUv);

        final String targetPythonVersion = getTargetPythonVersion(containerImage, taskRunner, runnerType)
            .or(resolver::findLocalPythonVersion)
            .orElseGet(this::logAndGetPythonDefaultVersion);

        final String hash = resolver.getRequirementsHashKey(targetPythonVersion, requirements);

        boolean cached = false;
        ResolvedPythonPackages resolvedPythonPackages = null;
        if (!requirements.isEmpty()) {
            final long metricCacheDownloadStart = System.currentTimeMillis();

            Optional<InputStream> cacheFile = isDependencyCacheEnabled ? runContext.storage().getCacheFile(getCacheKey(), hash) : Optional.empty();

            if (cacheFile.isPresent()) {
                runContext.logger().debug("Restoring python dependencies cache for key: {}", hash);
                resolvedPythonPackages = resolver.getPythonLibs(targetPythonVersion, hash, cacheFile.get());
                runContext.logger().debug("Cache restored successfully");
                runContext.metric(Timer.of("task.pythondeps.cache.download.duration", Duration.ofMillis(System.currentTimeMillis() - metricCacheDownloadStart)));
                cached = true;
            } else {
                if (isDependencyCacheEnabled) {
                    runContext.logger().debug("Could not find python dependencies cache for key: {}", hash);
                }
                resolvedPythonPackages = resolver.getPythonLibs(targetPythonVersion, hash, requirements);
            }
            runContext.logger().debug("Installed dependencies: {}", resolvedPythonPackages.packagesToString());
        }

        String pythonInterpreter = "python";
        if (pythonVersion != null && (taskRunner instanceof Process || RunnerType.PROCESS.equals(runnerType))) {
            pythonInterpreter = resolver.getPythonPath(targetPythonVersion);
        }

        return new ResolvedPythonEnvironment(cached, resolvedPythonPackages, pythonInterpreter);
    }

    private String getCacheKey() {
        return "python-dependencies-v"+ CACHE_FORMAT_VERSION + "-" + plugin.getType();
    }

    private String logAndGetPythonDefaultVersion() {
        runContext.logger().warn("No Python Version found. Using default: '{}'", DEFAULT_IMAGE);
        return DEFAULT_PYTHON_VERSION;
    }

    private Path getLocalCacheDir() {
        return ((DefaultRunContext) runContext).getApplicationContext().getEnvironment().getProperty("kestra.tasks.tmp-dir.path", String.class)
            .map(Path::of)
            .orElse(Path.of(System.getProperty("java.io.tmpdir")));
    }

    public boolean isCacheEnabled() {
        return isDependencyCacheEnabled;
    }

    public void uploadCache(final RunContext runContext, final ResolvedPythonPackages resolvedPythonPackages) {
        try {
            final long start = System.currentTimeMillis();
            runContext.logger().debug("Uploading python dependencies cache for key: {}", resolvedPythonPackages.hash());
            File cache = resolvedPythonPackages.toZippedArchive(runContext.workingDir());
            runContext.storage().putCacheFile(cache, getCacheKey(), resolvedPythonPackages.hash());
            runContext.logger().debug("Cache uploaded successfully (size: {} bytes)", cache.length());
            runContext.metric(Timer.of("task.pythondeps.cache.upload.duration", Duration.ofMillis(System.currentTimeMillis() - start)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<String> getTargetPythonVersion(final Property<String> containerImage, final TaskRunner<?> taskRunner, final RunnerType runnerType) throws IllegalVariableEvaluationException {
        String pyVersion = null;
        if (pythonVersion != null) {
            pyVersion = pythonVersion;
        } else if (!(taskRunner instanceof Process || RunnerType.PROCESS.equals(runnerType))) {
            String container = runContext.render(containerImage).as(String.class).orElse(null);
            pyVersion = PythonVersionParser.parsePyVersionFromDockerImage(container).orElse(null);
            if (pyVersion == null) {
                pyVersion = logAndGetPythonDefaultVersion();
            }
        }
        return Optional.ofNullable(pyVersion);
    }

    /**
     * Resolved Python Environment with Interpreter and Packages.
     *
     * @param cached      whether the python packages was resolved from cache.
     * @param packages    the python packages
     * @param interpreter the python interpreter.
     */
    public record ResolvedPythonEnvironment(
        boolean cached,
        ResolvedPythonPackages packages,
        String interpreter
    ) {
    }
}
