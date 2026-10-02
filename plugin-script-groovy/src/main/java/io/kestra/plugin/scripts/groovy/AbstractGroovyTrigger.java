package io.kestra.plugin.scripts.groovy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGroovyTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<AbstractGroovyTrigger.Output> {

    protected static final String DEFAULT_IMAGE = "groovy";
    private static final Pattern EXIT_CONDITION = Pattern.compile("^exit\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);

    @Schema(title = "Container image", description = "Image used by the underlying Groovy task on each poll. Defaults to groovy.")
    @PluginProperty(group = "execution")
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(title = "Condition to match", description = """
        Evaluated after each run. 'exit N' compares a known process exit code (case insensitive).
        Other nonempty values are regexes matched against structured vars emitted using
        ::{"outputs":{...}}::. Invalid or excessive regexes fall back to substring matching.
        Raw stdout and stderr are not matched.
        """)
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> exitCondition;

    @Schema(title = "Check interval", description = "Interval between fresh executions of the configured script or commands. Defaults to PT60S.")
    @PluginProperty(group = "execution")
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(title = "Edge trigger mode", description = """
        When true (default), the first matching poll emits and consecutive matches are suppressed
        until a nonmatching poll. The last match state is stored in the flow namespace KV store,
        scoped to this flow and trigger. When false, each fresh matching run emits.
        This does not provide exactly-once delivery if an evaluation crashes.
        """)
    @PluginProperty(group = "advanced")
    @Builder.Default
    protected Property<Boolean> edge = Property.ofValue(true);

    protected abstract ScriptOutput executeTask(RunContext runContext) throws Exception;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var rEdge = runContext.render(this.edge).as(Boolean.class).orElse(true);
        var rCondition = runContext.render(this.exitCondition).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("exitCondition is required; set 'exit N' or a regex against vars"));
        validateCondition(rCondition);

        Output output;
        try {
            output = runOnce(runContext, rCondition);
        } catch (Exception e) {
            runContext.logger().warn("Groovy trigger could not execute its task; check the container image and task runner", e);
            return Optional.empty();
        }

        var matched = matchesCondition(output);
        var store = runContext.namespaceKv(context.getNamespace());
        var key = edgeStateKey(context.getFlowId(), context.getTriggerId());
        var lastMatched = store.getValue(key).map(value -> Boolean.TRUE.equals(value.value())).orElse(false);
        store.put(key, new KVValueAndMetadata(null, matched));

        if (!matched || (rEdge && lastMatched)) {
            return Optional.empty();
        }
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    static String edgeStateKey(String flowId, String triggerId) {
        return "groovy_edge_" + flowId.length() + "_" + flowId + "_" + triggerId.length() + "_" + triggerId;
    }

    private Output runOnce(RunContext runContext, String rCondition) throws Exception {
        try {
            var taskOutput = executeTask(runContext);
            return new Output(
                Instant.now(), rCondition,
                taskOutput == null ? null : taskOutput.getExitCode(),
                taskOutput == null ? null : taskOutput.getVars()
            );
        } catch (RunnableTaskException e) {
            Integer exitCode = null;
            for (var cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof TaskException taskException) {
                    exitCode = taskException.getExitCode();
                    break;
                }
            }
            if (exitCode == null) {
                throw e;
            }
            var vars = e.getOutput() instanceof ScriptOutput taskOutput ? taskOutput.getVars() : null;
            return new Output(Instant.now(), rCondition, exitCode, vars);
        }
    }

    static void validateCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("exitCondition must not be empty; set 'exit N' or a regex against vars");
        }
        var matcher = EXIT_CONDITION.matcher(condition.trim());
        if (matcher.matches()) {
            try {
                Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("exitCondition exit code must be between 0 and " + Integer.MAX_VALUE, e);
            }
        }
    }

    boolean matchesCondition(Output output) {
        if (output == null || output.getCondition() == null || output.getCondition().isBlank()) {
            return false;
        }
        var condition = output.getCondition().trim();
        validateCondition(condition);
        var matcher = EXIT_CONDITION.matcher(condition);
        if (matcher.matches()) {
            return output.getExitCode() != null && output.getExitCode() == Integer.parseInt(matcher.group(1));
        }
        return output.getVars() != null && !output.getVars().isEmpty()
            && ExitConditionRegex.find(condition, output.getVars().toString());
    }

    @Data
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Poll timestamp", description = "Time the Groovy task finished for this poll.")
        private Instant timestamp;

        @Schema(title = "Rendered condition", description = "Rendered exitCondition for this poll.")
        private String condition;

        @Schema(title = "Process exit code", description = "Groovy task exit code, or null when unavailable. Unknown codes never match 'exit N'.")
        private Integer exitCode;

        @Schema(title = "Structured script vars", description = "Values emitted by the task using ::{\"outputs\":{...}}::, including values emitted before a process failure.")
        private Map<String, Object> vars;
    }
}
