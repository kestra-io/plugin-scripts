package io.kestra.plugin.scripts.dotnet;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlParser;
import io.kestra.plugin.scripts.csharp.Script;

import static org.assertj.core.api.Assertions.assertThat;

class BackwardCompatibilityTest {

    @Test
    void oldDotnetScriptTypeDeserializesToCSharpScript() {
        String source = """
            id: backward-compatibility
            namespace: company.team
            tasks:
              - id: script
                type: io.kestra.plugin.scripts.dotnet.Script
                script: |
                  System.Console.WriteLine("Hello");
            """;

        Flow flow = YamlParser.parse(source, Flow.class);

        assertThat(flow.getTasks()).hasSize(1);
        assertThat(flow.getTasks().get(0).getClass()).isEqualTo(Script.class);
    }
}