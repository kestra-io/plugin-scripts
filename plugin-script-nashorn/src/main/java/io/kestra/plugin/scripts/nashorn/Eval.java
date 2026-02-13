package io.kestra.plugin.scripts.nashorn;

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
    title = "Execute Nashorn (JavaScript) script",
    description = "Deprecated; use `io.kestra.plugin.graalvm.js.Eval` instead. Runs a rendered Nashorn script via the JVM Eval base; stdout/stderr not captured—log via `logger`."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: nashorn_eval
                namespace: company.team

                tasks:
                  - id: eval
                    type: io.kestra.plugin.scripts.nashorn.Eval
                    outputs:
                      - out
                      - map
                    script: |
                      var Counter = Java.type('io.kestra.core.models.executions.metrics.Counter');
                      var File = Java.type('java.io.File');
                      var FileOutputStream = Java.type('java.io.FileOutputStream');

                      logger.info('executionId: {}', runContext.render('{{ execution.id }}'));
                      runContext.metric(Counter.of('total', 666, 'name', 'bla'));

                      map = {'test': 'here'}
                      var tempFile = runContext.workingDir().createTempFile().toFile()
                      var output = new FileOutputStream(tempFile)
                      output.write('555\\n666\\n'.getBytes())

                      out = runContext.storage().putFile(tempFile)"
                """
        )
    }
)
@Deprecated
public class Eval extends io.kestra.plugin.scripts.jvm.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "nashorn");
    }
}
