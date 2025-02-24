package io.kestra.plugin.scripts.python;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.models.tasks.runners.TargetOS;
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
            title = "Execute a Python script and generate an output.",
            full = true,
            code = """
                id: python_demo
                namespace: company.team

                tasks:
                  - id: python
                    type: io.kestra.plugin.scripts.python.Script
                    beforeCommands:
                      - pip install requests
                    script: |
                      from kestra import Kestra
                      import requests

                      response = requests.get('https://kestra.io')
                      print(response.status_code)

                      Kestra.outputs({'status': response.status_code, 'text': response.text})
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
                    warningOnStdErr: false
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
        )
    }
)
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/kestrapy:latest";

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

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

        return commands
            .addEnv(Map.of(
                "PYTHONUNBUFFERED", "true",
                "PIP_ROOT_USER_ACTION", "ignore",
                "PIP_DISABLE_PIP_VERSION_CHECK", "1"
                ))
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.of(List.of(
                String.join(" ", "python", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .withTargetOS(os)
            .run();
    }
}
