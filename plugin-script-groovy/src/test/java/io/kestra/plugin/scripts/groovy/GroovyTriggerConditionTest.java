package io.kestra.plugin.scripts.groovy;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GroovyTriggerConditionTest {
    private AbstractGroovyTrigger trigger(boolean commands) {
        return commands ? CommandsTrigger.builder().build() : ScriptTrigger.builder().build();
    }

    static Stream<Arguments> conditions() {
        return Stream.of(false, true).flatMap(
            commands -> Stream.of(
                Arguments.of(commands, "exit 0", 0, null, true),
                Arguments.of(commands, "  EXIT  1  ", 1, null, true),
                Arguments.of(commands, "exit 42", 42, Map.of(), true),
                Arguments.of(commands, "exit 0", 1, null, false),
                Arguments.of(commands, "exit 0", null, null, false),
                Arguments.of(commands, null, 0, Map.of("status", "ready"), false),
                Arguments.of(commands, "", 0, Map.of("status", "ready"), false),
                Arguments.of(commands, "  ", 0, Map.of("status", "ready"), false),
                Arguments.of(commands, "ready", 0, null, false),
                Arguments.of(commands, "ready", 0, Map.of(), false),
                Arguments.of(commands, "status=r.*", 0, Map.of("status", "ready"), true),
                Arguments.of(commands, "status=waiting", 0, Map.of("status", "ready"), false),
                Arguments.of(commands, "[", 0, Map.of("status", "[ready"), true),
                Arguments.of(commands, "[", 0, Map.of("status", "ready"), false)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("conditions")
    void matchesSupportedConditions(boolean commands, String condition, Integer code, Map<String, Object> vars, boolean expected) {
        assertEquals(expected, trigger(commands).matchesCondition(new AbstractGroovyTrigger.Output(Instant.now(), condition, code, vars)));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void nullOutputAndOverflow(boolean commands) {
        assertFalse(trigger(commands).matchesCondition(null));
        assertThrows(
            IllegalArgumentException.class, () -> trigger(commands).matchesCondition(
                new AbstractGroovyTrigger.Output(Instant.now(), "exit 2147483648", 0, null)
            )
        );
    }

    @Test
    void scopedKeysDoNotCollide() {
        assertNotEquals(AbstractGroovyTrigger.edgeStateKey("ab", "c"), AbstractGroovyTrigger.edgeStateKey("a", "bc"));
        assertNotEquals(AbstractGroovyTrigger.edgeStateKey("a_b", "c"), AbstractGroovyTrigger.edgeStateKey("a", "b_c"));
    }
}
