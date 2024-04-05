package io.kestra.plugin.scripts.exec.scripts.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @deprecated use return {@link io.kestra.core.models.tasks.runners.ScriptService} instead.
 */
@Deprecated
public final class ScriptService {
    private ScriptService() {
    }

    public static String replaceInternalStorage(RunContext runContext, @Nullable String command) throws IOException {
        return io.kestra.core.models.tasks.runners.ScriptService.replaceInternalStorage(runContext, command, (ignored, file) -> {}, false);
    }

    public static List<String> uploadInputFiles(RunContext runContext, List<String> commands) throws IOException {
        try {
            return io.kestra.core.models.tasks.runners.ScriptService.replaceInternalStorage(runContext, Collections.emptyMap(), commands, (ignored, file) -> {}, false);
        } catch (IllegalVariableEvaluationException e) {
            // Throw unchecked exception to prevent breaking the old method signature
            throw new RuntimeException(e);
        }
    }

    public static Map<String, URI> uploadOutputFiles(RunContext runContext, Path outputDir) throws IOException {
        return io.kestra.core.models.tasks.runners.ScriptService.uploadOutputFiles(runContext, outputDir);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, String command) {
        return io.kestra.core.models.tasks.runners.ScriptService.scriptCommands(interpreter, beforeCommands, command);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, List<String> commands) {
        return io.kestra.core.models.tasks.runners.ScriptService.scriptCommands(interpreter, beforeCommands, commands);
    }
}