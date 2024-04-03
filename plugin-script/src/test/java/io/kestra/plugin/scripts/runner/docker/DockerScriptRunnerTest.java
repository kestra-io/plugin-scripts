package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.script.AbstractScriptRunnerTest;
import io.kestra.core.models.script.ScriptRunner;


class DockerScriptRunnerTest extends AbstractScriptRunnerTest {
    @Override
    protected ScriptRunner scriptRunner() {
        return DockerScriptRunner.builder().image("centos").build();
    }
}