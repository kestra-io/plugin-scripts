package io.kestra.plugin.scripts.jython;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(
    examples = {
        @Example(
            title = "Transform with file from internal storage",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if row['name'] == 'richard': ",
                "    row = None",
                "  else: ",
                "    row['email'] = row['name'] + '@kestra.io'\n"
            }
        ),
        @Example(
            title = "Transform with file from json string",
            code = {
                "from: \"[{\"name\":\"jane\"}, {\"name\":\"richard\"}]\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if row['name'] == 'richard': ",
                "    row = None",
                "  else: ",
                "    row['email'] = row['name'] + '@kestra.io'\n"
            }
        )
    }
)
public class FileTransform extends io.kestra.plugin.scripts.jvm.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }
}
