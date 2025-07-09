package io.kestra.plugin.scripts.groovy;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.scripts.jvm.FileTransform;

import java.util.UUID;

@KestraTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return io.kestra.plugin.scripts.groovy.FileTransform.builder()
            .id("groovy-transform-" + UUID.randomUUID())
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
            .id("groovy-transform-rows" + UUID.randomUUID())
            .type(FileTransform.class.getName())
            .from(source)
            .concurrent(10)
            .script(Property.of("rows = [1, 2, row, [\"action\": \"insert\"]]\n"))
            .build();
    }
}
