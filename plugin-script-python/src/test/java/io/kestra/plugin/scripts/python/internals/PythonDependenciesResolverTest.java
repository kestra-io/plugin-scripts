package io.kestra.plugin.scripts.python.internals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.scripts.python.Script;

import jakarta.inject.Inject;

import static io.kestra.core.utils.TestsUtils.mockRunContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class PythonDependenciesResolverTest {

    private static final String FAKE_INSTALLER_URL = "https://astral.sh/uv/1.2.3/install.sh";

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldFailWhenInstallerChecksumMismatches() throws Exception {
        RunContext runContext = buildRunContext();
        byte[] tamperedInstaller = "echo 'malicious payload'".getBytes(StandardCharsets.UTF_8);

        FakeResolver resolver = new FakeResolver(runContext, tamperedInstaller, sha256Hex("not-the-real-content".getBytes(StandardCharsets.UTF_8)));

        KestraRuntimeException exception = assertThrows(KestraRuntimeException.class, resolver::getUvCmd);
        assertThat(exception.getMessage(), containsString("Integrity check failed"));
        assertThat(exception.getMessage(), containsString(FAKE_INSTALLER_URL));
    }

    @Test
    void shouldProceedWhenInstallerChecksumMatches() throws Exception {
        RunContext runContext = buildRunContext();
        byte[] validInstaller = ("""
            #!/bin/sh
            mkdir -p "$UV_INSTALL_DIR"
            printf '#!/bin/sh\\necho "uv 1.2.3"\\n' > "$UV_INSTALL_DIR/uv"
            chmod +x "$UV_INSTALL_DIR/uv"
            """).getBytes(StandardCharsets.UTF_8);

        FakeResolver resolver = new FakeResolver(runContext, validInstaller, sha256Hex(validInstaller));

        String uvCmd = resolver.getUvCmd();

        assertThat(uvCmd, is(runContext.workingDir().resolve(Path.of("uv")).toString()));
    }

    @Test
    void shouldFailFastWhenAutoInstallDisabledAndUvNotFound() throws Exception {
        RunContext runContext = buildRunContext();

        PythonDependenciesResolver resolver = new PythonDependenciesResolver(
            runContext.logger(),
            runContext.workingDir(),
            runContext.workingDir().path().getParent(),
            PackageManagerType.UV,
            "1.2.3",
            "irrelevant-when-disabled",
            false
        ) {
            @Override
            protected String detectInstalledUvVersion(String uvCmd) {
                return null;
            }
        };

        KestraRuntimeException exception = assertThrows(KestraRuntimeException.class, resolver::getUvCmd);
        assertThat(exception.getMessage(), containsString("automatic installation is disabled"));
        assertThat(exception.getMessage(), containsString("UV_PATH"));
    }

    private RunContext buildRunContext() throws Exception {
        Script task = Script.builder().id("uv-installer-test-" + UUID.randomUUID()).type(Script.class.getName()).build();
        return mockRunContext(runContextFactory, task, Map.of());
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    /**
     * Test double that forces 'uv' to be reported as absent and returns a canned installer
     * instead of downloading from astral.sh, so tests are deterministic and offline.
     */
    private static class FakeResolver extends PythonDependenciesResolver {
        private final byte[] installerContent;

        FakeResolver(RunContext runContext, byte[] installerContent, String expectedSha256) {
            super(
                runContext.logger(),
                runContext.workingDir(),
                runContext.workingDir().path().getParent(),
                PackageManagerType.UV,
                "1.2.3",
                expectedSha256,
                true
            );
            this.installerContent = installerContent;
        }

        @Override
        protected String detectInstalledUvVersion(String uvCmd) {
            return null;
        }

        @Override
        protected byte[] downloadInstaller(String url) {
            assertThat(url, is(FAKE_INSTALLER_URL));
            return installerContent;
        }
    }
}
