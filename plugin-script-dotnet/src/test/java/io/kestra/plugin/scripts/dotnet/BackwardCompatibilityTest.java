package io.kestra.plugin.scripts.dotnet;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlParser;
import io.kestra.plugin.scripts.csharp.Commands;
import io.kestra.plugin.scripts.csharp.CommandsTrigger;
import io.kestra.plugin.scripts.csharp.Script;
import io.kestra.plugin.scripts.csharp.ScriptTrigger;

import static org.assertj.core.api.Assertions.assertThat;

class BackwardCompatibilityTest {

    @Test
    void oldDotnetScriptTypeDeserializesToCSharpScript() {
        Flow flow = parseFlow("""
            id: backward-compatibility
            namespace: company.team
            tasks:
              - id: script
                type: io.kestra.plugin.scripts.dotnet.Script
                script: |
                  System.Console.WriteLine("Hello");
            """);

        assertThat(flow.getTasks()).hasSize(1);
        assertThat(flow.getTasks().get(0).getClass()).isEqualTo(Script.class);
    }

    @Test
    void oldDotnetCommandsTypeDeserializesToCSharpCommands() {
        Flow flow = parseFlow("""
            id: backward-compatibility
            namespace: company.team
            tasks:
              - id: commands
                type: io.kestra.plugin.scripts.dotnet.Commands
                commands:
                  - dotnet --version
            """);

        assertThat(flow.getTasks()).hasSize(1);
        assertThat(flow.getTasks().get(0).getClass()).isEqualTo(Commands.class);
    }

    @Test
    void oldDotnetScriptTriggerTypeDeserializesToCSharpScriptTrigger() {
        Flow flow = parseFlow("""
            id: backward-compatibility
            namespace: company.team
            triggers:
              - id: script-trigger
                type: io.kestra.plugin.scripts.dotnet.ScriptTrigger
                interval: PT1M
                exitCondition: "exit 1"
                script: |
                  System.Environment.Exit(1);
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: compatibility-test
            """);

        assertThat(flow.getTriggers()).hasSize(1);
        assertThat(flow.getTriggers().get(0).getClass()).isEqualTo(ScriptTrigger.class);
    }

    @Test
    void oldDotnetCommandsTriggerTypeDeserializesToCSharpCommandsTrigger() {
        Flow flow = parseFlow("""
            id: backward-compatibility
            namespace: company.team
            triggers:
              - id: commands-trigger
                type: io.kestra.plugin.scripts.dotnet.CommandsTrigger
                interval: PT1M
                exitCondition: "exit 1"
                commands:
                  - exit 1
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: compatibility-test
            """);

        assertThat(flow.getTriggers()).hasSize(1);
        assertThat(flow.getTriggers().get(0).getClass()).isEqualTo(CommandsTrigger.class);
    }

    private static Flow parseFlow(String source) {
        return YamlParser.parse(source, Flow.class);
    }
}