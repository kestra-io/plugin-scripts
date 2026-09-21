package io.kestra.plugin.scripts.bun;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * The condition regex is compiled once and reused, because the trigger itself is rebuilt on every poll.
 */
class ConditionPatternTest {

    @Test
    void scriptTrigger_reusesTheCompiledPattern() {
        assertThat(ScriptTrigger.conditionPattern("status=\\w+"), is(sameInstance(ScriptTrigger.conditionPattern("status=\\w+"))));
    }

    @Test
    void commandsTrigger_reusesTheCompiledPattern() {
        assertThat(CommandsTrigger.conditionPattern("status=\\w+"), is(sameInstance(CommandsTrigger.conditionPattern("status=\\w+"))));
    }

    @Test
    void scriptTrigger_staysCorrectWhenMoreConditionsThanTheCacheHolds() {
        for (int i = 0; i < 200; i++) {
            assertThat(ScriptTrigger.conditionPattern("value-" + i).matcher("value-" + i).find(), is(true));
        }
        assertThat(ScriptTrigger.conditionPattern("value-0").matcher("value-0").find(), is(true));
    }
}
