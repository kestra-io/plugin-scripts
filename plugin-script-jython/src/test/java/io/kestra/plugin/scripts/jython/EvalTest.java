package io.kestra.plugin.scripts.jython;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

import java.util.Arrays;

@MicronautTest
class EvalTest extends io.kestra.plugin.scripts.jvm.EvalTest {
    @Override
    protected io.kestra.plugin.scripts.jvm.Eval task() {
        return Eval.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .outputs(Arrays.asList("out", "map"))
            .script("from io.kestra.core.models.executions.metrics import Counter\n" +
                "import tempfile\n" +
                "from java.io import File\n" +
                "\n" +
                "logger.info('executionId: {}', runContext.render('{{ execution.id }}'))\n" +
                "runContext.metric(Counter.of('total', 666, 'name', 'bla'))\n" +
                "\n" +
                "map = {'test': 'here'}\n" +
                "tempFile = tempfile.NamedTemporaryFile()\n" +
                "tempFile.write('555\\n666\\n')\n" +
                "\n" +
                "out = runContext.storage().putFile(File(tempFile.name))"
            )
            .build();
    }
}
