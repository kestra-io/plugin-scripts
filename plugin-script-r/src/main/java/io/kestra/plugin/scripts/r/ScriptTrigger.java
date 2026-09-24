package io.kestra.plugin.scripts.r;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.enums.MonacoLanguages;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when an R script matches a condition",
    description = "Polls by running an inline R script in a container (default image 'r-base') and starts the flow when its result matches the condition."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the script fails with exit code 1.",
            full = true,
            code = """
                id: r_script_trigger
                namespace: company.team

                triggers:
                  - id: script_failure
                    type: io.kestra.plugin.scripts.r.ScriptTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    script: |
                      stop("boom")

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

    private static final String DEFAULT_IMAGE = "r-base";

    private static final Pattern EXIT_CONDITION_PATTERN =
        Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    // The trigger is rebuilt on every poll, so the compiled condition has to live in a static.
    // Conditions can be templated, so keep it bounded and drop the least recently used entry.
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
        description = "Image used by the Script task to run the inline R script; defaults to 'r-base'. Provide an image that includes the required CRAN packages, or install them in the script itself."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline R script",
        description = "Multi-line R script executed on each poll, with the same semantics as the R Script task."
    )
    @NotNull
    @PluginProperty(language = MonacoLanguages.R, group = "main")
    protected Property<String> script;

    @Schema(
        title = "Condition to match",
        description = """
            Condition evaluated after each execution. The trigger emits only when it matches.
            'exit N' compares the exit code, otherwise the string is used as a regex
            (or substring fallback) against emitted vars and failure logs.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
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
        boolean edgeEnabled = runContext.render(this.edge).as(Boolean.class).orElse(true);

        Output output;
        try {
            output = runOnce(runContext);
        } catch (Exception e) {
            runContext.logger().warn("Trigger evaluation failed, returning empty result to avoid blocking the scheduler", e);
            return Optional.empty();
        }

        boolean matched = matchesCondition(output);

        boolean emit = shouldEmit(runContext, context, edgeEnabled, matched);

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(
            TriggerService.generateExecution(this, conditionContext, context, output)
        );
    }

    boolean shouldEmit(RunContext runContext, TriggerContext context, boolean edge, boolean matched) throws Exception {
        if (!edge) {
            return matched;
        }

        // A polling trigger is rebuilt from the flow definition (and serialized to a worker) on
        // every poll, so the previous result cannot live in a field. It is kept in the namespace
        // KV store instead and advanced on every poll.
        KVStore kvStore = runContext.namespaceKv(context.getNamespace());
        String key = edgeStateKey(context);

        boolean previouslyMatched = kvStore.getValue(key)
            .map(value -> Boolean.parseBoolean(String.valueOf(value.value())))
            .orElse(false);
        kvStore.put(key, new KVValueAndMetadata(null, matched));

        return matched && !previouslyMatched;
    }

    // Length prefixed so that the pairs ("a-b", "c") and ("a", "b-c") can never share a key.
    // Flow and trigger ids only use characters that are valid in a KV key.
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

        String renderedCondition = runContext.render(this.exitCondition)
            .as(String.class)
            .filter(condition -> !condition.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("exitCondition must render to a non-empty value"));

        try {
            ScriptOutput taskOutput = task.run(TriggerRunContext.forEmbeddedTask(runContext, task));

            return new Output(
                Instant.now(),
                renderedCondition,
                safeExitCode(taskOutput),
                safeVars(taskOutput)
            );
        } catch (RunnableTaskException e) {
            ExtractedFailure failure = extractFailure(e);
            return new Output(
                Instant.now(),
                renderedCondition,
                failure.exitCode,
                null
            );
        }
    }

    boolean matchesCondition(Output out) {
        String cond = out.getCondition() == null ? "" : out.getCondition().trim();

        Matcher exitMatcher = EXIT_CONDITION_PATTERN.matcher(cond);

        if (exitMatcher.matches()) {
            int expected = Integer.parseInt(exitMatcher.group(1));
            return out.getExitCode() != null && out.getExitCode() == expected;
        }

        String haystack = buildHaystack(out);
        if (haystack.isEmpty() || cond.isEmpty()) {
            return false;
        }

        try {
            return conditionPattern(cond).matcher(haystack).find();
        } catch (Exception invalidRegex) {
            return haystack.contains(cond);
        }
    }

    static Pattern conditionPattern(String condition) {
        return CONDITION_PATTERNS.computeIfAbsent(condition, Pattern::compile);
    }

    private String buildHaystack(Output out) {
        if (out.getVars() == null || out.getVars().isEmpty()) {
            return "";
        }
        // Map.toString() produces {key=value, ...} — intentional for substring/regex matching.
        return out.getVars().toString();
    }

    private Integer safeExitCode(ScriptOutput output) {
        try {
            return output.getExitCode();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> safeVars(ScriptOutput output) {
        try {
            return output.getVars();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ExtractedFailure(Integer exitCode) {}

    private ExtractedFailure extractFailure(RunnableTaskException e) {
        Integer exitCode = null;

        Throwable cur = e.getCause();
        while (cur != null) {
            if (cur instanceof TaskException te) {
                exitCode = te.getExitCode();
                break;
            }
            cur = cur.getCause();
        }

        return new ExtractedFailure(exitCode);
    }

    @Data
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Poll timestamp",
            description = "Timestamp when this trigger evaluation occurred."
        )
        private Instant timestamp;

        @Schema(
            title = "Rendered condition",
            description = "Rendered value of the exitCondition property for this poll."
        )
        private String condition;

        @Schema(
            title = "Script exit code",
            description = "Exit code returned by the R process (may be null if not available)."
        )
        private Integer exitCode;

        @Schema(
            title = "Script vars",
            description = """
                Vars produced by the task (e.g. via ::{"outputs":{...}}:: convention). This is the main structured
                way to evaluate non-exit conditions on successful runs.
                """
        )
        private Map<String, Object> vars;
    }
}
