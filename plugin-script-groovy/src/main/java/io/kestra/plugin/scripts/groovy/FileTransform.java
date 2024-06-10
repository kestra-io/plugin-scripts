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
            title = "Convert row by row of a file from Kestra's internal storage.",
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
            title = "Create multiple rows from one row.",
            code = {
                "from: \"{{ outputs['avro-to-gcs'] }}\"",
                "script: |",
                "  logger.info('row: {}', row)",
                "  rows = [[\"action\", \"insert\"], row]"
            }
        ),
        @Example(
            title = "Transform a JSON string to a file.",
            code = {
                "from: \"[{\\\"name\\\":\\\"jane\\\"}, {\\\"name\\\":\\\"richard\\\"}]\"",
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
            title = "JSON transformations using jackson library",
            full = true,
            code = """
id: json_transform_using_jackson
namespace: dev

tasks:
  - id: "file_transform"
    type: "io.kestra.plugin.scripts.groovy.FileTransform"
    from: "[{\"name\":\"John Doe\", \"age\":99, \"embedded\":{\"foo\":\"bar\"}}]"
    script: |
      import com.fasterxml.jackson.*

      def mapper = new databind.ObjectMapper();
      def jsonStr = mapper.writeValueAsString(row);
      logger.info('input in json str: {}', jsonStr)

      def typeRef = new core.type.TypeReference<HashMap<String,Object>>() {};

      data = mapper.readValue(jsonStr, typeRef);
  
      logger.info('json object: {}', data);
      logger.info('embedded field: {}', data.embedded.foo)
"""
        )
    }
)
public class FileTransform extends io.kestra.plugin.scripts.jvm.FileTransform {
    @Override
    public Output run(RunContext runContext) throws Exception {
        return this.run(runContext, "groovy");
    }
}
