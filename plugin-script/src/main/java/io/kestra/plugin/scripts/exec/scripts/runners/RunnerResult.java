package io.kestra.plugin.scripts.exec.scripts.runners;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RunnerResult {
    private int exitCode;
    private AbstractLogThread stdOut;
    private AbstractLogThread stdErr;
}
