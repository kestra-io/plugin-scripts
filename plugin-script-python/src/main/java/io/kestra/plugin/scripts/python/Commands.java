package io.kestra.plugin.scripts.python;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotEmpty;

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
        title = """
        Execute a Python script in a Conda virtual environment. First, add the following script in the embedded VS Code editor and name it `etl_script.py`:
          
        ```python
        import argparse

        parser = argparse.ArgumentParser()

        parser.add_argument("--num", type=int, default=42, help="Enter an integer")

        args = parser.parse_args()
        result = args.num * 2
        print(result)
        ```
          
        Then, make sure to set the `enabled` flag of the `namespaceFiles` property to `true` to enable [namespace files](https://kestra.io/docs/developer-guide/namespace-files).           

        This flow uses a `PROCESS` runner and Conda virtual environment for process isolation and dependency management. However, note that, by default, Kestra runs tasks in a Docker container (i.e. a `DOCKER` runner), and you can use the `docker` property to customize many options, such as the Docker image to use.
        """,
        code = """
id: python_venv
namespace: company.team

tasks:
  - id: hello
    type: io.kestra.plugin.scripts.python.Commands
    namespaceFiles:
      enabled: true
    taskRunner:
      type: io.kestra.plugin.core.runner.Process
    beforeCommands:
      - conda activate myCondaEnv
    commands:
      - python etl_script.py
                """
        ),  
    @Example(
        full = true,
        title = "Execute a Python script from Git in a Docker container and output a file",
        code = """     
id: pythonCommandsExample
namespace: company.team

tasks:
  - id: wdir
    type: io.kestra.plugin.core.flow.WorkingDirectory
    tasks:
      - id: cloneRepository
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: gitPythonScripts
        type: io.kestra.plugin.scripts.python.Commands
        warningOnStdErr: false
        containerImage: ghcr.io/kestra-io/pydata:latest
        beforeCommands:
          - pip install faker > /dev/null
        commands:
          - python examples/scripts/etl_script.py
          - python examples/scripts/generate_orders.py
        outputFiles:
          - orders.csv

  - id: loadCsvToS3
    type: io.kestra.plugin.aws.s3.Upload
    accessKeyId: "{{ secret('AWS_ACCESS_KEY_ID') }}"
    secretKeyId: "{{ secret('AWS_SECRET_ACCESS_KEY') }}"
    region: eu-central-1
    bucket: kestraio
    key: stage/orders.csv
    from: "{{ outputs.gitPythonScripts.outputFiles['orders.csv'] }}"
                """
        ),
    @Example(
        full = true,
        title = "Execute a Python script on a remote worker with a GPU",
        code = """     
id: gpuTask
namespace: company.team

tasks:
  - id: hello
    type: io.kestra.plugin.scripts.python.Commands
    taskRunner:
      type: io.kestra.plugin.core.runner.Process
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
namespace: company.team
description: process CSV file from S3 trigger

tasks:
  - id: wdir
    type: io.kestra.plugin.core.flow.WorkingDirectory
    tasks:
      - id: cloneRepo
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: python
        type: io.kestra.plugin.scripts.python.Commands
        inputFiles:
          data.csv: "{{ trigger.objects | jq('.[].uri') | first }}"
        description: this script reads a file `data.csv` from S3 trigger
        containerImage: ghcr.io/kestra-io/pydata:latest
        warningOnStdErr: false
        commands:
          - python examples/scripts/clean_messy_dataset.py
        outputFiles:
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
namespace: company.team

tasks:
  - id: wdir
    type: io.kestra.plugin.core.flow.WorkingDirectory
    tasks:
      - id: cloneRepository
        type: io.kestra.plugin.git.Clone
        url: https://github.com/kestra-io/examples
        branch: main

      - id: gitPythonScripts
        type: io.kestra.plugin.scripts.python.Commands
        warningOnStdErr: false
        commands:
          - python examples/scripts/etl_script.py
        outputFiles:
          - "*.csv"
          - "*.parquet"
        containerImage: annageller/kestra:latest
        taskRunner:
          type: io.kestra.plugin.scripts.runner.docker.Docker
          config: |
            {
              "auths": {
                  "https://index.docker.io/v1/": {
                      "username": "annageller",
                      "password": "{{ secret('DOCKER_PAT') }}"
                  }
              }
            }"""
        ),
    @Example(
        full = true,
        title = "Create a python script and execute it in a virtual environment",
        code = """
            id: script_in_venv
            namespace: company.team
            tasks:
              - id: bash
                type: io.kestra.plugin.scripts.python.Commands
                inputFiles:
                  main.py: |
                    import requests
                    from kestra import Kestra

                    response = requests.get('https://google.com')
                    print(response.status_code)
                    Kestra.outputs({'status': response.status_code, 'text': response.text})                    
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
    private static final String DEFAULT_IMAGE = "ghcr.io/kestra-io/kestrapy:latest";

    @Builder.Default
    protected String containerImage = DEFAULT_IMAGE;

    @Schema(
        title = "The commands to run."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    protected List<String> commands;

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
        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            this.commands,
            this.targetOS
        );

        return this.commands(runContext)
            .addEnv(Map.of(
                "PYTHONUNBUFFERED", "true",
                "PIP_ROOT_USER_ACTION", "ignore",
                "PIP_DISABLE_PIP_VERSION_CHECK", "1"
            ))
            .withCommands(commandsArgs)
            .run();
    }
}
