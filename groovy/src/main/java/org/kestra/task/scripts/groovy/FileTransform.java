package org.kestra.task.scripts.groovy;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.runners.RunContext;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(
    examples = {
        @Example(
            code = {
                "from: \"{{ outputs.avro-to-gcs }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row.get('name') == 'richard') {",
                "    row = null",
                "  } else {",
                "    row.put('email', row.get('name') + '@kestra.io')",
                "  }"
            }
        )
    }
)
public class FileTransform extends org.kestra.task.scripts.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "groovy");
    }
}
