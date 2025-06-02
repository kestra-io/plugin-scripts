package io.kestra.plugin.scripts.nashorn;


import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;

@KestraTest
class FileTransformTest extends io.kestra.plugin.scripts.jvm.FileTransformTest {
    @Override
    protected FileTransform task(String source) {
        return FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source)
            .script(Property.ofValue("logger.info('row: {}', row)\n" +
                "if (row['name'] == 'richard') {\n" +
                "  row = null;\n" +
                "} else {\n" +
                "  row['email'] = row['name'] + '@kestra.io';\n" +
                "}\n"
            ))
            .build();
    }

    @Override
    protected io.kestra.plugin.scripts.jvm.FileTransform multipleRows(String source) {
        return FileTransform.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .from(source)
            .script(Property.ofValue("rows = [1, 2, row, {\"action\": \"insert\"}]\n"))
            .build();
    }
}
