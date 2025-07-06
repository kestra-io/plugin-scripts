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
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.go.Script;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class ScriptTest {
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

        var script = Script.builder()
            .id("go_script")
            .type(Script.class.getName())
            .allowWarning(true)
            .script(Property.ofValue("""
                    package main
                    import "fmt"
                    func main() {
                        fmt.Println("hello there!")
                    }
                """))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, script, ImmutableMap.of());
        var run = script.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("hello there!"));
        receive.blockLast();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }

    @Test
    void should_create_output_csv() throws Exception {
        var outputFile = "output.csv";
        var script = Script.builder()
            .id("go_script")
            .type(Script.class.getName())
            .allowWarning(true)
            .script(Property.ofValue("""
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
                """))
            .outputFiles(Property.ofValue(List.of(outputFile)))
            .beforeCommands(Property.ofValue(List.of(
                "go mod init go_script",
                "go get github.com/go-gota/gota",
                "go mod tidy"
            )))
            .build();

        var runContext = TestsUtils.mockRunContext(runContextFactory, script, ImmutableMap.of());
        var run = script.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getOutputFiles().containsKey(outputFile), is(true));

        var outputCsvInputStream = storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get(outputFile));
        assertThat(CharStreams.toString(new InputStreamReader(outputCsvInputStream)), is("""
            Name,Age
            Alice,25
            Bob,30
            Charlie,35
            """));
    }
}
