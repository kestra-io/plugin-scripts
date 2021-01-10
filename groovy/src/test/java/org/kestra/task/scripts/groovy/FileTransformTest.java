package org.kestra.task.scripts.groovy;

import io.micronaut.test.annotation.MicronautTest;
import org.kestra.task.scripts.FileTransform;

import java.net.URI;

@MicronautTest
class FileTransformTest extends org.kestra.task.scripts.FileTransformTest {
    @Override
    protected FileTransform task(URI source) {
        return org.kestra.task.scripts.groovy.FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source.toString())
            .script("logger.info('row: {}', row)\n" +
                "if (row.get('name') == 'richard') {\n" +
                "  row = null\n" +
                "} else {\n" +
                "  row.put('email', row.get('name') + '@kestra.io')\n" +
                "}\n"
            )
            .build();
    }
}
