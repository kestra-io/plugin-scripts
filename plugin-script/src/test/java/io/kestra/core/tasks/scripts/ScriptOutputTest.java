package io.kestra.core.tasks.scripts;

import io.kestra.core.models.flows.State;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ScriptOutputTest {

    @Test
    void shouldReturnFailStateForExitCodeNotZero() {
        ScriptOutput output = ScriptOutput.builder()
            .exitCode(1)
            .build();
        Assertions.assertEquals(Optional.of(State.Type.FAILED), output.finalState());
    }

    @Test
    void shouldReturnEmptyStateForExitCodeNotZero() {
        ScriptOutput output = ScriptOutput.builder()
            .exitCode(0)
            .build();
        Assertions.assertEquals(Optional.empty(), output.finalState());
    }

    @Test
    void shouldReturnWarnStateForExitCodeNotZero() {
        ScriptOutput output = ScriptOutput.builder()
            .exitCode(0)
            .stdErrLineCount(10)
            .warningOnStdErr(true)
            .build();
        Assertions.assertEquals(Optional.of(State.Type.WARNING), output.finalState());
    }
}