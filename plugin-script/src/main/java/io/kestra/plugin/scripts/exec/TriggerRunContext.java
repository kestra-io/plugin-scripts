package io.kestra.plugin.scripts.exec;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Helper for running an embedded task from within a trigger evaluation.
 *
 * <p>Trigger RunContexts carry {@code flow} and {@code trigger} variables but not
 * {@code task}, {@code execution}, or {@code taskrun}. The Docker task runner calls
 * {@code ScriptService.labels(runContext, ...)} which reads those three entries and
 * NPEs when they are absent.
 *
 * <p>This class clones the trigger RunContext (preserving its logger, storage, and
 * working directory) and injects synthetic placeholder values for the missing entries
 * so that Docker container labels can be set without a NPE.
 *
 * <p>The {@code variables} field on {@link DefaultRunContext} is private and there is
 * no public mutator — {@code setVariables()} is package-private. Reflection is the
 * only available mechanism from plugin code that does not require creating a completely
 * disconnected RunContext (which would lose the trigger's logger and storage).
 */
public final class TriggerRunContext {

    private TriggerRunContext() {}

    /**
     * Returns a RunContext suitable for executing an embedded task inside a trigger
     * evaluation. The returned context is a clone of {@code triggerCtx} enriched with
     * synthetic {@code task}, {@code execution}, and {@code taskrun} variables required
     * by the Docker task runner. All other context (logger, storage, working directory)
     * is shared with the original trigger context so that output remains visible in the
     * trigger's execution log.
     *
     * <p>If the trigger context is not a {@link DefaultRunContext}, or if reflection
     * fails for any reason, the original context is returned unchanged (best-effort
     * fallback — the caller's existing try-catch in evaluate() will handle any
     * downstream NPE).
     *
     * @param triggerCtx the RunContext from {@code conditionContext.getRunContext()}
     * @param task the embedded task instance (must have id and type set)
     * @return an enriched RunContext safe to pass to {@code task.run()}
     */
    public static RunContext forEmbeddedTask(RunContext triggerCtx, Task task) {
        if (!(triggerCtx instanceof DefaultRunContext base)) {
            return triggerCtx;
        }

        DefaultRunContext clone = base.clone();

        Map<String, Object> vars = new HashMap<>(triggerCtx.getVariables());

        String taskId = Optional.ofNullable(task.getId()).orElse("trigger-embedded-task");
        String taskType = Optional.ofNullable(task.getType()).orElse("");
        vars.putIfAbsent("task", Map.of("id", taskId, "type", taskType));

        String evalId = Optional.ofNullable(triggerCtx.getTriggerExecutionId())
            .orElse(taskId + "-eval");
        vars.putIfAbsent("execution", Map.of("id", evalId));
        vars.putIfAbsent("taskrun", Map.of("id", evalId + "-taskrun", "attemptsCount", "0"));

        try {
            Field variablesField = DefaultRunContext.class.getDeclaredField("variables");
            variablesField.setAccessible(true);
            variablesField.set(clone, vars);
        } catch (Exception ignored) {
            return triggerCtx;
        }

        return clone;
    }
}
