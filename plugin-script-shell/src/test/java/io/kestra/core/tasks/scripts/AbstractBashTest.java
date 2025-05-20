package io.kestra.core.tasks.scripts;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.TaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
abstract class AbstractBashTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    abstract protected Bash.BashBuilder<?, ?> configure(Bash.BashBuilder<?, ?> builder);

    @Test
    void run() throws Exception {
        Bash bash = configure(Bash.builder()
            .commands(new String[]{"echo 0", "echo 1", ">&2 echo 2", ">&2 echo 3"})
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(2));
        assertThat(run.getStdErrLineCount(), is(2));
    }

    @Test
    void files() throws Exception {
        Bash bash = configure(Bash.builder()
            .outputFiles(Property.of(Arrays.asList("xml", "csv")))
            .inputFiles(ImmutableMap.of("files/in/in.txt", "I'm here"))
            .commands(new String[]{
                "echo '::{\"outputs\": {\"extract\":\"'$(cat files/in/in.txt)'\"}}::'",
                "echo 1 >> {{ outputFiles.xml }}",
                "echo 2 >> {{ outputFiles.csv }}",
                "echo 3 >> {{ outputFiles.xml }}"
            })
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErrLineCount(), is(0));

        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("I'm here"));

        InputStream get = storageInterface.get(null, null, run.getOutputFiles().get("xml"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("1\n3\n")
        );

        get = storageInterface.get(null, null, run.getOutputFiles().get("csv"));

        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is("2\n")
        );
    }

    @Test
    void outputDirs() throws Exception {
        Bash bash = configure(Bash.builder()
            .outputDirs(Property.of(Arrays.asList("xml", "csv")))
            .inputFiles(ImmutableMap.of("files/in/in.txt", "I'm here"))
            .commands(new String[]{
                "echo 1 >> {{ outputDirs.xml }}/file1.txt",
                "mkdir -p {{ outputDirs.xml }}/sub/sub2",
                "echo 2 >> {{ outputDirs.xml }}/sub/sub2/file2.txt",
                "echo 3 >> {{ outputDirs.csv }}/file1.txt",
            })
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdErrLineCount(), is(0));
        assertThat(run.getStdOutLineCount(), is(0));

        InputStream get = storageInterface.get(null, null, run.getOutputFiles().get("xml/file1.txt"));
        assertThat(CharStreams.toString(new InputStreamReader(get)), is("1\n"));

        get = storageInterface.get(null, null, run.getOutputFiles().get("xml/sub/sub2/file2.txt"));
        assertThat(CharStreams.toString(new InputStreamReader(get)), is("2\n"));

        get = storageInterface.get(null, null, run.getOutputFiles().get("csv/file1.txt"));
        assertThat(CharStreams.toString(new InputStreamReader(get)), is("3\n"));
    }
    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void failed() {
        Bash bash = configure(Bash.builder()
            .commands(new String[]{"echo 1 1>&2", "exit 66", "echo 2"})
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        RunnableTaskException bashException = assertThrows(RunnableTaskException.class, () -> {
            bash.run(runContext);
        });


        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getExitCode(), is(66));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getStdOutLineCount(), is(0));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getStdErrLineCount(), is(1));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void stopOnFirstFailed() {
        Bash bash = configure(Bash.builder()
            .commands(new String[]{"unknown", "echo 1"})
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        RunnableTaskException bashException = assertThrows(RunnableTaskException.class, () -> {
            bash.run(runContext);
        });

        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getExitCode(), is(127));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getStdOutLineCount(), is(0));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) bashException.getOutput()).getStdErrLineCount(), is(1));
    }
    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
    void dontStopOnFirstFailed() throws Exception {
        Bash bash = configure(Bash.builder()
            .commands(new String[]{"unknown", "echo 1"})
            .exitOnFailed(Property.of(false))
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(1));
    }

    @Test
    void useInputFiles() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("test.sh", "tst() { echo '::{\"outputs\": {\"extract\":\"testbash\"}}::' ; echo '{{workingDir}}'; }");

        List<String> commands = new ArrayList<>();
        commands.add("source {{workingDir}}/test.sh && tst");

        Bash bash = configure(Bash.builder()
            .interpreter(Property.of("/bin/bash"))
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("testbash"));
    }

    @Test
    void useInputFilesFromKestraFs() throws Exception {
        URL resource = AbstractBashTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        Map<String, String> files = new HashMap<>();
        files.put("test.sh", "cat fscontent.txt");
        files.put("fscontent.txt", put.toString());

        List<String> commands = new ArrayList<>();
        commands.add("cat fscontent.txt > {{ outputFiles.out }} ");

        Bash bash = configure(Bash.builder()
            .interpreter(Property.of("/bin/bash"))
            .commands(commands.toArray(String[]::new))
            .inputFiles(files)
            .outputFiles(Property.of(Collections.singletonList("out")))
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        InputStream get = storageInterface.get(null, null, run.getOutputFiles().get("out"));
        String outputContent = CharStreams.toString(new InputStreamReader(get));
        String fileContent = String.join("\n", Files.readAllLines(new File(resource.getPath()).toPath(), StandardCharsets.UTF_8));
        assertThat(outputContent, is(fileContent + "\n"));
    }

    @Test
    void useInputFilesAsVariable() throws Exception {
        URL resource = AbstractBashTest.class.getClassLoader().getResource("application.yml");

        URI put1 = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        URI put2 = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );


        Map<String, String> files = new HashMap<>();
        files.put("1.yml", put1.toString());
        files.put("2.yml", put2.toString());

        List<String> commands = new ArrayList<>();
        commands.add("cat 1.yml 2.yml > {{ outputFiles.out }} ");

        Bash bash = configure(Bash.builder()
            .interpreter(Property.of("/bin/bash"))
            .commands(commands.toArray(String[]::new))
            .inputFiles(JacksonMapper.ofJson().writeValueAsString(files))
            .outputFiles(Property.of(Collections.singletonList("out")))
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        InputStream get = storageInterface.get(null, null, run.getOutputFiles().get("out"));
        String outputContent = CharStreams.toString(new InputStreamReader(get));
        String fileContent = String.join("\n", Files.readAllLines(new File(resource.getPath()).toPath(), StandardCharsets.UTF_8));
        assertThat(outputContent, is(fileContent + "\n" + fileContent + "\n"));
    }

    @Test
    void preventRelativeFile() throws Exception {
        URL resource = AbstractBashTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        assertThrows(IllegalArgumentException.class, () -> {
            Bash bash = configure(Bash.builder()
                .commands(new String[]{"echo 1"})
                .inputFiles(Map.of(
                    "{{ inputs.vars }}", put.toString()
                ))
            ).build();

            RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of(
                "vars", "../../test.txt"
            ));

            bash.run(runContext);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            Bash bash = configure(Bash.builder()
                .commands(new String[]{"echo 1"})
                .inputFiles(Map.of(
                    "{{ inputs.vars }}", put.toString()
                ))
            ).build();

            RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of(
                "vars", "../../test.txt"
            ));

            bash.run(runContext);
        });

        // we allow dot file starting with a .
        Bash bash = configure(Bash.builder()
            .commands(new String[]{"echo 1"})
            .inputFiles(Map.of(
                "{{ inputs.vars }}", put.toString()
            ))
        ).build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of(
            "vars", ".test.txt"
        ));

        ScriptOutput run = bash.run(runContext);
        assertThat(run.getExitCode(), is(0));
    }

    static void controlOutputs(RunContext runContext, ScriptOutput run) {
        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        assertThat(AbstractBashTest.getMetrics(runContext, "count").getValue(), is(1D));
        assertThat(AbstractBashTest.getMetrics(runContext, "count2").getValue(), is(2D));
        assertThat(AbstractBashTest.getMetrics(runContext, "count2").getTags().size(), is(0));
        assertThat(AbstractBashTest.getMetrics(runContext, "count").getTags().size(), is(2));
        assertThat(AbstractBashTest.getMetrics(runContext, "count").getTags().get("tag1"), is("i"));
        assertThat(AbstractBashTest.getMetrics(runContext, "count").getTags().get("tag2"), is("win"));

        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer1").getValue().getNano(), greaterThan(0));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer1").getTags().size(), is(2));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag1"), is("i"));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag2"), is("lost"));

        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer2").getValue().getNano(), greaterThan(100000000));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer2").getTags().size(), is(2));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag1"), is("i"));
        assertThat(AbstractBashTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag2"), is("destroy"));
    }

    @SuppressWarnings("unchecked")
    static <T> AbstractMetricEntry<T> getMetrics(RunContext runContext, String name) {
        return (AbstractMetricEntry<T>) runContext.metrics()
            .stream()
            .filter(abstractMetricEntry -> abstractMetricEntry.getName().equals(name))
            .findFirst()
            .orElseThrow();
    }
}
