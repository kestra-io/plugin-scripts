package io.kestra.plugin.scripts.nashorn;


import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source)
            .script("logger.info('row: {}', row)\n" +
                "if (row['name'] == 'richard') {\n" +
                "  row = null;\n" +
                "} else {\n" +
                "  row['email'] = row['name'] + '@kestra.io';\n" +
                "}\n"
            )
            .build();
    }
}
