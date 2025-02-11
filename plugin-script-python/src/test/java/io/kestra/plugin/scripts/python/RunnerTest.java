package io.kestra.plugin.scripts.python;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true)
class RunnerTest {
    @Test
    @ExecuteFlow("sanity-checks/python.yaml")
    void python(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
