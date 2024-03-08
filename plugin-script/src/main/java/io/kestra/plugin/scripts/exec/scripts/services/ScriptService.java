package io.kestra.plugin.scripts.exec.scripts.services;

import io.kestra.core.runners.RunContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @deprecated use return {@link io.kestra.core.models.script.ScriptService} instead.
 */
@Deprecated
public final class ScriptService {
    private ScriptService() {
    }

    public static String replaceInternalStorage(RunContext runContext, @Nullable String command) throws IOException {
        return io.kestra.core.models.script.ScriptService.replaceInternalStorage(runContext, command);
    }

    public static List<String> uploadInputFiles(RunContext runContext, List<String> commands) throws IOException {
        return io.kestra.core.models.script.ScriptService.uploadInputFiles(runContext, commands);
    }

    public static Map<String, URI> uploadOutputFiles(RunContext runContext, Path outputDir) throws IOException {
        return io.kestra.core.models.script.ScriptService.uploadOutputFiles(runContext, outputDir);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, String command) {
        return io.kestra.core.models.script.ScriptService.scriptCommands(interpreter, beforeCommands, command);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, List<String> commands) {
        return io.kestra.core.models.script.ScriptService.scriptCommands(interpreter, beforeCommands, commands);
    }
}