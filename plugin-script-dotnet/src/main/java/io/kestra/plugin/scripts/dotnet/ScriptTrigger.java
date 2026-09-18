package io.kestra.plugin.scripts.dotnet;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
import io.kestra.plugin.scripts.exec.TriggerRunContext;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
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
    title = "Trigger on .NET script condition",
    description = "Polls by running an inline .NET script in a container (default image mcr.microsoft.com/dotnet/sdk:10.0) and emits when exitCondition matches. Edge mode is intended to emit only on transitions but currently does not survive a poll-to-poll worker dispatch (see the edge property). Polls every 60s by default. Accepts 'exit N' or a regex (fallback substring) matched against emitted vars; a failed run has no vars, so only 'exit N' can match a failure."
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
                    type: io.kestra.plugin.scripts.dotnet.ScriptTrigger
                    interval: PT60S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: mcr.microsoft.com/dotnet/sdk:10.0
                    script: |
                      System.Environment.Exit(1);

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

    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/dotnet/sdk:10.0";
    private static final Pattern EXIT_CONDITION_PATTERN = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    @Schema(
        title = "Container image for script execution",
        description = """
            Image used by the Script task to run the inline .NET script; defaults to 'mcr.microsoft.com/dotnet/sdk:10.0'.
            Provide an image that includes the .NET SDK.
            """
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline .NET script",
        description = """
            Multi-line script executed on each poll, with the same semantics as the .NET Script task.
            """
    )
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> script;

    @Schema(
        title = "Condition to match",
        description = """
            Rendered condition evaluated after each execution; the trigger emits only when it matches.
            'exit N' compares the exit code, otherwise the string is used as a regex (or substring fallback) \
            against emitted vars (from ::{"outputs":...}::). On a failed run no vars are available to match \
            against, so only an 'exit N' condition can match a failure.
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
            When true (default), intended to emit only on a transition from not matching to matching; when \
            false, emit on every poll that matches. Currently only dedupes within a single held-in-memory \
            trigger instance and does not survive the worker's serialize/deserialize round trip between \
            polls, so a real distributed deployment will still emit on every matching poll regardless of \
            this setting.
            """
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<Boolean> edge = Property.ofValue(true);

    // Known limitation: this only dedupes within a single held-in-memory trigger instance.
    // Polling triggers are dispatched to a worker as a serialized payload with no getter
    // exposed for this field, so it never survives that round trip - in a real distributed
    // deployment, edge mode degenerates to "matched", firing on every poll rather than only
    // on a not-matching-to-matching transition. Excluded from equals/hashCode so this
    // mutable field itself never affects equality (equals/hashCode also always fall
    // through to Object's reference identity via AbstractTrigger and this project's
    // lombok.equalsAndHashCode.callSuper=call, so two identically built triggers are
    // still unequal regardless - that part is a pre-existing, kestra-wide behavior,
    // not something this exclusion changes).
    @Builder.Default
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    private final AtomicBoolean lastMatched = new AtomicBoolean(false);

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

        boolean emit = renderedEdge
            ? (!lastMatched.getAndSet(matched) && matched)
            : matched;

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, out));
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
            return Pattern.compile(cond).matcher(haystack).find();
        } catch (Exception invalidRegex) {
            return haystack.contains(cond);
        }
    }

    private String buildHaystack(Output out) {
        if (out.getVars() == null || out.getVars().isEmpty()) {
            return "";
        }
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
            description = "Exit code returned by the .NET process (may be null if not available)."
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
