package io.kestra.plugin.scripts.node;

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
@Schema(title = "Trigger a flow when Node.js commands match a condition.")
@Plugin(
    examples = {
        @Example(
            title = "Trigger when Node command fails.",
            full = true,
            code = """
                id: node_commands_trigger
                namespace: io.kestra.dev

                triggers:
                  - id: on_fail
                    type: io.kestra.plugin.scripts.node.CommandsTrigger
                    interval: PT5S
                    exitCondition: "exit 1"
                    commands:
                      - node -e "throw new Error('boom')"

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

    @Schema(
        title = "Node commands to execute.",
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

    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

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
                taskOutput.getExitCode(),
                taskOutput.getVars(),
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
        private String condition;
        private Integer exitCode;
        private Map<String, Object> vars;
        private String logs;
    }
}
