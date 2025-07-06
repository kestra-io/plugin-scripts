package io.kestra.plugin.scripts.jython;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collection;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Transform an ION file from Kestra's internal storage with a Jython script.",
    description = "This task is deprecated, please use `io.kestra.plugin.graalvm.python.FileTransform` instead."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Extract data from an API, add a column, and store it as a downloadable CSV file.",
            code = """
                id: etl_api_to_csv
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.fs.http.Download
                    uri: https://gorest.co.in/public/v2/users

                  - id: ion_to_json
                    type: io.kestra.plugin.serdes.json.JsonToIon
                    from: "{{ outputs.download.uri }}"
                    newLine: false

                  - id: write_json
                    type: io.kestra.plugin.serdes.json.IonToJson
                    from: "{{ outputs.ion_to_json.uri }}"

                  - id: add_column
                    type: io.kestra.plugin.scripts.jython.FileTransform
                    from: "{{ outputs.write_json.uri }}"
                    script: |
                      from datetime import datetime
                      logger.info('row: {}', row)
                      row['inserted_at'] = datetime.utcnow()

                  - id: csv
                    type: io.kestra.plugin.serdes.csv.IonToCsv
                    from: "{{ outputs.add_column.uri }}"
                """
        ),
        @Example(
            title = "Transform with file from internal storage.",
            full = true,
            code = """
                id: jython_file_transform
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.jython.FileTransform
                    from: "{{ inputs.file }}"
                    script: |
                      logger.info('row: {}', row)

                      if row['name'] == 'richard':
                        row = None
                      else:
                        row['email'] = row['name'] + '@kestra.io'
                """
        ),
        @Example(
            title = "Create multiple rows from one row.",
            full = true,
            code = """
                id: jython_file_transform
                namespace: company.team

                inputs:
                  - id: file
                    type: FILE

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.jython.FileTransform
                    from: "{{ inputs.file }}"
                    script: |
                      logger.info('row: {}', row)
                      rows = [{"action": "insert"}, row]
                """
        ),
        @Example(
            title = "Transform with file from JSON string.",
            full = true,
            code = """
                id: jython_file_transform
                namespace: company.team

                inputs:
                  - id: json
                    type: JSON
                    defaults: {"name": "john"}

                tasks:
                  - id: file_transform
                    type: io.kestra.plugin.scripts.jython.FileTransform
                    from: "{{ inputs.json }}"
                    script: |
                      logger.info('row: {}', row)

                      if row['name'] == 'richard':
                        row = None
                      else:
                        row['lemail'] = row['name'] + '@kestra.io'
                """
        )
    }
)
@Deprecated
public class FileTransform extends io.kestra.plugin.scripts.jvm.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "python");
    }

    @Override
    protected Collection<Object> convertRows(Object rows) {
        //noinspection unchecked
        return (Collection<Object>) rows;
    }
}
