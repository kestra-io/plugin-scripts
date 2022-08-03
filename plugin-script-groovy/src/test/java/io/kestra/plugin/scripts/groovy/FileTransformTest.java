package io.kestra.plugin.scripts.groovy;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.plugin.scripts.FileTransform;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class FileTransformTest extends io.kestra.plugin.scripts.FileTransformTest {
    @Override
    protected FileTransform task(URI source) {
        return io.kestra.plugin.scripts.groovy.FileTransform.builder()
            .id("unit-test")
            .type(FileTransform.class.getName())
            .from(source.toString())
            .concurrent(10)
            .script("logger.info('row: {}', row)\n" +
                "sleep(1000)\n" +
                "if (row.get('name') == 'richard') {\n" +
                "  row = null\n" +
                "} else {\n" +
                "  row.put('email', row.get('name') + '@kestra.io')\n" +
                "}\n"
            )
            .build();
    }


    @SuppressWarnings("unchecked")
    @Test
    void rows() throws Exception {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".trs");
        OutputStream output = new FileOutputStream(tempFile);

        FileSerde.write(output, ImmutableMap.of(
            "id", "1",
            "name", "john"
        ));

        URI source = storageInterface.put(
            new URI("/" + IdUtils.create()),
            new FileInputStream(tempFile)
        );

        FileTransform task = io.kestra.plugin.scripts.groovy.FileTransform.builder()
            .id("unit-test")
            .type(FileTransform.class.getName())
            .from(source.toString())
            .concurrent(10)
            .script("rows = [1,2,3, [\"action\": \"insert\"]]\n")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        FileTransform.Output runOutput = task.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(runOutput.getUri())));
        List<Object> result = new ArrayList<>();
        FileSerde.reader(inputStream, result::add);

        assertThat(result.size(), is(4));
        assertThat(result, hasItems(1, 2, 3));
    }
}
