package org.kestra.task.scripts.groovy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.runners.RunContext;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a groovy script."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "outputs:",
                "  - out",
                "  - map",
                "script: |",
                "  import org.kestra.core.models.executions.metrics.Counter",
                "  ",
                "  logger.info('executionId: {}', runContext.render('{{ execution.id }}'))",
                "  runContext.metric(Counter.of('total', 666, 'name', 'bla'))",
                "  ",
                "  map = Map.of('test', 'here')",
                "  File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + \"_\", \".out\")",
                "  var output = new FileOutputStream(tempFile)",
                "  output.write('555\\n666\\n'.getBytes())",
                "  ",
                "  out = runContext.putTempFile(tempFile)"
            }
        )
    }
)
public class Eval extends org.kestra.task.scripts.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "groovy");
    }
}
