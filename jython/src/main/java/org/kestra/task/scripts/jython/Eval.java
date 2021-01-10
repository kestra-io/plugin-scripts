package org.kestra.task.scripts.jython;

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
    title = "Execute a jython script."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "outputs:",
                "  - out",
                "  - map",
                "script: |",
                "  from org.kestra.core.models.executions.metrics import Counter",
                "  import tempfile",
                "  from java.io import File",
                "  ",
                "  logger.info('executionId: {}', runContext.render('{{ execution.id }}'))",
                "  runContext.metric(Counter.of('total', 666, 'name', 'bla'))",
                "  ",
                "  map = {'test': 'here'}",
                "  tempFile = tempfile.NamedTemporaryFile()",
                "  tempFile.write('555\\n666\\n')",
                "  ",
                "  out = runContext.putTempFile(File(tempFile.name))"
            }
        )
    }
)
public class Eval extends org.kestra.task.scripts.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }
}
