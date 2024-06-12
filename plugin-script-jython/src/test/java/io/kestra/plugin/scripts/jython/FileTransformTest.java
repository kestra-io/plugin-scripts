package io.kestra.plugin.scripts.jython;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.scripts.jvm.FileTransform;

@KestraTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
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
