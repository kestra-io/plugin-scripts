package io.kestra.plugin.scripts.nashorn;

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
    title = "Transform ion format file from kestra with a nashorn (javascript) script."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "outputs:",
                "  - out",
                "  - map",
                "script: |",
                "  var Counter = Java.type('io.kestra.core.models.executions.metrics.Counter');",
                "  var File = Java.type('java.io.File');",
                "  var FileOutputStream = Java.type('java.io.FileOutputStream');",
                "",
                "  logger.info('executionId: {}', runContext.render('{{ execution.id }}'));",
                "  runContext.metric(Counter.of('total', 666, 'name', 'bla'));",
                "  ",
                "  map = {'test': 'here'}",
                "  var tempFile = runContext.tempFile()",
                "  var output = new FileOutputStream(tempFile)",
                "  output.write('555\\n666\\n'.getBytes())",
                "",
                "  out = runContext.putTempFile(tempFile)"
            }
        )
    }
)
public class FileTransform extends io.kestra.plugin.scripts.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "nashorn");
    }
}
