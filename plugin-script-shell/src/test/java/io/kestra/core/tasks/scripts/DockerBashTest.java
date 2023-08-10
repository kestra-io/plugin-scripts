package io.kestra.core.tasks.scripts;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junitpioneer.jupiter.RetryingTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
@Property(name = "kestra.tasks.scripts.docker.volume-enabled", value = "true")
class DockerBashTest extends AbstractBashTest {
    @Override
    protected Bash.BashBuilder<?, ?> configure(Bash.BashBuilder<?, ?> builder) {
        return builder
            .id(this.getClass().getSimpleName())
            .type(Bash.class.getName())
            .runner(RunnerType.DOCKER)
            .dockerOptions(DockerOptions.builder()
                .image("ubuntu")
                .build()
            );
    }

    @RetryingTest(5)
    void volume() throws Exception {
        Path tmpDir = Files.createTempDirectory("tmpDirPrefix");
        Path tmpFile = tmpDir.resolve("tmp.txt");
        Files.write(tmpFile, "I'm here".getBytes());


        Bash bash = configure(Bash.builder()
            .commands(new String[]{
                "echo '::{\"outputs\": {\"extract\":\"'$(cat /host/tmp.txt)'\"}}::'",
            })
        )
            .dockerOptions(DockerOptions.builder()
                .image("ubuntu")
                .volumes(List.of(tmpDir.toFile() + ":/host" ))
                .build()
            )
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("I'm here"));
    }
}
