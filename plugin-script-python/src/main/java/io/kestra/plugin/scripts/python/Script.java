package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            title = "Execute a Python script.",
            full = true,
            code = """
                id: python_use_input_file
                namespace: company.team
                
                tasks:
                  - id: python
                    task: io.kestra.plugin.scripts.python.Script
                    script: |
                      from kestra import Kestra
                      import requests
                        
                      response = requests.get('https://google.com')
                      print(response.status_code)
                      
                      Kestra.outputs({'status': response.status_code, 'text': response.text})
                    beforeCommands:
                      - pip install requests kestra
                """
        ),
        @Example(
            title = "Execute a Python script with an input file from Kestra's local storage created by a previous task.",
            full = true,
            code = """
                id: python_use_input_file
                namespace: company.team
                
                tasks:
                  - id: python
                    task: io.kestra.plugin.scripts.python.Script
                    script: |
                      with open('{{ outputs.previousTaskId.uri }}', 'r') as f:
                        print(f.read())
                """
        ),
        @Example(
            title = "Execute a Python script that outputs a file.",
            full = true,
            code = """
                id: python_output_file
                namespace: company.team

                tasks:
                  - id: python
                    type: io.kestra.plugin.scripts.python.Script
                    script: |
                       f = open("{{ outputDir }}/myfile.txt", "a")
                       f.write("Hello from a Kestra task!")
                       f.close()   
                """
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{outputDir}}` expression. Files stored in that directory will be persisted in Kestra's internal storage. The first task in this example creates a file `'myfile.txt'` and the next task can access it by leveraging the syntax `{{outputs.yourTaskId.outputFiles['yourFileName.fileExtension']}}`.
            """,
            code = """     
                id: python_outputs
                namespace: company.team

                tasks:
                  - id: clean_dataset
                    type: io.kestra.plugin.scripts.python.Script
                    containerImage: ghcr.io/kestra-io/pydata:latest
                    script: |
                      import pandas as pd
                      df = pd.read_csv("https://huggingface.co/datasets/kestra/datasets/raw/main/csv/messy_dataset.csv")
                      
                      # Replace non-numeric age values with NaN
                      df["Age"] = pd.to_numeric(df["Age"], errors="coerce")
                
                      # mean imputation: fill NaN values with the mean age
                      mean_age = int(df["Age"].mean())
                      print(f"Filling NULL values with mean: {mean_age}")
                      df["Age"] = df["Age"].fillna(mean_age)
                      df.to_csv("{{ outputDir }}/clean_dataset.csv", index=False)
                
                  - id: readFileFromPython
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - head -n 10 {{ outputs.clean_dataset.outputFiles['clean_dataset.csv'] }}
                """
        )
    }
)
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/kestrapy:latest";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String script;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        List<String> internalToLocalFiles = new ArrayList<>();
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".py"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script, internalToLocalFiles)
        );
        commands = commands.withInputFiles(inputFiles);

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            String.join(" ", "python", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), this.targetOS)),
            this.targetOS
        );

        return commands
            .addEnv(Map.of(
                "PYTHONUNBUFFERED", "true",
                "PIP_ROOT_USER_ACTION", "ignore",
                "PIP_DISABLE_PIP_VERSION_CHECK", "1"
            ))
            .withCommands(commandsArgs)
            .run();
    }
}
