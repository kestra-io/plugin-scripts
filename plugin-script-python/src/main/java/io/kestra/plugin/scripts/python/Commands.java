package io.kestra.plugin.scripts.python;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.python.internals.PackageManagerType;
import io.kestra.plugin.scripts.python.internals.PythonEnvironmentManager;
import io.kestra.plugin.scripts.python.internals.PythonEnvironmentManager.ResolvedPythonEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute Python files and commands.",
    description = "Executes Python commands using the default 'python:3.13-slim' image unless overridden, with dependencies installed via UV (default) or pip and optional caching. Supports inputFiles/namespaceFiles to stage code and beforeCommands for setup; suitable for running existing scripts."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = """
                Execute a Python script in a uv virtual environment. First, add the following script as a Namespace File in the embedded Code Editor and name it `etl_script.py`:

                ```python
                import argparse
                import requests

                parser = argparse.ArgumentParser()

                parser.add_argument("--num", type=int, default=42, help="Enter an integer")

                args = parser.parse_args()
                result = args.num * 2
                print(result)
                print(f"Requests version: {requests.__version__}")
                ```

                Then, make sure to set the `enabled` flag of the `namespaceFiles` property to `true` to enable [namespace files](https://kestra.io/docs/concepts/namespace-files). By default, setting this to `true` injects all Namespace files; we `include` only the `etl_script.py` file as that is the only file we need for this task.

                This flow uses a `io.kestra.plugin.core.runner.Process` Task Runner and a virtual environment for process isolation and dependency management. However, note that, by default, Kestra runs tasks in a Docker container (i.e. a Docker Task Runner), and you can use the `taskRunner` property to customize many options, as well as `containerImage` to choose the Docker image to use.
                """,
            code = """
                id: python_venv
                namespace: company.team

                inputs:
                  - id: nr
                    type: INT
                    defaults: 21

                tasks:
                  - id: python
                    type: io.kestra.plugin.scripts.python.Commands
                    namespaceFiles:
                      enabled: true
                      folderPerNamespace: true
                      namespaces:
                        - company
                        - company.team
                        - company.team.project # Explicitly name all namespaces to include; by default only the flow namespace is loaded.
                      include:
                        - etl_script.py
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    beforeCommands:
                      - uv venv --python 3.13
                      - uv pip install requests
                    commands:
                      - python etl_script.py --num {{inputs.nr}}
                """
        ),
        @Example(
            full = true,
            title = "Execute a Python script from Git in a Docker container and output a file",
            code = """
                id: python_commands_example
                namespace: company.team

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: clone_repository
                        type: io.kestra.plugin.git.Clone
                        url: https://github.com/kestra-io/examples
                        branch: main

                      - id: git_python_scripts
                        type: io.kestra.plugin.scripts.python.Commands
                        containerImage: ghcr.io/kestra-io/pydata:latest
                        beforeCommands:
                          - pip install faker > /dev/null
                        commands:
                          - python examples/scripts/etl_script.py
                          - python examples/scripts/generate_orders.py
                        outputFiles:
                          - orders.csv

                  - id: load_csv_to_s3
                    type: io.kestra.plugin.aws.s3.Upload
                    accessKeyId: "{{ secret('AWS_ACCESS_KEY_ID') }}"
                    secretKeyId: "{{ secret('AWS_SECRET_KEY_ID') }}"
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
                id: gpu_task
                namespace: company.team

                tasks:
                  - id: python
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
            title = "Run a Python command that takes an environment variable as input",
            code = """
                id: python_input_as_env_variable
                namespace: company.team

                inputs:
                  - id: uri
                    type: URI
                    defaults: https://www.google.com/

                tasks:
                  - id: code
                    type: io.kestra.plugin.scripts.python.Commands
                    taskRunner:
                      type: io.kestra.plugin.scripts.runner.docker.Docker
                    containerImage: ghcr.io/kestra-io/pydata:latest
                    inputFiles:
                      main.py: |
                          import requests
                          import os

                          # Perform the GET request
                          response = requests.get(os.environ['URI'])

                          # Check if the request was successful
                          if response.status_code == 200:
                              # Print the content of the page
                              print(response.text)
                          else:
                              print(f"Failed to retrieve the webpage. Status code: {response.status_code}")
                    env:
                      URI: "{{ inputs.uri }}"
                    commands:
                      - python main.py
                """
        ),
        @Example(
            full = true,
            title = "Pass detected S3 objects from the event trigger to a Python script",
            code = """
                id: s3_trigger_commands
                namespace: company.team
                description: process CSV file from S3 trigger

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: clone_repository
                        type: io.kestra.plugin.git.Clone
                        url: https://github.com/kestra-io/examples
                        branch: main

                      - id: python
                        type: io.kestra.plugin.scripts.python.Commands
                        inputFiles:
                          data.csv: "{{ trigger.objects | jq('.[].uri') | first }}"
                        description: this script reads a file `data.csv` from S3 trigger
                        containerImage: ghcr.io/kestra-io/pydata:latest
                        commands:
                          - python examples/scripts/clean_messy_dataset.py
                        outputFiles:
                          - "*.csv"
                          - "*.parquet"

                triggers:
                  - id: wait_for_s3_object
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
                    secretKeyId: "{{ secret('AWS_SECRET_KEY_ID') }}"
                    region: "{{ secret('AWS_DEFAULT_REGION') }}"
                """
        ),
        @Example(
            full = true,
            title = "Execute a Python script from Git using a private Docker container image",
            code = """
                id: python_in_container
                namespace: company.team

                tasks:
                  - id: wdir
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: clone_repository
                        type: io.kestra.plugin.git.Clone
                        url: https://github.com/kestra-io/examples
                        branch: main

                      - id: git_python_scripts
                        type: io.kestra.plugin.scripts.python.Commands
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
                            }
                """
        ),
        @Example(
            full = true,
            title = "Create a Python script and execute it in a venv virtual environment",
            code = """
                id: script_in_venv
                namespace: company.team
                tasks:
                  - id: python
                    type: io.kestra.plugin.scripts.python.Commands
                    packageManager: UV
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
    },
    metrics = {
        @Metric(name = "deps.cache.download.duration", description = "Number of records converted", type = Counter.TYPE),
        @Metric(name = "deps.cache.upload.duration", description = "Number of records converted", type = Counter.TYPE),
    }
)
public class Commands extends AbstractPythonExecScript implements RunnableTask<ScriptOutput> {

