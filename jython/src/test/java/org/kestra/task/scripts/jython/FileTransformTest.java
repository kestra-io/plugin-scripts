package org.kestra.task.scripts.jython;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.kestra.task.scripts.FileTransform;

import java.net.URI;

@MicronautTest
class FileTransformTest extends org.kestra.task.scripts.FileTransformTest {
    @Override
    protected FileTransform task(URI source) {
        return org.kestra.task.scripts.jython.FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source.toString())
            .script("logger.info('row: {}', row)\n" +
                "if row['name'] == 'richard': \n" +
                "  row = None\n" +
                "else: \n" +
                "  row['email'] = row['name'] + '@kestra.io'\n"
            )
            .build();
    }
}
