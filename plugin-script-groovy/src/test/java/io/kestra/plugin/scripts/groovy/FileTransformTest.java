package io.kestra.plugin.scripts.groovy;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.scripts.jvm.FileTransform;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return io.kestra.plugin.scripts.groovy.FileTransform.builder()
            .id("unit-test")
            .type(FileTransform.class.getName())
            .from(source)
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
            null,
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

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(null, runOutput.getUri())));
        List<Object> result = new ArrayList<>();
        FileSerde.reader(inputStream, result::add);

        assertThat(result.size(), is(4));
        assertThat(result, hasItems(1, 2, 3));
    }
}
