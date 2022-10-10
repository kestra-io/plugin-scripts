package io.kestra.plugin.scripts.groovy;

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
            title = "Convert every row by row with file from internal storage",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row.get('name') == 'richard') {",
                "    row = null",
                "  } else {",
                "    row.put('email', row.get('name') + '@kestra.io')",
                "  }"
            }
        ),
        @Example(
            title = "Create multiple rows from one row",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "  rows = [[\"action\", \"insert\"], row]"
            }
        ),
        @Example(
            title = "Transform with file from json string",
            code = {
                "from: \"[{\"name\":\"jane\"}, {\"name\":\"richard\"}]\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row.get('name') == 'richard') {",
                "    row = null",
                "  } else {",
                "    row.put('email', row.get('name') + '@kestra.io')",
                "  }"
            }
        ),
    }
)
public class FileTransform extends io.kestra.plugin.scripts.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "groovy");
    }
}
