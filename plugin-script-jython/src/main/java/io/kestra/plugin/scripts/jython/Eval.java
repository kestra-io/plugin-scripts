package io.kestra.plugin.scripts.jython;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Jython script.",
    description = "This task is deprecated, please use `io.kestra.plugin.graalvm.js.Eval` instead."
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
@Deprecated
public class Eval extends io.kestra.plugin.scripts.jvm.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }
}
