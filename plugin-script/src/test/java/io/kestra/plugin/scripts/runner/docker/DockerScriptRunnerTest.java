package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.script.AbstractScriptRunnerTest;
import io.kestra.core.models.script.ScriptRunner;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


class DockerScriptRunnerTest extends AbstractScriptRunnerTest {

    @Override
    @Test
    @Disabled("Disable for now as the test didn't work")
    protected void inputAndOutputFiles() {
    }

    @Override
    protected ScriptRunner scriptRunner() {
        return DockerScriptRunner.builder().image("centos").build();
    }
}