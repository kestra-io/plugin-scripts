package io.kestra.plugin.scripts.python;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.python.internals.PythonEnvironmentManager;
import io.kestra.plugin.scripts.python.internals.PythonEnvironmentManager.ResolvedPythonEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.HashMap;
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
            title = "Execute a Python script and generate an output.",
            full = true,
            code = """
                id: python_demo
                namespace: company.team

                tasks:
                  - id: python
                    type: io.kestra.plugin.scripts.python.Script
                    dependencies:
                      - requests
                      - kestra
                    script: |
                      from kestra import Kestra
                      import requests

                      response = requests.get('https://kestra.io')
                      print(response.status_code)

                      Kestra.outputs({'status': response.status_code, 'text': response.text})
                """
        ),
        @Example(
            full = true,
            title = "Install pip packages before starting a Python Script task",
            code = """
                id: pip_packages_docker
                namespace: company.team

                tasks:
                  - id: run_python
                    type: io.kestra.plugin.scripts.python.Script
                    dependencies:
                      - requests
                    script: |
                      import requests
                      import json

                      response = requests.get("https://api.github.com")
                      data = response.json()
                      print(data)
                """
        ),
        @Example(
            title = "Log messages at different log levels using Kestra logger.",
            full = true,
            code = """
                id: python_logs
                namespace: company.team

                tasks:
                  - id: python_logger
                    type: io.kestra.plugin.scripts.python.Script
                    allowFailure: true
                    dependencies
                      - kestra
                    script: |
                      import time
                      from kestra import Kestra

                      logger = Kestra.logger()

                      logger.debug("DEBUG is used for diagnostic info.")
                      time.sleep(0.5)

                      logger.info("INFO confirms normal operation.")
                      time.sleep(0.5)

                      logger.warning("WARNING signals something unexpected.")
                      time.sleep(0.5)

                      logger.error("ERROR indicates a serious issue.")
                      time.sleep(0.5)

                      logger.critical("CRITICAL means a severe failure.")
                """
        ),
        @Example(
            title = "Execute a Python script with a file stored in Kestra's local storage created by a previous task.",
            full = true,
            code = """
                id: pass_data_between_tasks
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: python
                    type: io.kestra.plugin.scripts.python.Script
                    script: |
                      with open('{{ outputs.download.uri }}', 'r') as f:
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
                    outputFiles:
                      - "myfile.txt"
                    script: |
                      f = open("myfile.txt", "a")
                      f.write("Hello from a Kestra task!")
                      f.close()
                """
        ),
        @Example(
            full = true,
            title = """
            If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `outputFiles` property as shown in the example above. Files will be persisted in Kestra's internal storage. The first task in this example creates a file `'clean_dataset.csv'` and the next task can access it by leveraging the syntax `{{outputs.yourTaskId.outputFiles['yourFileName.fileExtension']}}`.
            """,
            code = """
                id: python_outputs
                namespace: company.team

                tasks:
                  - id: clean_dataset
                    type: io.kestra.plugin.scripts.python.Script
                    containerImage: ghcr.io/kestra-io/pydata:latest
                    outputFiles:
                      - "clean_dataset.csv"
                    dependencies:
                      - pandas
                    script: |
                      import pandas as pd
                      df = pd.read_csv("https://huggingface.co/datasets/kestra/datasets/raw/main/csv/messy_dataset.csv")

                      # Replace non-numeric age values with NaN
                      df["Age"] = pd.to_numeric(df["Age"], errors="coerce")

                      # mean imputation: fill NaN values with the mean age
                      mean_age = int(df["Age"].mean())
                      print(f"Filling NULL values with mean: {mean_age}")
                      df["Age"] = df["Age"].fillna(mean_age)
                      df.to_csv("clean_dataset.csv", index=False)

                  - id: read_file_from_python
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - head -n 10 {{ outputs.clean_dataset.outputFiles['clean_dataset.csv'] }}
                """
        ),
        @Example(
            full = true,
            title = """
            Create a Python inline script that takes input using an expression
            """,
            code = """
                id: python_use_input_in_inline
                namespace: company.team

                inputs:
                  - id: pokemon
                    type: STRING
                    defaults: pikachu

                  - id: your_age
                    type: INT
                    defaults: 25

                tasks:
                  - id: inline_script
                    type: io.kestra.plugin.scripts.python.Script
                    description: Fetch the pokemon detail and compare its experience
                    containerImage: ghcr.io/kestra-io/pydata:latest
                    dependencies:
                      - requests
                    script: |
                      import requests
                      import json

                      url = "https://pokeapi.co/api/v2/pokemon/{{ inputs.pokemon }}"
                      response = requests.get(url)

                      if response.status_code == 200:
                          pokemon = json.loads(response.text)
                          print(f"Base experience of {{ inputs.pokemon }} is { pokemon.get('base_experience') }")
                          if pokemon.get('base_experience') > int("{{ inputs.your_age }}"):
                              print("{{ inputs.pokemon }} has more base experience than your age")
                          else:
                              print("{{ inputs.pokemon}} is too young!")
                      else:
                          print(f"Failed to retrieve the webpage. Status code: {response.status_code}")
                """
        ),
        @Example(
            full = true,
            title = """
            Pass an input file to a Python script
            """,
            code = """
                id: python_input_file
                namespace: company.team

                tasks:
                  - id: download_file
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: get_total_rows
                    type: io.kestra.plugin.scripts.python.Script
                    dependencies:
                      - pandas
                    inputFiles:
                      input.csv: "{{ outputs.download_file.uri }}"
                    script: |
                      import pandas as pd

                      # Path to your CSV file
                      csv_file_path = "input.csv"

                      # Read the CSV file using pandas
                      df = pd.read_csv(csv_file_path)

                      # Get the number of rows
                      num_rows = len(df)

                      print(f"Number of rows: {num_rows}")
                """
        ),
        @Example(
            full = true,
            title = """
            Run a simple Python script to generate outputs and log them
            """,
            code = """
                id: python_generate_outputs
                namespace: company.team

                tasks:
                  - id: generate_output
                    type: io.kestra.plugin.scripts.python.Script
                    dependencies:
                      - kestra
                    script: |
                      from kestra import Kestra

                      marks = [79, 91, 85, 64, 82]
                      Kestra.outputs({"total_marks": sum(marks),"average_marks": sum(marks)/len(marks)})

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message:
                      - "Total Marks: {{ outputs.generate_output.vars.total_marks }}"
                      - "Average Marks: {{ outputs.generate_output.vars.average_marks }}"
                """
        )
    }
)
public class Script extends AbstractPythonExecScript implements RunnableTask<ScriptOutput> {

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `bash myscript.sh` or `python myscript.py`, use the `Commands` task instead."
    )
    @NotNull
    protected Property<String> script;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".py"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        PythonEnvironmentManager pythonEnvironmentManager = new PythonEnvironmentManager(runContext, this);
        ResolvedPythonEnvironment pythonEnvironment = pythonEnvironmentManager.setup(containerImage, taskRunner, runner);

        Map<String, String> env = new HashMap<>();
        env.put("PYTHONUNBUFFERED", "true");
        env.put("PIP_ROOT_USER_ACTION", "ignore");
        env.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");

        if (pythonEnvironment.packages() != null) {
            env.put("PYTHONPATH", pythonEnvironment.packages().path().toString());
        }

        ScriptOutput output = commands
            .addEnv(env)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.of(List.of(
                String.join(" ", pythonEnvironment.interpreter(), commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .withTargetOS(os)
            .run();

        // Cache upload
        if (pythonEnvironmentManager.isCacheEnabled() && pythonEnvironment.packages() != null && !pythonEnvironment.cached()) {
            pythonEnvironmentManager.uploadCache(runContext, pythonEnvironment.packages());
        }
        return output;
    }
}
