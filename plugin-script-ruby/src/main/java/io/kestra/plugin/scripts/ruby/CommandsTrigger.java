package io.kestra.plugin.scripts.ruby;

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
import java.util.List;
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
@Schema(title = "Trigger a flow when Ruby commands match a condition.")
@Plugin(
    examples = {
        @Example(
            title = "Trigger when Ruby command fails.",
            full = true,
            code = """
                id: ruby_commands_trigger
                namespace: company.team

                triggers:
                  - id: on_fail
                    type: io.kestra.plugin.scripts.ruby.CommandsTrigger
                    interval: PT5S
                    exitCondition: "exit 1"
                    commands:
                      - ruby -e "raise 'boom'"

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Triggered with exitCode={{ trigger.exitCode }}"
                """
        )
    }
)
public class CommandsTrigger extends AbstractTrigger
    implements PollingTriggerInterface, TriggerOutput<CommandsTrigger.Output> {

    private static final String DEFAULT_IMAGE = "ruby";

    @Schema(
        title = "Docker image used to execute the commands.",
        description = """
            Container image used by the underlying Commands task to run Ruby commands.
            Defaults to 'ruby'.
            """
    )
    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Ruby commands to execute.",
        description = "Commands executed on each poll."
    )
    @NotNull
    protected Property<List<String>> commands;

    @Schema(
        title = "Condition to match.",
        description = """
            Condition evaluated after execution.

            Supported forms:
            - 'exit N'
            - regex / substring matched against vars + logs
            """
    )
    @NotNull
    protected Property<String> exitCondition;

    @Schema(
        title = "Check interval.",
        description = "Interval between polling evaluations."
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

    // Known limitation: in-memory only — resets when the trigger is rehydrated (e.g. after restart),
    // so edge mode may re-fire once after a scheduler restart.
    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean lastMatched = new AtomicBoolean(false);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        boolean rEdge = runContext.render(this.edge).as(Boolean.class).orElse(true);

        Output out = runOnce(runContext);
        boolean matched = matchesCondition(out);

        boolean emit = rEdge
            ? (!lastMatched.getAndSet(matched) && matched)
            : matched;

        if (!emit) {
            return Optional.empty();
        }

        return Optional.of(
            TriggerService.generateExecution(this, conditionContext, context, out)
        );
    }

    private Output runOnce(RunContext runContext) throws Exception {
        Commands task = Commands.builder()
            .taskRunner(Process.instance())
            .containerImage(this.containerImage)
            .commands(this.commands)
            .build();

        String renderedCondition = runContext.render(this.exitCondition)
            .as(String.class)
            .orElse("");

        try {
            ScriptOutput taskOutput = task.run(runContext);

            return new Output(
                Instant.now(),
                renderedCondition,
                safeExitCode(taskOutput),
                safeVars(taskOutput),
                null
            );
        } catch (RunnableTaskException e) {
            ExtractedFailure failure = extractFailure(e);
            return new Output(
                Instant.now(),
                renderedCondition,
                failure.exitCode,
                null,
                failure.logs
            );
        }
    }

    private boolean matchesCondition(Output out) {
        String cond = out.getCondition() == null ? "" : out.getCondition().trim();

        Matcher exitMatcher = Pattern.compile("^\\s*exit\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE)
            .matcher(cond);

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
        } catch (Exception e) {
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
            title = "Commands exit code.",
            description = "Exit code returned by the Ruby process (may be null if not available)."
        )
        private Integer exitCode;

        @Schema(
            title = "Commands vars.",
            description = "Vars produced by the task (e.g. via ::{\"outputs\":{...}}:: convention)."
        )
        private Map<String, Object> vars;

        @Schema(
            title = "Captured logs (best effort).",
            description = "Captured error logs when the commands fail (best effort, depends on the runner)."
        )
        private String logs;
    }
}
