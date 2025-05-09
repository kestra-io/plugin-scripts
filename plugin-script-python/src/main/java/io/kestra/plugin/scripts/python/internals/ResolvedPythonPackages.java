package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.runners.WorkingDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            ZipEntry reqZipEntry = new ZipEntry(REQUIREMENTS_TXT);
            zos.putNextEntry(reqZipEntry);
            Files.copy(this.lockFile(), zos);
            zos.closeEntry();
            Files.walkFileTree(this.path(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = path().relativize(file);
                    ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace("\\", "/"));
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = path().relativize(dir);
                    if (!relativePath.toString().isEmpty()) {
                        ZipEntry dirEntry = new ZipEntry(relativePath.toString().replace("\\", "/") + "/");
                        zos.putNextEntry(dirEntry);
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
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
