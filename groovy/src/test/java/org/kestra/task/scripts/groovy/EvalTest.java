package org.kestra.task.scripts.groovy;

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
            .script("import org.kestra.core.models.executions.metrics.Counter\n" +
                "\n" +
                "logger.info('executionId: {}', runContext.render('{{ execution.id }}'))\n" +
                "runContext.metric(Counter.of('total', 666, 'name', 'bla'))\n" +
                "\n" +
                "map = Map.of('test', 'here')\n" +
                "File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + \"_\", \".out\")\n" +
                "var output = new FileOutputStream(tempFile)\n" +
                "output.write('555\\n666\\n'.getBytes())\n" +
                "\n" +
                "out = runContext.putTempFile(tempFile)"
            )
            .build();
    }
}
