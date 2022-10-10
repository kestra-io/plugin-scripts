package io.kestra.plugin.scripts.jython;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.plugin.scripts.FileTransform;

import java.net.URI;

@MicronautTest
class FileTransformTest extends io.kestra.plugin.scripts.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return io.kestra.plugin.scripts.jython.FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source)
            .script("logger.info('row: {}', row)\n" +
                "if row['name'] == 'richard': \n" +
                "  row = None\n" +
                "else: \n" +
                "  row['email'] = row['name'] + '@kestra.io'\n"
            )
            .build();
    }
}
