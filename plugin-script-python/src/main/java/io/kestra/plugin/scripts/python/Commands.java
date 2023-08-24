package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute one or more Python scripts from a Command Line Interface."
)
@Plugin(examples = {
    @Example(
        full = true,
        title = "Execute a Python script from Git in a Docker container and output a file",
        code = """     
id: pythonCommandsExample
namespace: dev

tasks:
  - id: wdir
    type: io.kestra.core.tasks.flows.WorkingDirectory
    tasks:
      - id: cloneRepository
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: gitPythonScripts
        type: io.kestra.plugin.scripts.python.Commands
        warningOnStdErr: false
        docker:
          image: ghcr.io/kestra-io/pydata:latest
        beforeCommands:
          - pip install faker > /dev/null
        commands:
          - python scripts/etl_script.py
          - python scripts/generate_orders.py
      
      - id: outputFile
        type: io.kestra.core.tasks.storages.LocalFiles
        outputs:
          - orders.csv

  - id: loadCsvToS3
    type: io.kestra.plugin.aws.s3.Upload
    accessKeyId: "{{secret('AWS_ACCESS_KEY_ID')}}"
    secretKeyId: "{{secret('AWS_SECRET_ACCESS_KEY')}}"
    region: eu-central-1
    bucket: kestraio
    key: stage/orders.csv
    from: "{{outputs.outputFile.uris['orders.csv']}}"
                """
        ),
    @Example(
        full = true,
        title = "Execute a Python script in a Conda virtual environment",
        code = """     
id: localPythonScript
namespace: dev

tasks:
  - id: hello
    type: io.kestra.plugin.scripts.python.Commands
    runner: PROCESS
    beforeCommands:
      - conda activate myCondaEnv
    commands:
      - python /Users/you/scripts/etl_script.py
                """
        ),
    @Example(
        full = true,
        title = "Execute a Python script on a remote worker with a GPU",
        code = """     
id: gpuTask
namespace: dev

tasks:
  - id: hello
    type: io.kestra.plugin.scripts.python.Commands
    runner: PROCESS
    commands:
      - python ml_on_gpu.py
    workerGroup:
      key: gpu
                """
        ),
    @Example(
        full = true,
        title = "Pass detected S3 objects from the event trigger to a Python script",
        code = """     
id: s3TriggerCommands
namespace: blueprint
description: process CSV file from S3 trigger

tasks:
  - id: wdir
    type: io.kestra.core.tasks.flows.WorkingDirectory
    tasks:
      - id: cloneRepo
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: local
        type: io.kestra.core.tasks.storages.LocalFiles
        inputs:
          data.csv: "{{ trigger.objects | jq('.[].uri') | first }}"

      - id: python
        type: io.kestra.plugin.scripts.python.Commands
        description: this script reads a file `data.csv` from S3 trigger
        docker:
          image: ghcr.io/kestra-io/pydata:latest
        warningOnStdErr: false
        commands:
          - python scripts/clean_messy_dataset.py

      - id: output
        type: io.kestra.core.tasks.storages.LocalFiles
        outputs:
          - "*.csv"
          - "*.parquet"

triggers:
  - id: waitForS3object
    type: io.kestra.plugin.aws.s3.Trigger
    bucket: declarative-orchestration
    maxKeys: 1
    interval: PT1S
    filter: FILES
    action: MOVE
    prefix: raw/
    moveTo:
      key: archive/raw/
    accessKeyId: "{{ secret('AWS_ACCESS_KEY_ID') }}"
    secretKeyId: "{{ secret('AWS_SECRET_ACCESS_KEY') }}"
    region: "{{ secret('AWS_DEFAULT_REGION') }}"
                """
        ),        
    @Example(
        full = true,
        title = "Execute a Python script from Git using a private Docker container image",
        code = """     
id: pythonInContainer
namespace: dev

tasks:
  - id: wdir
    type: io.kestra.core.tasks.flows.WorkingDirectory
    tasks:
      - id: cloneRepository
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: gitPythonScripts
        type: io.kestra.plugin.scripts.python.Commands
        warningOnStdErr: false
        commands:
          - python scripts/etl_script.py
        runner: DOCKER
        docker:
          image: annageller/kestra:latest
          config: |
            {
              "auths": {
                  "https://index.docker.io/v1/": {
                      "username": "annageller",
                      "password": "{{ secret('DOCKER_PAT') }}"
                  }
              }
            }
      - id: output
        type: io.kestra.core.tasks.storages.LocalFiles
        outputs:
          - "*.csv"
          - "*.parquet"
                """
        ),
    @Example(
        full = true,
        title = "Create a python script and execute it in a virtual environment",
        code = """
            id: "local-files"
            namespace: "io.kestra.tests"

            tasks:
              - id: workingDir
                type: io.kestra.core.tasks.flows.WorkingDirectory
                tasks:
                - id: inputFiles
                  type: io.kestra.core.tasks.storages.LocalFiles
                  inputs:
                    main.py: |
                      import requests
                      from kestra import Kestra

                      response = requests.get('https://google.com')
                      print(response.status_code)
                      Kestra.outputs({'status': response.status_code, 'text': response.text})
                - id: bash
                  type: io.kestra.plugin.scripts.python.Commands
                  beforeCommands:
                    - python -m venv venv
                    - . venv/bin/activate
                    - pip install requests kestra > /dev/null
                  commands:
                    - python main.py
            """
    )        
})
public class Commands extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "python";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

    @Schema(
        title = "The commands to run"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected List<String> commands;

    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(DEFAULT_IMAGE);
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            this.beforeCommands,
            this.commands
        );

        return this.commands(runContext)
            .addEnv(Map.of("PYTHONUNBUFFERED", "true"))
            .withCommands(commandsArgs)
            .run();
    }
}
