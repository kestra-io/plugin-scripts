package io.kestra.plugin.scripts.groovy;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
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
            .script(Property.of("logger.info('row: {}', row)\n" +
                "sleep(1000)\n" +
                "if (row.get('name') == 'richard') {\n" +
                "  row = null\n" +
                "} else {\n" +
                "  row.put('email', row.get('name') + '@kestra.io')\n" +
                "}\n"
            ))
            .build();
    }

    @Override
    protected FileTransform multipleRows(String source) {
        return io.kestra.plugin.scripts.groovy.FileTransform.builder()
            .id("unit-test")
            .type(FileTransform.class.getName())
            .from(source)
            .concurrent(10)
            .script(Property.of("rows = [1, 2, row, [\"action\": \"insert\"]]\n"))
            .build();
    }
}
