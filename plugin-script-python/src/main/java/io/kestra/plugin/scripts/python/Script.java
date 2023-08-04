package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Python script."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Python script",
            code = {
                "script: |",
                "  from kestra import Kestra",
                "  import requests",
                "",
                "  response = requests.get('https://google.com')",
                "  print(response.status_code)",
                "",
                "  Kestra.outputs({'status': response.status_code, 'text': response.text})",
                "beforeCommands:",
                "  - pip install requests kestra"
            }
        ),
        @Example(
            title = "Execute a Python script with an input file from Kestra's local storage created by a previous task.",
            code = {
                "script:",
                "  with open('{{ outputs.previousTaskId.uri }}', 'r') as f:",
                "    print(f.read())"
            }
        ),
        @Example(
            title = "Execute a Python script that outputs a file",
            code = {
                "script: |",
                "   f = open(\"{{outputDir}}/myfile.txt\", \"a\")",
                "   f.write(\"I can output files from my script!\")",
                "   f.close()"
            }
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{outputDir}}` [variable](https://kestra.io/docs/developer-guide/03.variables/). Files stored in that directory will be persisted in Kestra's internal storage. The first task in this example creates a file `'myfile.txt'` and the next task can access it by leveraging the syntax `{{outputs.yourTaskId.outputFiles['yourFileName.fileExtension']}}`. Check the [Outputs](https://kestra.io/docs/developer-guide/outputs) page for more details about managing outputs.
            """,
            code = """     
id: outputsPython
namespace: dev
tasks:
  - id: cleanDataset
    type: io.kestra.plugin.scripts.python.Script
    docker:
      image: ghcr.io/kestra-io/pydata:latest
    script: |
      import pandas as pd
      df = pd.read_csv("https://raw.githubusercontent.com/kestra-io/datasets/main/csv/messy_dataset.csv")
      
      # Replace non-numeric age values with NaN
      df["Age"] = pd.to_numeric(df["Age"], errors="coerce")

      # mean imputation: fill NaN values with the mean age
      mean_age = int(df["Age"].mean())
      print(f"Filling NULL values with mean: {mean_age}")
      df["Age"] = df["Age"].fillna(mean_age)
      df.to_csv("{{outputDir}}/clean_dataset.csv", index=False)

  - id: readFileFromPython
    type: io.kestra.plugin.scripts.shell.Commands
    runner: PROCESS
    commands:
      - head -n 10 {{outputs.cleanDataset.outputFiles['clean_dataset.csv']}}
                """
        )
    }
)
public class Script extends AbstractExecScript {
    @Schema(
        title = "Docker options when using the `DOCKER` runner"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder()
        .image("python")
        .build();

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    protected String script;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Path path = runContext.tempFile(
            ScriptService.replaceInternalStorage(runContext, runContext.render(this.script, commands.getAdditionalVars())).getBytes(StandardCharsets.UTF_8),
            ".py"
        );

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            String.join(" ", "python", path.toAbsolutePath().toString())
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
