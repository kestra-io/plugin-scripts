package io.kestra.plugin.scripts.julia;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.enums.MonacoLanguages;
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
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on Julia script condition",
    description = """
        Polls by running an inline Julia script in a container (default image julia) and emits when exitCondition matches. \
        Edge mode (the default) emits only on a transition from not matching to matching, remembering the previous result in the namespace KV store. \
        Polls every 60s by default. Accepts 'exit N' or a regex (fallback substring) matched against emitted vars; a failed run has no vars, so only 'exit N' can match a failure.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the script exits with code 42.",
            full = true,
            code = """
                id: julia_script_trigger
                namespace: company.team

                triggers:
                  - id: on_exit_42
                    type: io.kestra.plugin.scripts.julia.ScriptTrigger
                    interval: PT10S
                    exitCondition: "exit 42"
                    edge: true
                    containerImage: julia
                    script: |
                      exit(42)

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
public class ScriptTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<ScriptTrigger.Output> {

    private static final String DEFAULT_IMAGE = "julia";
    private static final Pattern EXIT_CONDITION_PATTERN = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final int MAX_CACHED_CONDITIONS = 64;
    private static final Map<String, Pattern> CONDITION_PATTERNS = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                return size() > MAX_CACHED_CONDITIONS;
            }
        }
    );

    @Schema(
        title = "Container image for script execution",
        description = "Image used to run the inline Julia script; defaults to 'julia'. Provide an image that includes Julia."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline Julia script",
        description = "Multi-line Julia script executed on each poll. Note that beforeCommands and env are not exposed on the trigger, so package installs or configuration must live in the script or container image."
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.JULIA, group = "main")
    protected Property<String> script;

    @Schema(
        title = "Condition to match",
        description = """
            Rendered condition evaluated after each execution; the trigger emits only when it matches. \
            'exit N' compares the exit code, otherwise the string is used as a regex (or substring fallback) \
            against emitted vars (from ::{"outputs":...}::). On a failed run no vars are available to match \
            against, so only an 'exit N' condition can match a failure. \
            Note that Julia exits with 1 on syntax or runtime errors, \
            so prefer a dedicated exit code (e.g. 'exit 42') for the condition you watch.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval",
        description = "Interval between polls; default PT60S."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
        title = "Edge trigger mode",
        description = """
            When true (default), emit only on a transition from not matching to matching, so a condition that \
            stays true does not fire on every poll. The previous result is kept in the namespace KV store, keyed \
            by flow and trigger id. When false, emit on every poll that matches.
            """
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<Boolean> edge = Property.ofValue(true);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        boolean renderedEdge = runContext.render(this.edge).as(Boolean.class).orElse(true);

        Output out;
        try {
            out = runOnce(runContext);
        } catch (Exception e) {
            runContext.logger().warn("Trigger evaluation failed, returning empty result to avoid blocking the scheduler", e);
            return Optional.empty();
        }

        boolean matched = matchesCondition(out);
        boolean emit = shouldEmit(runContext, context, renderedEdge, matched);

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, out));
    }

    boolean shouldEmit(RunContext runContext, TriggerContext context, boolean edge, boolean matched) throws Exception {
        if (!edge) {
            return matched;
        }

        KVStore kvStore = runContext.namespaceKv(context.getNamespace());
        String key = edgeStateKey(context);

        boolean previouslyMatched = kvStore.getValue(key)
            .map(value -> Boolean.parseBoolean(String.valueOf(value.value())))
            .orElse(false);

        if (matched != previouslyMatched) {
            kvStore.put(key, new KVValueAndMetadata(null, matched));
        }

        return matched && !previouslyMatched;
    }

    static String edgeStateKey(TriggerContext context) {
        return "trigger-edge-" + context.getFlowId().length() + "-" + context.getFlowId() + "-" + context.getTriggerId();
    }

    private Output runOnce(RunContext runContext) throws Exception {
        Script task = Script.builder()
            .id(this.getId())
            .type(Script.class.getName())
            .containerImage(this.containerImage)
            .script(this.script)
            .build();

        String renderedExitCondition = runContext.render(this.exitCondition).as(String.class)
            .filter(condition -> !condition.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("exitCondition must render to a non-empty value"));

        try {
            ScriptOutput taskOutput = task.run(TriggerRunContext.forEmbeddedTask(runContext, task));
            return new Output(Instant.now(), renderedExitCondition, safeExitCode(taskOutput), safeVars(taskOutput));
        } catch (RunnableTaskException e) {
            return new Output(Instant.now(), renderedExitCondition, extractExitCode(e), null);
        }
    }

    boolean matchesCondition(Output out) {
        String cond = out.getCondition() == null ? "" : out.getCondition().trim();

        Matcher exitMatcher = EXIT_CONDITION_PATTERN.matcher(cond);
        if (exitMatcher.matches()) {
            int expected = Integer.parseInt(exitMatcher.group(1));
            return out.getExitCode() != null && out.getExitCode() == expected;
        }

        String haystack = out.getVars() == null || out.getVars().isEmpty() ? "" : out.getVars().toString();
        if (haystack.isEmpty() || cond.isEmpty()) {
            return false;
        }

        try {
            return ExitConditionRegex.find(conditionPattern(cond), haystack);
        } catch (Exception invalidRegex) {
            return ExitConditionRegex.invalidPatternFallback(cond, haystack);
        }
    }

    static Pattern conditionPattern(String condition) {
        return CONDITION_PATTERNS.computeIfAbsent(condition, Pattern::compile);
    }

    private Integer safeExitCode(ScriptOutput taskOutput) {
        try {
            return taskOutput.getExitCode();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> safeVars(ScriptOutput taskOutput) {
        try {
            return taskOutput.getVars();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer extractExitCode(RunnableTaskException e) {
        Throwable cur = e.getCause();
        while (cur != null) {
            if (cur instanceof TaskException te) {
                return te.getExitCode();
            }
            cur = cur.getCause();
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Timestamp of the event that fired the trigger")
        private Instant timestamp;

        @Schema(title = "Rendered condition", description = "Rendered value of the exitCondition property for this poll.")
        private String condition;

        @Schema(title = "Script exit code", description = "Exit code returned by the Julia process (may be null if not available).")
        private Integer exitCode;

        @Schema(title = "Script vars", description = "Vars produced by the task (e.g. via ::{\"outputs\":{...}}:: convention).")
        private Map<String, Object> vars;
    }
}
