package io.kestra.plugin.scripts.python.internals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.KestraRuntimeException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageManagerTypeTest {

    @ParameterizedTest
    @ValueSource(strings = { "3/../../tmp/x", "3.12/../evil", "3.12 ; id", "3.", "../3" })
    void pipShouldRejectPythonVersionThatIsNotDigitsAndDots(String version) {
        KestraRuntimeException e = assertThrows(KestraRuntimeException.class, () -> PackageManagerType.PIP.getPythonPath(null, version));

        assertThat(e.getMessage(), containsString("Invalid Python version"));
    }
}
