package io.kestra.plugin.scripts.windows;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to execute Windows commands using cmd.exe.
 * <p>
 * This task allows users to execute Windows commands by providing a command string
 * and an optional working directory.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@Schema(
    title = "Execute Windows commands using cmd.exe",
    description = "This task allows you to execute Windows commands using cmd.exe."
)
@Plugin(
    examples = {
        @Example(
            title = "List files in a directory",
            code = {
                "command: \"dir\""
            }
        ),
        @Example(
            title = "Run an executable file",
            code = {
                "command: \"C:\\\\path\\\\to\\\\program.exe\""
            }
        )
    }
)
public class WindowsCmdTask implements RunnableTask<WindowsCmdTask.Output> {
    /**
     * Default constructor for WindowsCmdTask.
     * Required for deserialization and object creation.
     */
    public WindowsCmdTask() {
        // Default constructor
    }

    /**
     * The Windows command to execute using cmd.exe.
     */
    @Schema(
        title = "Command to execute",
        description = "The Windows command to execute using cmd.exe."
    )
    @PluginProperty(dynamic = true)
    private String command;

    /**
     * The directory in which the command will be executed.
     * Defaults to the task's temporary directory.
     */
    @Schema(
        title = "Working directory",
        description = "The directory in which the command will be executed. Defaults to the task's temporary directory."
    )
    @PluginProperty(dynamic = true)
    private String workingDirectory;

    /**
     * Executes the specified Windows command.
     *
     * @param runContext The context in which the task is executed.
     * @return The output of the executed command.
     * @throws Exception If an error occurs during command execution.
     */
    @Override
    public WindowsCmdTask.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        // Render the command and working directory with variables
        String renderedCommand = runContext.render(command);
        String renderedWorkingDirectory = workingDirectory != null
            ? runContext.render(workingDirectory)
            : runContext.workingDir().toString();

        logger.info("Executing command: {}", renderedCommand);
        logger.info("Working directory: {}", renderedWorkingDirectory);

        // Prepare the command for execution
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd.exe");
        cmd.add("/c");
        cmd.add(renderedCommand);

        // Execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.directory(runContext.workingDir().path().toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture the output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info(line);
                output.append(line).append(System.lineSeparator());
            }
        }

        // Wait for the process to complete
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("Command failed with exit code {}", exitCode);
            throw new RuntimeException("Command failed with exit code " + exitCode);
        }

        // Return the result
        return Output.builder()
            .output(output.toString())
            .build();
    }

    /**
     * Output class to represent the result of the executed command.
     */
    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        /**
         * Default constructor for Output.
         * Required for deserialization and object creation.
         */
        public Output(String output) {
            this.output = output;
        }

        /**
         * The output of the executed command.
         */
        @Schema(
            title = "Command output",
            description = "The output of the executed command."
        )
        private final String output;
    }
}