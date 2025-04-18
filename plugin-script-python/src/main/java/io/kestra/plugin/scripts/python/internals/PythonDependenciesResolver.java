package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.runners.WorkingDir;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for resolving python and installing packages.
 */
@Slf4j
public class PythonDependenciesResolver {

    private static final String HOME_ENV = System.getenv("HOME");
    private static final String PATH_ENV = System.getenv("PATH");
    private static final String WORKING_DIR_ADDITIONAL_PYTHON_LIB = ".kestra_additional_python_lib";

    private final Logger logger;
    private final WorkingDir workingDir;
    private final Path localCacheDir;
    private String uvCmd;

    /**
     * Creates a new {@link PythonDependenciesResolver} instance.
     *
     * @param workingDir The {@link WorkingDir}.
     */
    public PythonDependenciesResolver(final Logger logger, final WorkingDir workingDir, final Path localCacheDir) {
        this.workingDir = Objects.requireNonNull(workingDir, "workingDir cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.localCacheDir = Objects.requireNonNull(localCacheDir, "localCacheDir cannot be null");
    }

    /**
     * Gets the path for the python interpreter.
     *
     * @param version The python version.
     * @return The path to the python interpreter.
     */
    public String getPythonPath(final String version) {
        Optional<String> pythonPath = findPython(version);
        if (pythonPath.isEmpty()) {
            installPython(version);
            pythonPath = findPython(version);
        }
        return pythonPath.orElseThrow(() -> new KestraRuntimeException("Could not find or install Python '" + version + "'path"));
    }

    /**
     * Restores and get the resolved pythons packages from the given input stream.
     *
     * @param version The python version.
     * @param hash    The versioned requirement hash.
     * @param stream  The {@link InputStream}.
     * @return The {@link ResolvedPythonPackages}.
     * @throws IOException if an error occurred while reading the {@code stream}.
     */
    public ResolvedPythonPackages getPythonLibs(final String version, final String hash, final InputStream stream) throws IOException {
        final Path path = workingDir.path();
        try (ZipInputStream zis = new ZipInputStream(stream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outputPath = path.resolve(entry.getName()).normalize();

                // Prevent zip-slip vulnerability
                if (!outputPath.startsWith(path)) {
                    logger.trace("Skipping entry '{}'", entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream os = Files.newOutputStream(outputPath)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
        return new ResolvedPythonPackages(
            workingDir.resolve(Path.of(WORKING_DIR_ADDITIONAL_PYTHON_LIB)),
            workingDir.resolve(Path.of(ResolvedPythonPackages.REQUIREMENTS_TXT)),
            hash,
            version
        );
    }

    public ResolvedPythonPackages getPythonLibs(final String version, final String hash, final List<String> requirements) throws IOException {
        final String pythonPath = getPythonPath(version);
        final Path pythonLibDir = workingDir.resolve(Path.of(WORKING_DIR_ADDITIONAL_PYTHON_LIB));

        Path in = createRequirementInFileAndGetPath(version, requirements);

        logger.debug("Compiling dependencies");
        Path req = workingDir.createFile(ResolvedPythonPackages.REQUIREMENTS_TXT);

        try {
            execCommandAndGetStdOut(
                List.of(getUvCmd(), "pip", "compile",
                    "--quiet",
                    "--no-color",
                    "--no-config",
                    "--no-header",
                    "--strip-extras",
                    "--output-file", req.toString(),
                    "--python", pythonPath,
                    "--cache-dir", getUvCacheDir(),
                    in.toString()
                )
            );
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)  {
                Thread.currentThread().interrupt();
            }
            throw new KestraRuntimeException("Failed to wait for 'uv pip compile' command. Error " + e.getMessage());
        }

        logger.debug("Installing packages");
        try {
            execCommandAndGetStdOut(
                List.of(getUvCmd(), "pip", "install",
                    "--quiet",
                    "--no-color",
                    "--no-config",
                    "--link-mode", "copy",
                    "--reinstall",
                    "--index-strategy", "unsafe-best-match",
                    "--target=" + pythonLibDir,
                    "--requirement=" + req,
                    "--python", pythonPath,
                    "--cache-dir", getUvCacheDir()
                )
            );
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)  {
                Thread.currentThread().interrupt();
            }
            throw new KestraRuntimeException("Failed to wait for uv pip install command. Error " + e.getMessage());
        }
        return new ResolvedPythonPackages(pythonLibDir, req, hash, version);
    }

    public Path createRequirementInFileAndGetPath(String version, List<String> requirements) throws IOException {
        Path in = workingDir.createFile("requirements.in");
        Files.write(in, normalizeRequirements(version, requirements), StandardCharsets.UTF_8);
        return in;
    }

    /**
     * Computes the SHA-256 hash for the given python version and dependencies requirements.
     * <p>
     * The returned hash key can be used as cached-key.
     *
     * @param version      The python version.
     * @param requirements The python package requirements.
     * @return the SHA-256 hash.
     */
    public String getRequirementsHashKey(final String version, final List<String> requirements) {
        List<String> inReqList = normalizeRequirements(version, requirements);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : inReqList) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            byte[] bytes = digest.digest();
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private List<String> normalizeRequirements(String version, List<String> requirements) {
        List<String> inReqList = new ArrayList<>(requirements);
        Collections.sort(requirements);
        inReqList.addFirst("#pyversion: " + version);
        return inReqList;
    }

    private ExecExitStatus execCommandAndGetStdOut(List<String> command) throws IOException, InterruptedException {
        return execCommandAndGetStdOut(command, null);
    }

    private ExecExitStatus execCommandAndGetStdOut(List<String> command, Function<ProcessBuilder, ProcessBuilder> modifier) throws IOException, InterruptedException {
        logger.debug("Executing command: {}", String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectErrorStream(false); // keep stderr separate for error handling

        if (modifier != null) {
            builder = modifier.apply(builder);
        }

        Process process = builder.start();

        List<String> outs = new ArrayList<>();
        Thread stdoutLogger = Thread.ofVirtual().name("python-dep-resolver-log-out")
            .start(() -> logStream(process.getInputStream(), false, outs::add));

        Thread stderrLogger = Thread.ofVirtual().name("python-dep-resolver-log-err")
            .start(() -> logStream(process.getErrorStream(), true));

        int exitCode = process.waitFor();

        stdoutLogger.join();
        stderrLogger.join();

        return new ExecExitStatus(exitCode, outs);
    }

    private String getUvCmd() {
        if (this.uvCmd != null) {
            return this.uvCmd;
        }

        this.uvCmd = "uv";
        try {
            String uvPath = Optional.ofNullable(System.getenv("UV_PATH")).orElse("$HOME/.local/bin/uv".replace("$HOME", HOME_ENV));
            if (Files.exists(Path.of(uvPath))) {
                this.uvCmd = uvPath;
            }
        } catch (SecurityException ignore) {
            // Failed to check the path of 'uv'.
            // Ignore the exception because 'uv' will be installed below if necessary.
        }

        logger.debug("Finding uv command");
        String version = null;
        try {
            version = getUvVersion(uvCmd);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)  {
                Thread.currentThread().interrupt();
            }
        }
        if (version == null) {
            logger.warn(
                "Unable to detect an installed version of 'uv'. " +
                "Attempting to install 'uv' from: https://astral.sh/uv/install.sh. " +
                "Please make sure 'uv' is installed and available on all Kestra workers."
            );
            Path script = null;
            try {
                script = workingDir.createFile("install-uv.sh");
                try (InputStream in = URI.create("https://astral.sh/uv/install.sh").toURL().openStream()) {
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                execCommandAndGetStdOut(List.of("chmod", "+x", script.toString()));
                execCommandAndGetStdOut(List.of("sh", script.toString()), builder -> {
                        Map<String, String> env = builder.environment();
                        env.clear();
                        env.put("HOME", HOME_ENV);
                        env.put("PATH", PATH_ENV);
                        env.put("UV_INSTALL_DIR", workingDir.path().toString());
                        env.put("UV_NO_MODIFY_PATH", "true");
                        return builder;
                    }
                );
                this.uvCmd = workingDir.resolve(Path.of("uv")).toString();
                version = getUvVersion(uvCmd);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException)  {
                    Thread.currentThread().interrupt();
                }
                logger.debug("Failed to install uv", e);
            } finally {
                if (script != null) {
                    try {
                        Files.deleteIfExists(script);
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        if (version != null) {
            logger.debug("Use uv: {}", version);
        }
        return this.uvCmd;
    }

    private String getUvVersion(String uvCmd) throws IOException, InterruptedException {
        ExecExitStatus execStatus = execCommandAndGetStdOut(List.of(uvCmd, "--version"), null);
        return execStatus.isSuccess() ? execStatus.stdOuts.getFirst() : null;
    }

    private boolean installPython(String version) {
        logger.debug("Installing Python '{}' environment", version);

        // Only use managed Python installations; never use system Python installations
        List<String> command = List.of(
            getUvCmd(),
            "python",
            "install",
            version,
            "--python-preference=only-managed"
        );

        final ExecExitStatus exec;
        try {
            exec = execCommandAndGetStdOut(command, builder -> {
                // Clear and set environment
                Map<String, String> env = builder.environment();
                env.clear();
                env.put("HOME", HOME_ENV);
                env.put("PATH", PATH_ENV);
                env.put("UV_PYTHON_INSTALL_DIR", localCacheDir.resolve("python").toString());
                env.put("UV_CACHE_DIR", localCacheDir.resolve("uv").toString());

                return builder;
            });
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)  {
                Thread.currentThread().interrupt();
            }
            throw new KestraRuntimeException("Failed to wait for 'uv python install' command. Error " + e.getMessage());
        }
        return exec.isSuccess();
    }

    private Optional<String> findPython(final String version) {
        logger.debug("Finding Python '{}' interpreter.", version);

        List<String> command = List.of(
            getUvCmd(), "python", "find", version, "--system", "--python-preference=only-managed"
        );

        final ExecExitStatus exec;
        try {
            exec = execCommandAndGetStdOut(command, builder -> {
                // Clear and set only needed environment variables
                Map<String, String> env = builder.environment();
                env.clear();
                env.put("HOME", HOME_ENV);
                env.put("PATH", PATH_ENV);
                env.put("UV_PYTHON_INSTALL_DIR", getUvPythonInstallDir());
                env.put("UV_PYTHON_PREFERENCE", "only-managed");
                env.put("UV_CACHE_DIR", getUvCacheDir());

                return builder;
            });
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)  {
                Thread.currentThread().interrupt();
            }
            throw new KestraRuntimeException("Failed to wait for 'uv python find' command. Error " + e.getMessage());
        }

        if (exec.isSuccess()) {
            return exec.stdOuts().stream().findFirst();
        }
        return Optional.empty();
    }

    private String getUvPythonInstallDir() {
        return localCacheDir.resolve("python").toString();
    }

    private String getUvCacheDir() {
        return localCacheDir.resolve("uv").toString();
    }

    private void logStream(InputStream stream, boolean isStdErr) {
        logStream(stream, isStdErr, null);
    }

    private void logStream(InputStream stream, boolean isStdErr, Consumer<String> listener) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (listener != null) {
                    listener.accept(line);
                }
                if (isStdErr && line.contains("error")) {
                    // 'uv' writes debug log in stderr
                    logger.debug(line);
                } else {
                    logger.debug(line);
                }
            }
        } catch (IOException e) {
            logger.error("Error logging stream: {}", isStdErr ? "stderr" : "stdout", e);
        }
    }

    record ExecExitStatus(int exitCode, List<String> stdOuts) {
        public boolean isSuccess() { return exitCode == 0; }
    }
}
