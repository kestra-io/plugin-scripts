package io.kestra.plugin.scripts;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public abstract class FileTransformTest {
    @Inject
    protected RunContextFactory runContextFactory;

    @Inject
    protected StorageInterface storageInterface;

    abstract protected FileTransform task(URI source);

    @SuppressWarnings("unchecked")
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
            new URI("/" + IdUtils.create()),
            new FileInputStream(tempFile)
        );

        FileTransform task = this.task(source);

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        FileTransform.Output runOutput = task.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(runOutput.getUri())));
        List<Object> result = new ArrayList<>();
        FileSerde.reader(inputStream, result::add);

        AbstractMetricEntry<?> metric = runContext.metrics().get(0);
        assertThat(metric.getValue(), is(2D));

        assertThat(result.size(), is(2));
        assertThat(result.stream().filter(o -> ((Map<String, String>) o).get("id").equals("1")).findFirst().orElseThrow(), is(ImmutableMap.of(
            "id", "1",
            "name", "john",
            "email", "john@kestra.io"
        )));
        assertThat(result.stream().filter(o -> ((Map<String, String>) o).get("id").equals("2")).findFirst().orElseThrow(), is(ImmutableMap.of(
            "id", "2",
            "name", "jane",
            "email", "jane@kestra.io"
        )));
    }
}
