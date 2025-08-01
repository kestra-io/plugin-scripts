package io.kestra.plugin.scripts.nashorn;


import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;

import java.util.Arrays;
import java.util.UUID;

@KestraTest
class EvalTest extends io.kestra.plugin.scripts.jvm.EvalTest {
    @Override
    protected io.kestra.plugin.scripts.jvm.Eval task() {
        return Eval.builder()
            .id("nashorn-eval-" + UUID.randomUUID())
            .type(Eval.class.getName())
            .outputs(Property.of(Arrays.asList("out", "map")))
            .script(new Property<>("var Counter = Java.type('io.kestra.core.models.executions.metrics.Counter');\n" +
                "var File = Java.type('java.io.File');\n" +
                "var FileOutputStream = Java.type('java.io.FileOutputStream');\n" +
                "\n" +
                "logger.info('executionId: {}', runContext.render('{{ execution.id }}'));\n" +
                "runContext.metric(Counter.of('total', 666, 'name', 'bla'));\n" +
                "\n" +
                "map = {'test': 'here'}\n" +
                "var tempFile = runContext.workingDir().createTempFile().toFile()\n" +
                "var output = new FileOutputStream(tempFile)\n" +
                "output.write('555\\n666\\n'.getBytes())\n" +
                "\n" +
                "out = runContext.storage().putFile(tempFile)"
            ))
            .build();
    }
}
