package io.kestra.plugin.scripts.nashorn;


import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

import java.util.Arrays;

@MicronautTest
class EvalTest extends io.kestra.plugin.scripts.EvalTest {
    @Override
    protected io.kestra.plugin.scripts.Eval task() {
        return Eval.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .outputs(Arrays.asList("out", "map"))
            .script("var Counter = Java.type('io.kestra.core.models.executions.metrics.Counter');\n" +
                "var File = Java.type('java.io.File');\n" +
                "var FileOutputStream = Java.type('java.io.FileOutputStream');\n" +
                "\n" +
                "logger.info('executionId: {}', runContext.render('{{ execution.id }}'));\n" +
                "runContext.metric(Counter.of('total', 666, 'name', 'bla'));\n" +
                "\n" +
                "map = {'test': 'here'}\n" +
                "var tempFile = runContext.tempFile().toFile()\n" +
                "var output = new FileOutputStream(tempFile)\n" +
                "output.write('555\\n666\\n'.getBytes())\n" +
                "\n" +
                "out = runContext.putTempFile(tempFile)"
            )
            .build();
    }
}
