package io.kestra.core.tasks.scripts;

import com.google.common.base.Charsets;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.*;

import static io.kestra.core.utils.Rethrow.throwSupplier;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Python script (Deprecated).",
    description = "This task is deprecated, please use the [io.kestra.plugin.scripts.python.Script](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.python.script) or [io.kestra.plugin.scripts.python.Commands](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.python.commands) tasks instead.\n\n" +
        "With the Python task, you can execute a full Python script.\n" +
        "The task will create a fresh `virtualenv` for every tasks and allows to install some Python package define in `requirements` property.\n" +
        "\n" +
        "By convention, you need to define at least a `main.py` files in `inputFiles` that will be the script used.\n" +
        "But you are also able to add as many script as you need in `inputFiles`.\n" +
        "\n" +
        "You can also add a `pip.conf` in `inputFiles` to customize the pip download of dependencies (like a private registry).\n" +
        "\n" +
        "You can send outputs & metrics from your python script that can be used by others tasks. In order to help, we inject a python package directly on the working dir." +
        "Here is an example usage:\n" +
        "```python\n" +
        "from kestra import Kestra\n" +
        "Kestra.outputs({'test': 'value', 'int': 2, 'bool': True, 'float': 3.65})\n" +
        "Kestra.counter('count', 1, {'tag1': 'i', 'tag2': 'win'})\n" +
        "Kestra.timer('timer1', lambda: time.sleep(1), {'tag1': 'i', 'tag2': 'lost'})\n" +
        "Kestra.timer('timer2', 2.12, {'tag1': 'i', 'tag2': 'destroy'})\n" +
        "```",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a python script.",
            full = true,
            code = """
                id: python_flow
                namespace: company.team
                
                tasks:
                  - id: python
                    type: io.kestra.core.tasks.scripts.Python
                    inputFiles:
                      data.json: |
                        {"status": "OK"}
                      main.py: |
                        from kestra import Kestra
                        import json
                        import requests
                        import sys
                        result = json.loads(open(sys.argv[1]).read())
                        print(f"python script {result['status']}")
                        response = requests.get('http://google.com')
                        print(response.status_code)
                        Kestra.outputs({'status': response.status_code, 'text': response.text})
                      pip.conf: |
                        # some specific pip repository configuration
                    args:
                      - data.json
                    requirements:
                      - requests
                """
        ),
        @Example(
            title = "Execute a python script with an input file from Kestra's local storage created by a previous task.",
            full = true,
            code = """
                id: python_flow
                namespace: company.team
                
                tasks:
                  - id: python
                    type: io.kestra.core.tasks.scripts.Python
                    inputFiles:
                      data.csv: {{outputs.previousTaskId.uri}}
                      main.py: |
                        with open('data.csv', 'r') as f:
                          print(f.read())
                """
        )
    }
)
@Slf4j
@Deprecated
public class Python extends AbstractBash implements RunnableTask<ScriptOutput> {
    @Builder.Default
    @Schema(
        title = "The python interpreter to use",
        description = "Set the python interpreter path to use"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    private final String pythonPath = "python";

    @Schema(
        title = "Python command args",
        description = "Arguments list to pass to main python script"
    )
    @PluginProperty(dynamic = true)
    private List<String> args;

    @Schema(
        title = "Requirements are python dependencies to add to the python execution process",
        description = "Python dependencies list to setup in the virtualenv, in the same format than requirements.txt"
    )
    @PluginProperty(dynamic = true)
    protected List<String> requirements;

    @Schema(
        title = "The commands to run",
        description = "Default command will be launched with `./bin/python main.py {{args}}`"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    @Builder.Default
    protected List<String> commands = Collections.singletonList("./bin/python main.py");

    @Schema(
        title = "Create a virtual env",
        description = "When a virtual env is created, we will install the `requirements` needed. " +
            "Disabled it if all the requirements is already on the file system.\n" +
            "If you disabled the virtual env creation, the `requirements` will be ignored."
    )
    @PluginProperty
    @Builder.Default
    protected Boolean virtualEnv = true;

    @Schema(
        title = "Use uv for virtual environment and dependency installation",
        description = "If true, uses uv (https://github.com/astral-sh/uv) for venv and pip install instead of python/pip."
    )
    @PluginProperty
    @Builder.Default
    protected Boolean useUv = false;

    protected String virtualEnvCommand(RunContext runContext, List<String> requirements) throws IllegalVariableEvaluationException {
        List<String> renderer = new ArrayList<>();

        if (runContext.render(this.exitOnFailed).as(Boolean.class).orElseThrow()) {
            renderer.add("set -o errexit");
        }

        // renderer.add(this.pythonPath + " -m venv --system-site-packages " + workingDirectory + " > /dev/null");

        // if (requirements != null) {
        //     renderer.addAll(Arrays.asList(
        //         "./bin/pip install pip --upgrade > /dev/null",
        //         "./bin/pip install " + runContext.render(String.join(" ", requirements), additionalVars) + " > /dev/null"));
        // }

        if (Boolean.TRUE.equals(this.useUv)) {
            renderer.add("uv venv --system-site-packages .venv > /dev/null");
            if (requirements != null && !requirements.isEmpty()) {
                renderer.add("./.venv/bin/uv pip install " + runContext.render(String.join(" ", requirements), additionalVars) + " > /dev/null");
            }
        } else {
        renderer.add(this.pythonPath + " -m venv --system-site-packages " + workingDirectory + " > /dev/null");
            if (requirements != null && !requirements.isEmpty()) {
                renderer.addAll(Arrays.asList(
                "./bin/pip install pip --upgrade > /dev/null",
                "./bin/pip install " + runContext.render(String.join(" ", requirements), additionalVars) + " > /dev/null"));
            }
        }


        return String.join("\n", renderer);
    }

    @Override
    protected Map<String, String> finalEnv(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> env = super.finalEnv(runContext);

        // python buffer log by default, so we force unbuffer to have the whole log
        env.put("PYTHONUNBUFFERED", "true");

        return env;
    }

    @Override
    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> map = super.finalInputFiles(runContext);

        map.put("kestra.py", IOUtils.toString(
            Objects.requireNonNull(Python.class.getClassLoader().getResourceAsStream(
                "kestra.py")),
            Charsets.UTF_8
        ));

        return map;
    }

    @Override
    protected Map<String, String> finalInputFiles(RunContext runContext, Map<String, Object> additionalVar) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> map = super.finalInputFiles(runContext, additionalVar);

        map.put("kestra.py", IOUtils.toString(
            Objects.requireNonNull(Python.class.getClassLoader().getResourceAsStream(
                "kestra.py")),
            Charsets.UTF_8
        ));

        return map;
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        Map<String, String> finalInputFiles = this.finalInputFiles(runContext);

        if (!finalInputFiles.containsKey("main.py") && this.commands.size() == 1 && this.commands.get(0).equals("./bin/python main.py")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.py key with python code value.");
        }

        return run(runContext, throwSupplier(() -> {
            List<String> renderer = new ArrayList<>();
            if (this.virtualEnv) {
                renderer.add(this.virtualEnvCommand(runContext, requirements));
            } else if (runContext.render(this.exitOnFailed).as(Boolean.class).orElseThrow()) {
                renderer.add("set -o errexit");
            }

            for (String command : commands) {
                String argsString = args == null ? "" : " " + runContext.render(String.join(" ", args), additionalVars);

                String renderedCommand = runContext.render(command, additionalVars) + argsString;
                if (Boolean.TRUE.equals(this.useUv) && renderedCommand.startsWith("./bin/python")) {
                    renderedCommand = renderedCommand.replace("./bin/python", "./.venv/bin/python");
                }
                
                // renderer.add(runContext.render(command, additionalVars) + argsString);
                renderer.add(renderedCommand);
            }

            return String.join("\n", renderer);
        }));
    }
}
