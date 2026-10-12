package io.kestra.plugin.scripts.fsharp;

import java.time.Instant;
import java.util.Map;

import io.kestra.plugin.scripts.dotnet.AbstractDotnetTrigger;
import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class ScriptTriggerTest {

    /*
     * matchesCondition() is protected in AbstractDotnetTrigger because it is
     * shared implementation detail, not part of the public plugin API.
     *
     * We access the inherited protected method through reflection so that
     * this test does not create a subclass of a Kestra plugin.
     *
     * This is important because Kestra's PluginScanner scans plugin classes
     * from the test classpath. A test subclass such as:
     *
     *     TestableScriptTrigger extends ScriptTrigger
     *
     * can be detected as another plugin class and cause plugin scanning to
     * fail because the test class does not have the required public no-arg
     * constructor.
     */
    private static boolean matches(
        ScriptTrigger trigger,
        ScriptTrigger.Output output
    ) throws Exception {
        var method = AbstractDotnetTrigger.class.getDeclaredMethod(
            "matchesCondition",
            AbstractDotnetTrigger.Output.class
        );

        method.setAccessible(true);

        return (boolean) method.invoke(trigger, output);
    }

    // Use the real F# ScriptTrigger.
    // Do NOT create a TestableScriptTrigger subclass here.
    private final ScriptTrigger trigger = new ScriptTrigger();

    private ScriptTrigger.Output output(
        String condition,
        Integer exitCode,
        Map<String, Object> vars
    ) {
        return new ScriptTrigger.Output(
            Instant.now(),
            condition,
            exitCode,
            vars
        );
    }

    @ParameterizedTest
    @CsvSource(
        {
            "exit 0, 0, true",
            "exit 1, 1, true",
            "EXIT 1, 1, true",
            "exit 0, 1, false",
            "exit 1, 127, false",
            "exit 42, 42, true",
        }
    )
    void exitCodeCondition(
        String condition,
        int exitCode,
        boolean expected
    ) throws Exception {
        assertThat(
            matches(trigger, output(condition, exitCode, null)),
            is(expected)
        );
    }

    @Test
    void exitCondition_nullExitCode_doesNotMatch() throws Exception {
        assertThat(
            matches(
                trigger,
                output("exit 1", null, null)
            ),
            is(false)
        );
    }

    @Test
    void substringMatch_inVars() throws Exception {
        assertThat(
            matches(
                trigger,
                output(
                    "toto",
                    0,
                    Map.of("listing", "toto")
                )
            ),
            is(true)
        );
    }

    @Test
    void noMatch_whenSubstringAbsentFromVars() throws Exception {
        assertThat(
            matches(
                trigger,
                output(
                    "toto",
                    0,
                    Map.of("listing", "something_else")
                )
            ),
            is(false)
        );
    }

    @Test
    void regexMatch_inVars() throws Exception {
        assertThat(
            matches(
                trigger,
                output(
                    "status=\\w+",
                    0,
                    Map.of("status", "status=ready")
                )
            ),
            is(true)
        );
    }

    @Test
    void noMatch_emptyHaystack() throws Exception {
        assertThat(
            matches(
                trigger,
                output("something", 0, null)
            ),
            is(false)
        );
    }

    @Test
    void noMatch_emptyCondition() throws Exception {
        assertThat(
            matches(
                trigger,
                output(
                    "",
                    0,
                    Map.of("k", "v")
                )
            ),
            is(false)
        );
    }

    @Test
    void nullCondition_doesNotMatch() throws Exception {
        assertThat(
            matches(
                trigger,
                output(
                    null,
                    0,
                    Map.of("k", "v")
                )
            ),
            is(false)
        );
    }

    @Test
    void regexCondition_delegatesToHelperAndReturnsItsResult() throws Exception {
        String condition = "status=\\w+";

        ScriptTrigger.Output output = output(
            " " + condition + " ",
            0,
            Map.of("status", "status=ready")
        );

        try (var helper = mockStatic(ExitConditionRegex.class)) {
            helper.when(
                () -> ExitConditionRegex.find(
                    condition,
                    "{status=status=ready}"
                )
            ).thenReturn(true, false);

            assertThat(
                matches(trigger, output),
                is(true)
            );

            assertThat(
                matches(trigger, output),
                is(false)
            );

            helper.verify(
                () -> ExitConditionRegex.find(
                    condition,
                    "{status=status=ready}"
                ),
                times(2)
            );

            helper.verifyNoMoreInteractions();
        }
    }
}