package io.kestra.plugin.scripts.shell;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger on shell script condition",
    description = "Polls by running an inline shell script in a container (default image ubuntu) and emits when exitCondition matches. Supports edge mode to emit only on transitions and polls every 60s by default. Accepts 'exit N' or a regex (fallback substring) matched against emitted vars and failure logs; run untrusted scripts only in trusted images."
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
                    type: io.kestra.plugin.scripts.shell.ScriptTrigger
                    interval: PT10S
                    exitCondition: "exit 1"
                    edge: true
                    containerImage: ubuntu
                    script: |
                      # This command fails because the file doesn't exist, resulting in a non-zero exit code.
                      cat /path/that/does/not/exist

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

    private static final String DEFAULT_IMAGE = "ubuntu";

    @Schema(
        title = "Container image for script execution",
        description = """
            Image used by the Script task to run the inline shell script; defaults to 'ubuntu'.
            Provide an image that includes the needed shell and tooling.
            """
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline shell script",
        description = """
            Multi-line script executed on each poll, with the same semantics as the Shell Script task.
            """
    )
    @NotNull
    protected Property<String> script;

    @Schema(
        title = "Condition to match",
        description = """
            Rendered condition evaluated after each execution; the trigger emits only when it matches.
            'exit N' compares the exit code, otherwise the string is used as a regex (or substring fallback) against emitted vars (from ::{"outputs":...}::) and failure logs.
            """
    )
    @NotNull
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval",
        description = """
            Interval between polls; default PT60S. The scheduler uses this to schedule the next evaluation.
            """
    )
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
        title = "Edge trigger mode",
        description = """
            When true (default), emit only on a transition from not matching to matching. When false, emit on every poll that matches.
            """
    )
    @Builder.Default
    protected Property<Boolean> edge = Property.ofValue(true);

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean lastMatched = new AtomicBoolean(false);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        boolean rEdge = runContext.render(this.edge).as(Boolean.class).orElse(true);

        Output out = runOnce(runContext);

        // USE exitCondition to decide whether to emit
        boolean matched = matchesCondition(out);

        boolean emit = rEdge
            ? (!lastMatched.getAndSet(matched) && matched)
            : matched;

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, out));
    }

    private Output runOnce(RunContext runContext) throws Exception {
        Script task = Script.builder()
            .taskRunner(Process.instance())
            .containerImage(this.containerImage)
            .script(this.script)
            .build();

        String rExitCondition = runContext.render(this.exitCondition).as(String.class).orElse("");

        try {
            ScriptOutput taskOutput = task.run(runContext);
            Integer exitCode = safeExitCode(taskOutput);

            // vars are the only reliable structured "result" we can read on success
            Map<String, Object> vars = safeVars(taskOutput);

            return new Output(Instant.now(), rExitCondition, exitCode, vars, null);
        } catch (RunnableTaskException e) {
            ExtractedFailure failure = extractFailure(e);
            return new Output(Instant.now(), rExitCondition, failure.exitCode, null, failure.logs);
        }
    }

    private boolean matchesCondition(Output out) {
        String cond = out.getCondition() == null ? "" : out.getCondition().trim();

        // 1) exit N
        Matcher exitMatcher = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE).matcher(cond);
        if (exitMatcher.matches()) {
            int expected = Integer.parseInt(exitMatcher.group(1));
            return out.getExitCode() != null && out.getExitCode() == expected;
        }

        // 2) otherwise: regex (fallback contains) on vars + logs
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
        StringBuilder sb = new StringBuilder();

        if (out.getVars() != null && !out.getVars().isEmpty()) {
            sb.append(out.getVars()).append("\n");
        }
        if (out.getLogs() != null && !out.getLogs().isBlank()) {
            sb.append(out.getLogs()).append("\n");
        }

        return sb.toString();
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

    private record ExtractedFailure(Integer exitCode, String logs) {}

    private ExtractedFailure extractFailure(RunnableTaskException e) {
        Integer exitCode = null;
        String logs = null;

        Throwable cur = e.getCause();
        while (cur != null) {
            if (cur instanceof TaskException te) {
                exitCode = te.getExitCode();
                // Best-effort: TaskException carries a log consumer; toString() usually contains aggregated logs.
                try {
                    logs = te.getLogConsumer() != null ? te.getLogConsumer().toString() : null;
                } catch (Exception ignored) {}
                break;
            }
            cur = cur.getCause();
        }

        return new ExtractedFailure(exitCode, logs);
    }

    @Data
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        private Instant timestamp;

        @Schema(
            title = "Rendered condition.",
            description = "Rendered value of the exitCondition property for this poll."
        )
        private String condition;

        @Schema(
            title = "Script exit code.",
            description = "Exit code returned by the shell process (may be null if not available)."
        )
        private Integer exitCode;

        @Schema(
            title = "Script vars.",
            description = """
                Vars produced by the task (e.g. via ::{"outputs":{...}}:: convention). This is the main structured
                way to evaluate non-exit conditions on successful runs.
                """
        )
        private Map<String, Object> vars;

        @Schema(
            title = "Captured logs (best effort).",
            description = "Captured error logs when the script fails (best effort, depends on the runner)."
        )
        private String logs;
    }
}
