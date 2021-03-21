package io.kestra.plugin.scripts.groovy;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.plugin.scripts.FileTransform;

import java.net.URI;

@MicronautTest
class FileTransformTest extends io.kestra.plugin.scripts.FileTransformTest {
    @Override
    protected FileTransform task(URI source) {
        return io.kestra.plugin.scripts.groovy.FileTransform.builder()
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
