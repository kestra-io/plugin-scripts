package io.kestra.plugin.scripts.jython;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(
    title = "Transform ion format file from Kestra's internal storage with a Jython script."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Extract data from an API, add a column, and store it as a downloadable CSV file.",
            code = """     
id: etl-api-to-csv
namespace: dev

tasks:
  - id: download
    type: io.kestra.plugin.fs.http.Download
    uri: https://gorest.co.in/public/v2/users

  - id: ionToJSON
    type: "io.kestra.plugin.serdes.json.JsonReader"
    from: "{{outputs.download.uri}}"
    newLine: false

  - id: writeJSON
    type: io.kestra.plugin.serdes.json.JsonWriter
    from: "{{outputs.ionToJSON.uri}}"

  - id: addColumn
    type: io.kestra.plugin.scripts.jython.FileTransform
    from: "{{outputs.writeJSON.uri}}"
    script: |
      from datetime import datetime
      logger.info('row: {}', row)
      row['inserted_at'] = datetime.utcnow()

  - id: csv
    type: io.kestra.plugin.serdes.csv.CsvWriter
    from: "{{outputs.addColumn.uri}}"
"""
        ),           
        @Example(
            title = "Convert row by row of a file from Kestra's internal storage.",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "",
                "  // remove a column",
                "  del row['useless_column']",
                "  // update a column",
                "  row['email'] = row['name'] + '@kestra.io'",
                "  // set a column to null",
                "  row['last_update'] = None"
            }
        ),
        @Example(
            title = "Transform with file from JSON string.",
            code = {
                "from: \"[{\\\"name\\\":\\\"jane\\\"}, {\\\"name\\\":\\\"richard\\\"}]\"",
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
