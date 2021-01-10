package org.kestra.task.scripts.jython;

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
                "  if row['name'] == 'richard': ",
                "    row = None",
                "  else: ",
                "    row['email'] = row['name'] + '@kestra.io'\n"
            }
        )
    }
)
public class FileTransform extends org.kestra.task.scripts.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }
}
