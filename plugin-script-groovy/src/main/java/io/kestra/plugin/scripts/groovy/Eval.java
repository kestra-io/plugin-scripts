package io.kestra.plugin.scripts.groovy;

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
    title = "Execute a Groovy script."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Make an API call and pass request body to a Groovy script.",
            code = """     
    id: api-request-to-groovy
    namespace: dev

    tasks:
      - id: request
        type: io.kestra.plugin.fs.http.Request
        uri: "https://dummyjson.com/products/1"

      - id: groovy
        type: io.kestra.plugin.scripts.groovy.Eval
        script: |
          logger.info('{{ outputs.request.body }}')

      - id: download
        type: io.kestra.plugin.fs.http.Download
        uri: "https://dummyjson.com/products/1"

      - id: runContextGroovy
        type: io.kestra.plugin.scripts.groovy.Eval
        script: |
          // logger.info('Vars: {}', runContext.getVariables())
          URI uri = new URI(runContext.variables.outputs.download.uri)
          InputStream istream = runContext.storage().getFile(uri)
          logger.info('Content: {}', istream.text)
                    """
            ),        
        @Example(
            code = {
                "outputs:",
                "  - out",
                "  - map",
                "script: |",
                "  import io.kestra.core.models.executions.metrics.Counter",
                "  ",
                "  logger.info('executionId: {}', runContext.render('{{ execution.id }}'))",
                "  runContext.metric(Counter.of('total', 666, 'name', 'bla'))",
                "  ",
                "  map = Map.of('test', 'here')",
                "  File tempFile = runContext.tempFile().toFile()",
                "  var output = new FileOutputStream(tempFile)",
                "  output.write('555\\n666\\n'.getBytes())",
                "  ",
                "  out = runContext.storage().putFile(tempFile)"
            }
        )
    }
)
public class Eval extends io.kestra.plugin.scripts.jvm.Eval {
    @Override
    public Eval.Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "groovy");
    }
}
