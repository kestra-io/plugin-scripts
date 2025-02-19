package io.kestra.plugin.scripts.node;

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
    title = "Execute a Node.js script."
)
@Plugin(examples = {
    @Example(
        title = "Install package, create a Node.js script and execute it.",
        full = true,
        code = """
            id: nodejs_script
            namespace: company.team

            tasks:
              - id: script
                type: io.kestra.plugin.scripts.node.Script
                beforeCommands:
                  - npm install colors
                script: |
                  const colors = require("colors");
                  console.log(colors.red("Hello"));
                warningOnStdErr: false"
            """
    ),
    @Example(
        full = true,
        title = """
        If you want to generate files in your script to make them available for download and use in downstream tasks, you can leverage the `{{ outputDir }}` variable. Files stored in that directory will be persisted in Kestra's internal storage. To access this output in downstream tasks, use the syntax `{{ outputs.yourTaskId.outputFiles['yourFileName.fileExtension'] }}`.

        Alternatively, instead of the `{{ outputDir }}` variable, you could use the `outputFiles` property to output files from your script. You can access those files in downstream tasks using the same syntax `{{ outputs.yourTaskId.outputFiles['yourFileName.fileExtension'] }}`, and you can download the files from the UI's Output tab.
        """,
        code = """
            id: nodejs_script
            namespace: company.team

            tasks:
              - id: node
                type: io.kestra.plugin.scripts.node.Script
                warningOnStdErr: false
                beforeCommands:
                    - npm install json2csv > /dev/null 2>&1
                script: |
                    const fs = require('fs');
                    const { Parser } = require('json2csv');

                    // Product prices in our simulation
                    const productPrices = {
                        'T-shirt': 20,
                        'Jeans': 75,
                        'Shoes': 80,
                        'Socks': 5,
                        'Hat': 25
                    }

                    const generateOrder = () => {
                        const products = ['T-shirt', 'Jeans', 'Shoes', 'Socks', 'Hat'];
                        const statuses = ['pending', 'shipped', 'delivered', 'cancelled'];

                        const randomProduct = products[Math.floor(Math.random() * products.length)];
                        const randomStatus = statuses[Math.floor(Math.random() * statuses.length)];
                        const randomQuantity = Math.floor(Math.random() * 10) + 1;

                        const order = {
                            product: randomProduct,
                            status: randomStatus,
                            quantity: randomQuantity,
                            total: randomQuantity * productPrices[randomProduct]
                        };

                        return order;
                    }

                    let totalSales = 0;
                    let orders = [];

                    for (let i = 0; i < 100; i++) {
                        const order = generateOrder();
                        orders.push(order);
                        totalSales += order.total;
                    }

                    console.log(`Total sales: $${totalSales}`);

                    const fields = ['product', 'status', 'quantity', 'total'];
                    const json2csvParser = new Parser({ fields });
                    const csvData = json2csvParser.parse(orders);

                    fs.writeFileSync('{{ outputDir }}/orders.csv', csvData);

                    console.log('Orders saved to orders.csv');
            """
    )
})
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "node";

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
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".js"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);
        return commands
            .addEnv(Map.of(
                "PYTHONUNBUFFERED", "true",
                "PIP_ROOT_USER_ACTION", "ignore"
            ))
            .withInterpreter(this.interpreter)
            .withBeforeCommands(Property.of(getBeforeCommandsWithOptions(runContext)))
            .withCommands(Property.of(List.of(
                String.join(" ", "node", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .withTargetOS(os)
            .run();
    }
}
