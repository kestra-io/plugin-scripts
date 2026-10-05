package io.kestra.plugin.scripts.dotnet;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AbstractDotnetTrigger<O extends AbstractDotnetTrigger.Output>
    extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<O> {

    private static final String DEFAULT_IMAGE =
        "mcr.microsoft.com/dotnet/sdk:10.0";

    private static final Pattern EXIT_CONDITION_PATTERN =
        Pattern.compile(
            "^\\s*exit\\s+(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE
        );

    @Schema(
        title = "Container image for task execution",
        description = """
            Container image used to execute the underlying .NET task.
            Defaults to 'mcr.microsoft.com/dotnet/sdk:10.0'.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage =
        Property.ofValue(DEFAULT_IMAGE);
    @Schema(
        title = "Target operating system",
        description = "Operating system used by the underlying .NET task."
    )
    @PluginProperty(group = "execution")
    protected Property<TargetOS> targetOS;

    @Schema(
        title = "Condition to match",
        description = """
            Condition evaluated after each polling execution.

            Supported forms:
            - 'exit N' matches the task exit code.
            - Any other string is treated as a regex, with substring fallback,
              against values emitted by the task.

            A failed task has no output vars, so only an 'exit N'
            condition can match a failure.
            """
    )
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval",
        description = "Interval between polling evaluations."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
        title = "Edge trigger mode",
        description = """
            When true (default), emit only on a transition from
            not matching to matching.
            The previous result is stored in the namespace KV store
            using the flow and trigger identity.

            When false, emit on every matching poll.
            """
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<Boolean> edge = Property.ofValue(true);

    @Override
    public Optional<Execution> evaluate(
        ConditionContext conditionContext,
        TriggerContext context
    ) throws Exception {

        RunContext runContext = conditionContext.getRunContext();

        boolean renderedEdge = runContext
            .render(this.edge)
            .as(Boolean.class)
            .orElse(true);

        O out;

        try {
            out = runOnce(runContext);
        } catch (Exception e) {
            runContext.logger().warn(
                "Trigger evaluation failed, returning empty result to avoid blocking the scheduler",
                e
            );
            return Optional.empty();
        }

        boolean matched = matchesCondition(out);

        boolean emit = shouldEmit(
            runContext,
            context,
            renderedEdge,
            matched
        );

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(
            TriggerService.generateExecution(
                this,
                conditionContext,
                context,
                out
            )
        );
    }

    private O runOnce(RunContext runContext) throws Exception {

        String renderedCondition = runContext
            .render(this.exitCondition)
            .as(String.class)
            .filter(condition -> !condition.isBlank())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "exitCondition must render to a non-empty value"
                )
            );

        try {
            ScriptOutput taskOutput = runTask(runContext);

            Integer exitCode = safeExitCode(taskOutput);
            Map<String, Object> vars = safeVars(taskOutput);

            return createOutput(
                Instant.now(),
                renderedCondition,
                exitCode,
                vars
            );
        } catch (RunnableTaskException e) {
            Integer exitCode = extractFailureExitCode(e);

            return createOutput(
                Instant.now(),
                renderedCondition,
                exitCode,
                null
            );
        }
    }

    /**
     * Concrete triggers only need to decide which .NET task to execute.
     */
    protected abstract ScriptOutput runTask(
        RunContext runContext
    ) throws Exception;

    /**
     * Creates the concrete trigger output type.
     */
    protected abstract O createOutput(
        Instant timestamp,
        String condition,
        Integer exitCode,
        Map<String, Object> vars
    );

    protected boolean shouldEmit(
        RunContext runContext,
        TriggerContext context,
        boolean edge,
        boolean matched
    ) throws Exception {

        if (!edge) {
            return matched;
        }

        // A polling trigger is rebuilt from the flow definition on every poll,
        // so the previous result cannot live in a field.
        // Store it in the namespace KV store instead.
        KVStore kvStore =
            runContext.namespaceKv(context.getNamespace());

        String key = edgeStateKey(context);

        boolean previouslyMatched = kvStore
            .getValue(key)
            .map(value ->
                Boolean.parseBoolean(
                    String.valueOf(value.value())
                )
            )
            .orElse(false);

        // Avoid rewriting the same state on every poll.
        if (matched != previouslyMatched) {
            kvStore.put(
                key,
                new KVValueAndMetadata(null, matched)
            );
        }

        return matched && !previouslyMatched;
    }

    protected static String edgeStateKey(
        TriggerContext context
    ) {
        // Length-prefixed flow ID prevents collisions such as:
        // ("a-b", "c") vs ("a", "b-c")
        return "trigger-edge-"
            + context.getFlowId().length()
            + "-"
            + context.getFlowId()
            + "-"
            + context.getTriggerId();
    }

    protected boolean matchesCondition(O out) {

        String condition = out.getCondition() == null
            ? ""
            : out.getCondition().trim();

        Matcher exitMatcher =
            EXIT_CONDITION_PATTERN.matcher(condition);

        if (exitMatcher.matches()) {
            int expected =
                Integer.parseInt(exitMatcher.group(1));

            return out.getExitCode() != null
                && out.getExitCode() == expected;
        }

        String haystack = buildHaystack(out);

        if (haystack.isEmpty() || condition.isEmpty()) {
            return false;
        }

        return ExitConditionRegex.find(
            condition,
            haystack
        );
    }

    protected String buildHaystack(O out) {

        if (out.getVars() == null || out.getVars().isEmpty()) {
            return "";
        }

        return out.getVars().toString();
    }

    protected Integer safeExitCode(
        ScriptOutput taskOutput
    ) {
        try {
            return taskOutput.getExitCode();
        } catch (Exception ignored) {
            return null;
        }
    }

    protected Map<String, Object> safeVars(
        ScriptOutput taskOutput
    ) {
        try {
            return taskOutput.getVars();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer extractFailureExitCode(
        RunnableTaskException e
    ) {

        Throwable current = e.getCause();

        while (current != null) {
            if (current instanceof TaskException taskException) {
                return taskException.getExitCode();
            }

            current = current.getCause();
        }

        return null;
    }

    @Data
    @AllArgsConstructor
    public static class Output
        implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Timestamp of the event that fired the trigger"
        )
        private Instant timestamp;

        @Schema(
            title = "Rendered condition",
            description =
                "Rendered value of the exitCondition property for this poll."
        )
        private String condition;

        @Schema(
            title = "Task exit code",
            description =
                "Exit code returned by the underlying .NET task."
        )
        private Integer exitCode;

        @Schema(
            title = "Task vars",
            description =
                "Vars produced by the underlying .NET task."
        )
        private Map<String, Object> vars;
    }
}