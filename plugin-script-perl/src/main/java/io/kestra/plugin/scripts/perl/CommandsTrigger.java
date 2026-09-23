package io.kestra.plugin.scripts.perl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when Perl commands match a condition",
    description = "Polls and triggers a flow by running Perl commands within a script container."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when the command explicitly exits with code 1.",
            full = true,
            code = """
                id: commands_trigger
                namespace: company.team

                triggers:
                  - id: commands_failure
                    type: io.kestra.plugin.scripts.perl.CommandsTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: perl
                    commands:
                      - perl -e 'exit 1'

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
public class CommandsTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<CommandsTrigger.Output> {

    private static final String DEFAULT_IMAGE = "perl";
    private static final Pattern EXIT_CONDITION_PATTERN = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    @Schema(
        title = "Docker image used to execute the commands",
        description = """
            Container image used by the underlying Commands task to run Perl commands.
            Defaults to 'perl'.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Perl commands to execute",
        description = "Commands executed on each poll (same semantics as the Perl Commands task)."
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> commands;

    @Schema(
        title = "Condition to match",
        description = """
            Condition evaluated after each commands execution. The trigger emits an event only when this condition matches.

            Supported forms:
            - 'exit N' (example: 'exit 1'): matches when the process exit code equals N.
            - Any other string: treated as a regex (or substring if regex is invalid) matched against:
              - the task 'vars' (when commands emit ::{"outputs":...}::),
              - and error logs when the task fails (TaskException).
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
            If true, the trigger emits only on a transition from 'not matching' to 'matching' (anti-spam).
            The previous result is kept in the namespace KV store, keyed by flow and trigger id.
            If false, the trigger emits on every poll where the condition matches.
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
        Commands task = Commands.builder()
            .id(this.getId())
            .type(Commands.class.getName())
            .containerImage(this.containerImage)
            .commands(this.commands)
            .build();

        String renderedCondition = runContext.render(this.exitCondition).as(String.class).orElse("");

        try {
            ScriptOutput taskOutput = task.run(TriggerRunContext.forEmbeddedTask(runContext, task));
            Integer exitCode = safeExitCode(taskOutput);
            Map<String, Object> vars = safeVars(taskOutput);

            return new Output(Instant.now(), renderedCondition, exitCode, vars);
        } catch (RunnableTaskException e) {
            ExtractedFailure failure = extractFailure(e);
            return new Output(Instant.now(), renderedCondition, failure.exitCode, null);
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
            title = "Commands exit code",
            description = "Exit code returned by the Perl process (may be null if not available)."
        )
        private Integer exitCode;

        @Schema(
            title = "Commands vars",
            description = "Vars produced by the task (e.g. via ::{\"outputs\":{...}}:: convention)."
        )
        private Map<String, Object> vars;
    }
}
