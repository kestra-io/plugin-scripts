package io.kestra.plugins.scripts.go;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.go.Commands;
import io.kestra.plugin.scripts.go.Script;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    StorageInterface storageInterface;

    @Test
    void should_print_hello_there() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        var receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        var goScript = storageInterface.put(
            null,
            null,
            new URI("/file/storage/go_script.go"),
            IOUtils.toInputStream("""
                    package main
                    import "fmt"
                    func main() {
                        fmt.Println("hello there!")
                    }
                    """,
                StandardCharsets.UTF_8
            )
        );

        var inputFiles = new HashMap<>();
        inputFiles.put("go_script.go", goScript.toString());

        var commands = Commands.builder()
            .id("go_commands")
            .type(Script.class.getName())
            .inputFiles(inputFiles)
            .beforeCommands(Property.of(List.of(
                "go mod init go_commands",
                "go mod tidy"
            )))
            .commands(Property.of(List.of("go run go_script.go")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, commands, ImmutableMap.of());
        var run = commands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));

        /**
         * Those lines are displayed in the container even if a "go mod tidy" was added as a beforeCommand,
         * and even if we are in the same folder than the "go.mod" file and the script executes well, strange...
         *
         * 11:54:52.807 ERROR docker-java-stream-455560730 f.s.go_commands go: creating new go.mod: module go_commands
         * 11:54:52.860 ERROR docker-java-stream-455560730 f.s.go_commands go: to add module requirements and sums:
         * 11:54:52.972 ERROR docker-java-stream-455560730 f.s.go_commands 	go mod tidy
         */
        assertThat(run.getStdErrLineCount(), is(3));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("hello there!"));
        receive.blockLast();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }

    @Test
    void should_create_output_csv() throws Exception {

        var goScript = storageInterface.put(
            null,
            null,
            new URI("/file/storage/go_script.go"),
            IOUtils.toInputStream("""
                    package main
                    import (
                        "os"
                        "github.com/go-gota/gota/dataframe"
                        "github.com/go-gota/gota/series"
                    )
                    func main() {
                        names := series.New([]string{"Alice", "Bob", "Charlie"}, series.String, "Name")
                        ages := series.New([]int{25, 30, 35}, series.Int, "Age")
                        df := dataframe.New(names, ages)
                        file, _ := os.Create("output.csv")
                        df.WriteCSV(file)
                        defer file.Close()
                    }
                    """,
                StandardCharsets.UTF_8
            )
        );

        var inputFiles = new HashMap<>();
        inputFiles.put("go_script.go", goScript.toString());

        var outputFile = "output.csv";

        var commands = Commands.builder()
            .id("go_commands")
            .type(Script.class.getName())
            .inputFiles(inputFiles)
            .outputFiles(Property.of(List.of(outputFile)))
            .beforeCommands(Property.of(List.of(
                "go mod init go_commands",
                "go mod tidy"
            )))
            .commands(Property.of(List.of("go run go_script.go")))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, commands, ImmutableMap.of());
        var run = commands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getOutputFiles().containsKey(outputFile), is(true));

        var outputCsvInputStream = storageInterface.get(null, null, run.getOutputFiles().get(outputFile));
        assertThat(CharStreams.toString(new InputStreamReader(outputCsvInputStream)), is("""
            Name,Age
            Alice,25
            Bob,30
            Charlie,35
            """));
    }
}
