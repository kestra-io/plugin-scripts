package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.runners.WorkingDir;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @param path      the path to python packages.
 * @param lockFile  the requirements locked file.
 * @param hash      the hash associated to the packages.
 * @param version   the python version.
 */
public record ResolvedPythonPackages(
    Path path,
    Path lockFile,
    String hash,
    String version
) {

    public static final String REQUIREMENTS_TXT = "requirements.txt";
    public static final String REQUIREMENTS_IN = "requirements.in";

    public File toZippedArchive(final WorkingDir workingDir) throws IOException {
        Path tempFile = workingDir.createTempFile("python-" + this.version() + "-cache.zip");

        try (
            OutputStream fos = Files.newOutputStream(tempFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
            TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)
        ) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            // Add requirements.txt first
            Path reqFile = this.lockFile();
            TarArchiveEntry reqEntry = new TarArchiveEntry(reqFile.toFile(), REQUIREMENTS_TXT);
            taos.putArchiveEntry(reqEntry);
            Files.copy(reqFile, taos);
            taos.closeArchiveEntry();

            // Walk the packages directory
            Files.walkFileTree(this.path(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = path().relativize(file);
                    String entryName = relativePath.toString().replace("\\", "/");
                    TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), entryName);
                    setPosixPermission(file, tarEntry);
                    taos.putArchiveEntry(tarEntry);
                    Files.copy(file, taos);
                    taos.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = path().relativize(dir);
                    if (!relativePath.toString().isEmpty()) {
                        String entryName = relativePath.toString().replace("\\", "/") + "/";
                        TarArchiveEntry dirEntry = new TarArchiveEntry(dir.toFile(), entryName);
                        // Preserve POSIX permissions if supported
                        setPosixPermission(dir, dirEntry);
                        taos.putArchiveEntry(dirEntry);
                        taos.closeArchiveEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                private static void setPosixPermission(Path file, TarArchiveEntry tarEntry) {
                    // Preserve POSIX permissions if supported
                    try {
                        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
                        tarEntry.setMode(UnixModeToPosixFilePermissions.fromPosixFilePermissions(perms));
                    } catch (UnsupportedOperationException | IOException ignore) {
                        // Skipping unix file permission
                    }
                }
            });
        }

        return tempFile.toFile();
    }

    public String packagesToString() throws IOException {
        return Files.readAllLines(lockFile()).stream()
            .filter(line -> !line.trim().startsWith("#"))
            .collect(Collectors.joining(", "));
    }
}
