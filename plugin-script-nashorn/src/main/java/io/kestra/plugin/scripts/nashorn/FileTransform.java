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
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.Collection;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Transform an ION file from Kestra's internal storage with a Nashorn (JavaScript) script.",
    description = "This task is deprecated, please use `io.kestra.plugin.graalvm.js.FileTransform` instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Transform with file from internal storage",
            full = true,
            code = """
                id: nashorn_file_transform
                namespace: company.team

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.nashorn.FileTransform
                    from: "{{ outputs['avro-to-gcs'] }}"
                    script: |
                      logger.info('row: {}', row)

                      if (row['name'] === 'richard') {
                        row = null
                      } else {
                        row['email'] = row['name'] + '@kestra.io'
                      }
                """
        ),
        @Example(
            title = "Create multiple rows from one row.",
            full = true,
            code = """
                id: nashorn_file_transform
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.nashorn.FileTransform
                    from: "{{ inputs.file }}"
                    script: |
                      logger.info('row: {}', row)
                      rows = [{"action": "insert"}, row]
                """
        ),
        @Example(
            title = "Transform JSON string input with a Nashorn script.",
            full = true,
            code = """
                id: nashorn_file_transform
                namespace: company.team

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.nashorn.FileTransform
                    from: "[{\"name":\"jane\"}, {\"name\":\"richard\"}]"
                    script: |
                      logger.info('row: {}', row)

                      if (row['name'] === 'richard') {
                        row = null
                      } else {
                        row['email'] = row['name'] + '@kestra.io'
                      }
                """
        )
    }
)
@Deprecated
public class FileTransform extends io.kestra.plugin.scripts.jvm.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "nashorn");
    }

    @Override
    protected Collection<Object> convertRows(Object rows) {
        return ((ScriptObjectMirror) rows).values();
    }
}
