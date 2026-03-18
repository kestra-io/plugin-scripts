package io.kestra.plugins.scripts.bun;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.bun.Commands;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

@KestraTest
public class CommandsTest {

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void task() throws Exception {
        var bunCommands = Commands.builder()
            .id("bun-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .beforeCommands(Property.ofValue(List.of("bun add cowsay")))
            .commands(Property.ofValue(List.of("bun run index.ts")))
            .inputFiles(Map.of("index.ts", "import { say } from 'cowsay'; console.log(say({ text: 'I love Kestra!' }));"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bunCommands, ImmutableMap.of());
        ScriptOutput run = bunCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), greaterThanOrEqualTo(1));
    }
}