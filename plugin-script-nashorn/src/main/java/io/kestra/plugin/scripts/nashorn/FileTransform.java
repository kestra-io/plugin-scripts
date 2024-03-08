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
    title = "Transform ion format file from Kestra's internal storage with a Nashorn (JavaScript) script."
)
@Plugin(
    examples = {
        @Example(
            title = "Convert row by row of a file from Kestra's internal storage.",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  # remove a column",
                "  delete row['useless_column']",
                "  # update a column",
                "  row['email'] = row['name'] + '@kestra.io'",
                "  # set a column to null",
                "  row['last_update'] = null"
            }
        ),
        @Example(
            title = "Transform JSON string input with a Nashorn script.",
            code = {
                "from: \"[{\\\"name\":\\\"jane\\\"}, {\\\"name\\\":\\\"richard\\\"}]\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row['name'] === 'richard') {",
                "    row = null",
                "  } else {",
                "    row['email'] = row['name'] + '@kestra.io'",
                "  }"
            }
        )
    }
)
public class FileTransform extends io.kestra.plugin.scripts.jvm.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "nashorn");
    }
}
