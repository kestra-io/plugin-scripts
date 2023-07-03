package io.kestra.plugin.scripts.exec.scripts.runners;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RunnerResult {
    int exitCode;
    AbstractLogThread stdOut;
    AbstractLogThread stdErr;
}
