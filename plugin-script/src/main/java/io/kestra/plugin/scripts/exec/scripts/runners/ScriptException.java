package io.kestra.plugin.scripts.exec.scripts.runners;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;

@Getter
@Builder
public class ScriptException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int exitCode;
    private final int stdOutSize;
    private final int stdErrSize;

    public ScriptException(int exitCode, int stdOutSize, int stdErrSize) {
        super("Command failed with code " + exitCode);
        this.exitCode = exitCode;
        this.stdOutSize = stdOutSize;
        this.stdErrSize = stdErrSize;
    }
}
