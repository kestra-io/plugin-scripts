package io.kestra.plugin.scripts.jython;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Jython script."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: jython_eval
                namespace: company.team

                tasks:
                  - id: eval
                    type: io.kestra.plugin.scripts.jython.Eval
                    outputs:
                      - out
                      - map
                    script: |
                      from io.kestra.core.models.executions.metrics import Counter
                      import tempfile
                      from java.io import File
                      
                      logger.info('executionId: {}', runContext.render('{{ execution.id }}'))
                      runContext.metric(Counter.of('total', 666, 'name', 'bla'))
                      
                      map = {'test': 'here'}
                      tempFile = tempfile.NamedTemporaryFile()
                      tempFile.write('555\\n666\\n')
                      
                      out = runContext.storage().putFile(File(tempFile.name)
                """
        )
    }
)
public class Eval extends io.kestra.plugin.scripts.jvm.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }
}
