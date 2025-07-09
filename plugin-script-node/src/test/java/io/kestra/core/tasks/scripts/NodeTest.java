package io.kestra.core.tasks.scripts;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class NodeTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"hello world\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("hello world"));
        assertThat(run.getStdErrLineCount(), equalTo(0));
    }

    @Test
    void
    failed() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "process.exit(1)");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .build();

        RunnableTaskException nodeException = assertThrows(RunnableTaskException.class, () -> {
            node.run(runContext);
        });

        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) nodeException.getOutput()).getExitCode(), is(1));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) nodeException.getOutput()).getStdErrLineCount(), equalTo(0));
    }

    @Test
    void requirements() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "require('axios').get('http://google.com').then(r => { console.log('::{\"outputs\": {\"extract\":\"' + r.status + '\"}}::') })");
        files.put("package.json", "{\"dependencies\":{\"axios\":\"^0.20.0\"}}");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .npmPath(Property.of("npm"))
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
    }

    @Test
    void manyFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('./otherfile').value) + '\"}}::')");
        files.put("otherfile.js", "module.exports.value = 'success'");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("success"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('fs').readFileSync('./sub/folder/file/test.txt', 'utf-8')) + '\"}}::')");
        files.put("sub/folder/file/test.txt", "OK");
        files.put("sub/folder/file/test1.txt", "OK");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("OK"));
    }

    @Test
    void args() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (process.argv.slice(2).join(' ')) + '\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .args(Property.of(Arrays.asList("test", "param", "value")))
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getVars().get("extract"), is("test param value"));
    }

    @Test
    void outputs() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of("test", "value"));
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "const Kestra = require(\"./kestra\");" +
            "Kestra.outputs({test: 'value', int: 2, bool: true, float: 3.65});" +
            "Kestra.counter('count', 1, {tag1: 'i', tag2: 'win'});" +
            "Kestra.counter('count2', 2);" +
            "Kestra.timer('timer1', (callback) => { setTimeout(callback, 1000) }, {tag1: 'i', tag2: 'lost'});" +
            "Kestra.timer('timer2', 2.12, {tag1: 'i', tag2: 'destroy'});"
        );

        Node node = Node.builder()
            .id("test-node-task")
            .nodePath(Property.of("node"))
            .inputFiles(files)
            .build();

        ScriptOutput run = node.run(runContext);

        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        assertThat(run.getVars().get("test"), is("value"));
        assertThat(run.getVars().get("int"), is(2));
        assertThat(run.getVars().get("bool"), is(true));
        assertThat(run.getVars().get("float"), is(3.65));

        assertThat(getMetrics(runContext, "count").getValue(), is(1D));
        assertThat(NodeTest.getMetrics(runContext, "count2").getValue(), is(2D));
        assertThat(NodeTest.getMetrics(runContext, "count2").getTags().size(), is(0));
        assertThat(NodeTest.getMetrics(runContext, "count").getTags().size(), is(2));
        assertThat(NodeTest.getMetrics(runContext, "count").getTags().get("tag1"), is("i"));
        assertThat(NodeTest.getMetrics(runContext, "count").getTags().get("tag2"), is("win"));

        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer1").getValue().getNano(), greaterThan(0));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer1").getTags().size(), is(2));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag1"), is("i"));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer1").getTags().get("tag2"), is("lost"));

        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer2").getValue().getNano(), greaterThan(100000000));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer2").getTags().size(), is(2));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag1"), is("i"));
        assertThat(NodeTest.<Duration>getMetrics(runContext, "timer2").getTags().get("tag2"), is("destroy"));
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
