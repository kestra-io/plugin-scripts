package io.kestra.plugin.scripts.perl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
    title = "Trigger on Perl script condition",
    description = "Polls by running an inline Perl script in a container (default image perl) and emits when exitCondition matches. Edge mode (the default) emits only on a transition from not matching to matching, remembering the previous result in the namespace KV store. Polls every 60s by default. Accepts 'exit N' or a regex (fallback substring) matched against emitted vars and failure logs."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the script fails with an implicit error (exit 1).",
            full = true,
            code = """
                id: script_trigger
                namespace: company.team

                triggers:
                  - id: script_failure
                    type: io.kestra.plugin.scripts.perl.ScriptTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: perl
                    script: |
                      # This fails with a non-zero exit code.
                      exit 1;

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }} (condition={{ trigger.condition }})"
                """
        )
    }
)
// TODO: extract shared trigger logic (evaluate, matchesCondition, extractFailure, Output)
//  into an AbstractScriptTrigger in plugin-script to reduce duplication across Shell, Node, Ruby, etc.
public class ScriptTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<ScriptTrigger.Output> {

    private static final String DEFAULT_IMAGE = "perl";
    private static final Pattern EXIT_CONDITION_PATTERN = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    @Schema(
        title = "Container image for script execution",
        description = """
            Image used by the Script task to run the inline Perl script; defaults to 'perl'.
            Provide an image that includes the Perl runtime and any required CPAN modules.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline Perl script",
        description = """
            Multi-line Perl script executed on each poll, with the same semantics as the Perl Script task.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> script;

    @Schema(
        title = "Condition to match",
        description = """
            Rendered condition evaluated after each execution; the trigger emits only when it matches.
            'exit N' compares the exit code, otherwise the string is used as a regex (or substring fallback) against emitted vars (from ::{"outputs":...}::) and failure logs.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval",
        description = """
            Interval between polls; default PT60S. The scheduler uses this to schedule the next evaluation.
            """
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

        String renderedExitCondition = runContext.render(this.exitCondition).as(String.class).orElse("");

        try {
            ScriptOutput taskOutput = task.run(TriggerRunContext.forEmbeddedTask(runContext, task));
            Integer exitCode = safeExitCode(taskOutput);
            Map<String, Object> vars = safeVars(taskOutput);

            return new Output(Instant.now(), renderedExitCondition, exitCode, vars);
        } catch (RunnableTaskException e) {
            ExtractedFailure failure = extractFailure(e);
            return new Output(Instant.now(), renderedExitCondition, failure.exitCode, null);
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
            // Guard against catastrophic backtracking (ReDoS) from user-supplied patterns
            var pattern = Pattern.compile(cond);
            var future = CompletableFuture.supplyAsync(
                () -> pattern.matcher(haystack).find()
            );
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            return haystack.contains(cond);
        } catch (Exception e) {
            return haystack.contains(cond);
        }
    }

    private String buildHaystack(Output out) {
        if (out.getVars() == null || out.getVars().isEmpty()) {
            return "";
        }
        // Map.toString() produces {key=value, ...} — intentional for substring/regex matching.
        return out.getVars().toString();
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

    private record ExtractedFailure(Integer exitCode) {
    }

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
        @Schema(title = "Timestamp of the event that fired the trigger")
        private Instant timestamp;

        @Schema(
            title = "Rendered condition",
            description = "Rendered value of the exitCondition property for this poll."
        )
        private String condition;

        @Schema(
            title = "Script exit code",
            description = "Exit code returned by the Perl process (may be null if not available)."
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
