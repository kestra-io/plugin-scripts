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
@Schema(title = "Trigger a flow when a Shell script matches a condition.")
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
        title = "Docker image used to execute the script.",
        description = """
            Container image used by the underlying Script task to run the inline shell script.
            Defaults to 'ubuntu'.
            """
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline shell script to execute.",
        description = """
            Inline script content (multi-line string). This is the same 'script' concept as the Shell Script task.
            The script is executed on each poll.
            """
    )
    @NotNull
    protected Property<String> script;

    @Schema(
        title = "Condition to match.",
        description = """
            Condition evaluated after each script execution. The trigger emits an event only when this condition matches.

            Supported forms:
            - 'exit N' (example: 'exit 1'): matches when the script exit code equals N.
            - Any other string: treated as a regex (or substring if regex is invalid) matched against:
              - the task 'vars' (when the script emits ::{"outputs":...}::),
              - and error logs when the task fails (TaskException).
            """
    )
    @NotNull
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval",
        description = """
            Interval between polling evaluations. The scheduler uses this interval to compute the next evaluation date.
            """
    )
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(
        title = "Edge trigger mode.",
        description = """
            If true, the trigger emits only on a transition from 'not matching' to 'matching' (anti-spam).
            If false, the trigger emits on every poll where the condition matches.
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
