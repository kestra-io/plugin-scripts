package org.kestra.task.scripts.jython;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

import java.util.Arrays;

@MicronautTest
class EvalTest extends org.kestra.task.scripts.EvalTest {
    @Override
    protected org.kestra.task.scripts.Eval task() {
        return Eval.builder()
            .id("unit-test")
            .type(Eval.class.getName())
            .outputs(Arrays.asList("out", "map"))
            .script("from org.kestra.core.models.executions.metrics import Counter\n" +
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
                "out = runContext.putTempFile(File(tempFile.name))"
            )
            .build();
    }
}
