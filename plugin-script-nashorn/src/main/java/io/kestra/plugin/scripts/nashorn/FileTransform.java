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
            title = "Transform with file from internal storage",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row['name'] === 'richard') {",
                "    row = null",
                "  } else {",
                "    row['email'] = row['name'] + '@kestra.io')",
                "  }"
            }
        ),
        @Example(
            title = "Transform with file from json string",
            code = {
                "from: \"[{\"name\":\"jane\"}, {\"name\":\"richard\"}]\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  if (row['name'] === 'richard') {",
                "    row = null",
                "  } else {",
                "    row['email'] = row['name'] + '@kestra.io')",
                "  }"
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
