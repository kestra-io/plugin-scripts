package io.kestra.plugin.scripts.jvm;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.scripts.jvm.FileTransform;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class FileTransformTest {
    @Inject
    protected RunContextFactory runContextFactory;

    @Inject
    protected StorageInterface storageInterface;

    abstract protected FileTransform task(String source);

    abstract protected FileTransform multipleRows(String source);

    @Test
    void run() throws Exception {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".trs");
        OutputStream output = new FileOutputStream(tempFile);

        FileSerde.write(output, ImmutableMap.of(
            "id", "1",
            "name", "john"
        ));

        FileSerde.write(output, ImmutableMap.of(
            "id", "2",
            "name", "jane"
        ));

        FileSerde.write(output, ImmutableMap.of(
            "id", "3",
            "name", "richard"
        ));

        URI source = storageInterface.put(
            null,
            new URI("/" + IdUtils.create()),
            new FileInputStream(tempFile)
        );

        test(source.toString(), 2);
    }

    @Test
    void runWithArrayJsonString() throws Exception {
        test(JacksonMapper.ofJson().writeValueAsString(
            Arrays.asList(
                ImmutableMap.of(
                    "id", "1",
                    "name", "john"
                ),
                ImmutableMap.of(
                    "id", "2",
                    "name", "jane"
                ),
                ImmutableMap.of(
                    "id", "3",
                    "name", "richard"
                )
            )
        ), 2);
    }

    @Test
    void runWithJsonString() throws Exception {
        test(JacksonMapper.ofJson().writeValueAsString(
            ImmutableMap.of(
                "id", "1",
                "name", "john"
            )

        ), 1);
    }

    @SuppressWarnings("unchecked")
    void test(String source, int size) throws Exception {
        FileTransform task = this.task(source);

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        FileTransform.Output runOutput = task.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(null, runOutput.getUri())));
        List<Object> result = new ArrayList<>();
        FileSerde.reader(inputStream, result::add);

        AbstractMetricEntry<?> metric = runContext.metrics().get(0);
        assertThat(metric.getValue(), is((double)size));

        assertThat(result.size(), is(size));
        assertThat(result.stream().filter(o -> ((Map<String, String>) o).get("id").equals("1")).findFirst().orElseThrow(), is(ImmutableMap.of(
            "id", "1",
            "name", "john",
            "email", "john@kestra.io"
        )));

        if (size > 1) {
            assertThat(result.stream().filter(o -> ((Map<String, String>) o).get("id").equals("2")).findFirst().orElseThrow(), is(ImmutableMap.of(
                "id", "2",
                "name", "jane",
                "email", "jane@kestra.io"
            )));
        }
    }

    @Test
    void rows() throws Exception {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".trs");
        OutputStream output = new FileOutputStream(tempFile);

        var map = Map.of(
            "id", "1",
            "name", "john"
        );

        FileSerde.write(output, map);

        URI source = storageInterface.put(
            null,
            new URI("/" + IdUtils.create()),
            new FileInputStream(tempFile)
        );

        io.kestra.plugin.scripts.jvm.FileTransform task = this.multipleRows(source.toString());

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        io.kestra.plugin.scripts.jvm.FileTransform.Output runOutput = task.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(null, runOutput.getUri())));
        List<Object> result = new ArrayList<>();
        FileSerde.reader(inputStream, result::add);

        assertThat(result.size(), is(4));
        assertThat(result, hasItems(1, 2));
        assertThat(result.get(2), is(map));
        assertThat(result.get(3), is(Map.of("action", "insert")));
    }
}