    @Schema(
        title = "Commands to execute",
        description = "List of Python commands run in order inside the container; combine with beforeCommands for env setup and inputFiles/namespaceFiles to stage scripts."
    )
    @NotNull
    protected Property<List<String>> commands;

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
        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        PackageManagerType resolvedPackageManager = runContext.render(this.packageManager).as(PackageManagerType.class).orElse(PackageManagerType.PIP);
        PythonEnvironmentManager pythonEnvironmentManager = new PythonEnvironmentManager(runContext, this, resolvedPackageManager);
        ResolvedPythonEnvironment pythonEnvironment = pythonEnvironmentManager.setup(containerImage, taskRunner, runner);

        Map<String, String> env = new HashMap<>();
        env.put("PYTHONUNBUFFERED", "true");
        env.put("PIP_ROOT_USER_ACTION", "ignore");
        env.put("PIP_DISABLE_PIP_VERSION_CHECK", "1");

        if (pythonEnvironment.packages() != null) {
            env.put("PYTHONPATH", pythonEnvironment.packages().path().toString());
        }

        ScriptOutput output = this.commands(runContext)
            .addEnv(env)
            .withInterpreter(this.interpreter)
            .withCommands(commands)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withTargetOS(os)
            .run();

        // Cache upload
        if (pythonEnvironmentManager.isCacheEnabled() && pythonEnvironment.packages() != null && !pythonEnvironment.cached()) {
            pythonEnvironmentManager.uploadCache(runContext, pythonEnvironment.packages());
        }
        return output;
    }
}
