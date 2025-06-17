package io.kestra.plugin.scripts.jbang;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void script() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script jbangScript = Script.builder()
            .id(IdUtils.create())
            .type(Script.class.getName())
            .script(Property.of("""
                class helloworld {
                    public static void main(String[] args) {
                        if(args.length==0) {
                            System.out.println("Hello World!");
                        } else {
                            System.out.println("Hello " + args[0]);
                        }
                    }
                }"""
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, jbangScript, ImmutableMap.of());
        ScriptOutput run = jbangScript.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Hello World"));
        receive.blockLast();
    }
}
