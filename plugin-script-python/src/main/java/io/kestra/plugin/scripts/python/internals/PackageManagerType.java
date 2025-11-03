package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public enum PackageManagerType {
    UV("uv") {
        @Override
        public String getPythonPath(PythonDependenciesResolver resolver, String version) {
            Optional<String> pythonPath = resolver.findPython(version);
            if (pythonPath.isEmpty()) {
                resolver.installPython(version);
                pythonPath = resolver.findPython(version);
            }
            return pythonPath.orElseThrow(() ->
                new KestraRuntimeException("Could not find or install Python '" + version + "' path"));
        }

        @Override
        public ResolvedPythonPackages installPackages(PythonDependenciesResolver resolver,
                                                      String pythonPath, String version, String hash,
                                                      List<String> requirements, Path pythonLibDir) throws IOException {
            Path in = resolver.createRequirementInFileAndGetPath(version, hash, requirements);

            resolver.logger.debug("Compiling dependencies with uv");
            Path req = resolver.workingDir.createFile(resolver.getRequirementTxtFilename(hash));

            try {
                resolver.execCommandAndGetStdOut(
                    List.of(resolver.getUvCmd(), "pip", "compile",
                        "--quiet",
                        "--no-color",
                        "--no-config",
                        "--no-header",
                        "--strip-extras",
                        "--output-file", req.toString(),
                        "--python", pythonPath,
                        "--cache-dir", resolver.getUvCacheDir(),
                        in.toString()
                    )
                );
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new KestraRuntimeException("Failed to wait for 'uv pip compile' command. Error " + e.getMessage());
            }

            resolver.logger.debug("Installing packages with uv");
            try {
                resolver.execCommandAndGetStdOut(
                    List.of(resolver.getUvCmd(), "pip", "install",
                        "--quiet",
                        "--no-color",
                        "--no-config",
                        "--link-mode", "copy",
                        "--reinstall",
                        "--index-strategy", "unsafe-best-match",
                        "--target=" + pythonLibDir,
                        "--requirement=" + req,
                        "--python", pythonPath,
                        "--cache-dir", resolver.getUvCacheDir()
                    )
                );
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new KestraRuntimeException("Failed to wait for uv pip install command. Error " + e.getMessage());
            }
            return new ResolvedPythonPackages(pythonLibDir, req, hash, version);
        }

        @Override
        public boolean isAvailable(PythonDependenciesResolver resolver) {
            try {
                String version = resolver.getUvVersion(resolver.getUvCmd());
                return version != null;
            } catch (Exception e) {
                resolver.logger.debug("UV not available: {}", e.getMessage());
                return false;
            }
        }
    },

    PIP("pip") {
        @Override
        public String getPythonPath(PythonDependenciesResolver resolver, String version) {
            String normalized = null, major = null;
            if (version != null && !version.isBlank()) {
                String[] parts = version.split("\\.");
                major = parts[0];
                normalized = parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
            }

            String[] candidates = normalized == null
                ? new String[]{"python3", "python"}
                : new String[]{"python" + normalized, "python" + major, "python3", "python"};

            for (String candidate : candidates) {
                try {
                    Process process = new ProcessBuilder(candidate, "--version").start();
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        return candidate;
                    }
                } catch (Exception ignored) {}
            }
            throw new KestraRuntimeException("Could not find a suitable Python interpreter for version: " + version);
        }

        @Override
        public ResolvedPythonPackages installPackages(PythonDependenciesResolver resolver,
                                                      String pythonPath, String version, String hash,
                                                      List<String> requirements, Path pythonLibDir) throws IOException {
            Path req = resolver.workingDir.createFile(resolver.getRequirementTxtFilename(hash));
            Files.write(req, requirements, StandardCharsets.UTF_8);

            resolver.logger.debug("Installing packages with pip");
            try {
                resolver.execCommandAndGetStdOut(
                    List.of(pythonPath, "-m", "pip", "install",
                        "--quiet",
                        "--no-cache-dir",
                        "--target=" + pythonLibDir,
                        "--requirement=" + req
                    )
                );
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new KestraRuntimeException("Failed to wait for pip install command. Error " + e.getMessage());
            }
            return new ResolvedPythonPackages(pythonLibDir, req, hash, version);
        }

        @Override
        public boolean isAvailable(PythonDependenciesResolver resolver) {
            return true;
        }
    };

    private final String displayName;

    PackageManagerType(String displayName) {
        this.displayName = displayName;
    }

    public abstract String getPythonPath(PythonDependenciesResolver resolver, String version);

    public abstract ResolvedPythonPackages installPackages(PythonDependenciesResolver resolver,
                                                           String pythonPath, String version, String hash,
                                                           List<String> requirements, Path pythonLibDir) throws IOException;

    public abstract boolean isAvailable(PythonDependenciesResolver resolver);

    public static PackageManagerType from(Boolean useUv) {
        return Boolean.TRUE.equals(useUv) ? UV : PIP;
    }

    public String getDisplayName() {
        return displayName;
    }
}