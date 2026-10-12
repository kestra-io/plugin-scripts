package io.kestra.core.tasks.scripts;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class NodeTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"hello world\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());

        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getVars().get("extract"), is("hello world"));
        assertThat(run.getStdErrLineCount(), equalTo(0));
    }

    @Test
    void failed() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "process.exit(1)");

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());

        RunnableTaskException nodeException = assertThrows(RunnableTaskException.class, () ->
        {
            node.run(runContext);
        });

        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) nodeException.getOutput()).getExitCode(), is(1));
        assertThat(((io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) nodeException.getOutput()).getStdErrLineCount(), equalTo(0));
    }

    private Node requirementsNode(int status) {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", """
            const http = require('http');
            const axios = require('axios');
            const sockets = new Set();
            const server = http.createServer((req, res) => {
                res.writeHead(%d, { 'Content-Type': 'text/plain' });
                res.end('kestra-axios-fixture');
            });
            server.on('connection', socket => {
                sockets.add(socket);
                socket.on('close', () => sockets.delete(socket));
            });
            const deadline = setTimeout(() => {
                console.error('HTTP fixture timed out');
                process.exit(1);
            }, 15000);
            (async () => {
                try {
                    await new Promise((resolve, reject) => {
                        server.once('error', reject);
                        server.listen(0, '127.0.0.1', resolve);
                    });
                    const response = await axios.get('http://127.0.0.1:' + server.address().port, {
                        proxy: false,
                        timeout: 5000
                    });
                    if (response.status !== 200 || response.data !== 'kestra-axios-fixture') {
                        throw new Error('Unexpected HTTP fixture response');
                    }
                    console.log('::' + JSON.stringify({ outputs: { extract: String(response.status), body: response.data } }) + '::');
                } finally {
                    sockets.forEach(socket => socket.destroy());
                    await new Promise(resolve => server.close(resolve));
                    clearTimeout(deadline);
                }
            })().catch(error => {
                console.error(error.message);
                process.exitCode = 1;
            });
            """.formatted(status));
        files.put("package.json", "{\"dependencies\":{\"axios\":\"^0.20.0\"}}");
        return Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .npmPath(Property.ofValue("npm"))
            .inputFiles(files)
            .build();
    }

    @Test
    @Timeout(90)
    void requirements() throws Exception {
        Node node = requirementsNode(200);
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        ScriptOutput run = node.run(runContext);
        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("200"));
        assertThat(run.getVars().get("body"), is("kestra-axios-fixture"));
    }

    @Test
    @Timeout(90)
    void requirementsHttpFailureDoesNotEmitSuccess() throws Exception {
        Node node = requirementsNode(503);
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        var failure = assertThrows(RunnableTaskException.class, () -> node.run(runContext));
        var output = (io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) failure.getOutput();
        assertThat(output.getExitCode(), is(1));
        assertFalse(output.getVars().containsKey("extract"));
    }

    @Test
    @Timeout(90)
    void invalidRequirementsDoNotExecuteMain() throws Exception {
        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .npmPath(Property.ofValue("npm"))
            .inputFiles(
                Map.of(
                    "package.json", "{invalid json",
                    "main.js", "console.log('::{\"outputs\":{\"mainExecuted\":true}}::')"
                )
            )
            .build();
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        var failure = assertThrows(RunnableTaskException.class, () -> node.run(runContext));
        var output = (io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput) failure.getOutput();
        assertThat(output.getExitCode(), not(0));
        assertFalse(output.getVars().containsKey("mainExecuted"));
        assertThat(output.getStdOutLineCount(), is(0));
    }

    @Test
    void manyFiles() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('./otherfile').value) + '\"}}::')");
        files.put("otherfile.js", "module.exports.value = 'success'");

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("success"));
    }

    @Test
    void fileInSubFolders() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (require('fs').readFileSync('./sub/folder/file/test.txt', 'utf-8')) + '\"}}::')");
        files.put("sub/folder/file/test.txt", "OK");
        files.put("sub/folder/file/test1.txt", "OK");

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        ScriptOutput run = node.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getVars().get("extract"), is("OK"));
    }

    @Test
    void args() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("main.js", "console.log('::{\"outputs\": {\"extract\":\"' + (process.argv.slice(2).join(' ')) + '\"}}::')");

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .args(Property.ofValue(Arrays.asList("test", "param", "value")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of());
        ScriptOutput run = node.run(runContext);

        assertThat(run.getVars().get("extract"), is("test param value"));
    }

    @Test
    void outputs() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put(
            "main.js", "const Kestra = require(\"./kestra\");" +
                "Kestra.outputs({test: 'value', int: 2, bool: true, float: 3.65});" +
                "Kestra.counter('count', 1, {tag1: 'i', tag2: 'win'});" +
                "Kestra.counter('count2', 2);" +
                "Kestra.timer('timer1', (callback) => { setTimeout(callback, 1000) }, {tag1: 'i', tag2: 'lost'});" +
                "Kestra.timer('timer2', 2.12, {tag1: 'i', tag2: 'destroy'});"
        );

        Node node = Node.builder()
            .id("test-node-task")
            .type(Node.class.getName())
            .nodePath(Property.ofValue("node"))
            .inputFiles(files)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, node, ImmutableMap.of("test", "value"));
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

        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer1").getValue().toNanos(), greaterThan(0L));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer1").getTags().size(), is(2));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer1").getTags().get("tag1"), is("i"));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer1").getTags().get("tag2"), is("lost"));

        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer2").getValue().getNano(), greaterThan(100000000));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer2").getTags().size(), is(2));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer2").getTags().get("tag1"), is("i"));
        assertThat(NodeTest.<Duration> getMetrics(runContext, "timer2").getTags().get("tag2"), is("destroy"));
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
