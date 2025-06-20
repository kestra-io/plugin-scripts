package io.kestra.plugin.scripts.windows;

import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WindowsCmdTaskTest {
    @Test
    void testSimpleCommand() throws Exception {
        // Create a mock RunContext
        RunContext runContext = TestsUtils.mockRunContext(Map.of());

        // Create an instance of WindowsCmdTask
        WindowsCmdTask task = WindowsCmdTask.builder()
            .command("echo Hello, World!")
            .build();

        // Run the task
        WindowsCmdTask.Output output = task.run(runContext);

        // Assert the output
        assertTrue(output.getOutput().contains("Hello, World!"));
    }

    @Test
    void testInvalidCommand() {
        // Create a mock RunContext
        RunContext runContext = TestsUtils.mockRunContext(Map.of());

        // Create an instance of WindowsCmdTask with an invalid command
        WindowsCmdTask task = WindowsCmdTask.builder()
            .command("invalidcommand")
            .build();

        // Run the task and expect an exception
        Exception exception = null;
        try {
            task.run(runContext);
        } catch (Exception e) {
            exception = e;
        }

        // Assert that an exception was thrown
        assertTrue(exception != null);
        assertTrue(exception.getMessage().contains("Command failed"));
    }
}