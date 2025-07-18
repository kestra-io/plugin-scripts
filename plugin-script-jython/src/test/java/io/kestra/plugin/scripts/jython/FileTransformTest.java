package io.kestra.plugin.scripts.jython;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.scripts.jvm.FileTransform;

import java.util.UUID;

@KestraTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return io.kestra.plugin.scripts.jython.FileTransform.builder()
            .id("jython-transform-" + UUID.randomUUID())
            .type(io.kestra.plugin.scripts.jython.FileTransform.class.getName())
            .from(source)
            .script(Property.of("logger.info('row: {}', row)\n" +
                "if row['name'] == 'richard': \n" +
                "  row = None\n" +
                "else: \n" +
                "  row['email'] = row['name'] + '@kestra.io'\n"
            ))
            .build();
    }

    @Override
    protected FileTransform multipleRows(String source) {
        return io.kestra.plugin.scripts.jython.FileTransform.builder()
            .id("jython-transform-rows" + UUID.randomUUID())
            .type(io.kestra.plugin.scripts.jython.FileTransform.class.getName())
            .from(source)
            .script(Property.of("rows = [1, 2 , row, {\"action\": \"insert\"}]\n"))
            .build();
    }
}
