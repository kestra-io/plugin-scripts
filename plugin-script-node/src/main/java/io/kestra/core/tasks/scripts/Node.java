package io.kestra.core.tasks.scripts;

import com.google.common.base.Charsets;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static io.kestra.core.utils.Rethrow.throwSupplier;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Node.js script (Deprecated).",
    description = "This task is deprecated, please use the [io.kestra.plugin.scripts.node.Script](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.node.script) or [io.kestra.plugin.scripts.node.Commands](https://kestra.io/plugins/tasks/io.kestra.plugin.scripts.node.commands) tasks instead.\n\n" +
        "With the Node task, you can execute a full JavaScript script.\n" +
        "The task will create a temporary folder for each task, and allows you to install some npm packages defined in an optional `package.json` file.\n" +
        "\n" +
        "By convention, you need to define at least a `main.js` file in `inputFiles` that will be the script used.\n" +
        "You can also add as many JavaScript files as you need in `inputFiles`.\n" +
        "\n" +
        "The outputs & metrics from your Node.js script can be used by others tasks. In order to make things easy, we inject a node package directly on the working directory." +
        "Here is an example usage:\n" +
        "```javascript\n" +
        "const Kestra = require(\"./kestra\");\n" +
        "Kestra.outputs({test: 'value', int: 2, bool: true, float: 3.65});\n" +
        "Kestra.counter('count', 1, {tag1: 'i', tag2: 'win'});\n" +
        "Kestra.timer('timer1', (callback) => { setTimeout(callback, 1000) }, {tag1: 'i', tag2: 'lost'});\n" +
        "Kestra.timer('timer2', 2.12, {tag1: 'i', tag2: 'destroy'});\n" +
        "```",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a Node.js script.",
            code = {
                "inputFiles:",
                "  main.js: |",
                "    const Kestra = require(\"./kestra\");",
                "    const fs = require('fs')",
                "    const result = fs.readFileSync(process.argv[2], \"utf-8\")",
                "    console.log(JSON.parse(result).status)",
                "    const axios = require('axios')",
                "    axios.get('http://google.fr').then(d => { console.log(d.status); Kestra.outputs({'status': d.status, 'text': d.data})})",
                "    console.log(require('./mymodule').value)",
                "  data.json: |",
                "    {\"status\": \"OK\"}",
                "  mymodule.js: |",
                "    module.exports.value = 'hello world'",
                "  package.json: |",
                "    {",
                "      \"name\": \"tmp\",",
                "      \"version\": \"1.0.0\",",
                "      \"description\": \"\",",
                "      \"main\": \"index.js\",",
                "      \"dependencies\": {",
                "          \"axios\": \"^0.20.0\"",
                "      },",
                "      \"devDependencies\": {},",
                "      \"scripts\": {",
                "          \"test\": \"echo `Error: no test specified` && exit 1\"",
                "      },",
                "      \"author\": \"\",",
                "      \"license\": \"ISC\"",
                "    }",
                "args:",
                "  - data.json",
            }
        ),
        @Example(
            title = "Execute a Node.js script with an input file from Kestra's internal storage created by a previous task.",
            code = {
                "inputFiles:",
                "  data.csv: {{ outputs.previousTaskId.uri }}",
                "  main.js: |",
                "    const fs = require('fs')",
                "    const result = fs.readFileSync('data.csv', 'utf-8')",
                "    console.log(result)"
            }
        )
    }
)
@Deprecated
public class Node extends AbstractBash implements RunnableTask<io.kestra.core.tasks.scripts.ScriptOutput> {
    @Builder.Default
    @Schema(
        title = "The node interpreter to use.",
        description = "Set the node interpreter path to use."
    )
    private final Property<String> nodePath = Property.of("node");

    @Builder.Default
    @Schema(
        title = "The npm binary to use.",
        description = "Set the npm binary path for node dependencies setup."
    )
    private final Property<String> npmPath = Property.of("npm");

    @Schema(
        title = "Node command args.",
        description = "Arguments list to pass to main JavaScript script."

    )
    private Property<List<String>> args;

    @Override
    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> map = super.finalInputFiles(runContext);

        map.put("kestra.js", IOUtils.toString(
            Objects.requireNonNull(Node.class.getClassLoader().getResourceAsStream("kestra.js")),
            Charsets.UTF_8
        ));

        return map;
    }

    @Override
    protected Map<String, String> finalInputFiles(RunContext runContext, Map<String, Object> additionalVar) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> map = super.finalInputFiles(runContext, additionalVar);

        map.put("kestra.js", IOUtils.toString(
            Objects.requireNonNull(Node.class.getClassLoader().getResourceAsStream("kestra.js")),
            Charsets.UTF_8
        ));

        return map;
    }

    @Override
    public io.kestra.core.tasks.scripts.ScriptOutput run(RunContext runContext) throws Exception {
        Map<String, String> finalInputFiles = this.finalInputFiles(runContext);

        if (!finalInputFiles.containsKey("main.js")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.js key with javascript code value.");
        }

        return run(runContext, throwSupplier(() -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (runContext.render(this.exitOnFailed).as(Boolean.class).orElseThrow()) {
                renderer.add("set -o errexit");
            }

            var renderedArgs = runContext.render(this.getArgs()).asList(String.class);
            String args = renderedArgs.isEmpty() ? "" : " " + String.join(" ", renderedArgs);

            String npmInstall = finalInputFiles.containsKey("package.json") ? runContext.render(npmPath).as(String.class).orElse(null) + " i > /dev/null" : "";

            renderer.addAll(Arrays.asList(
                "PATH=\"$PATH:" + new File(runContext.render(nodePath).as(String.class).orElseThrow()).getParent() + "\"",
                npmInstall,
                nodePath + " main.js" + args
            ));

            return String.join("\n", renderer);
        }));
    }
}
