package io.kestra.plugin.scripts.ruby;

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
    title = "Execute a Ruby script."
)
@Plugin(
    examples = {
    @Example(
        full = true,
        title = """
        Create a Ruby script and execute it. The easiest way to create a Ruby script is to use the embedded VS Code editor. Create a file named `main.rb` and paste the following code:
        
        ```ruby
        require 'csv'
        require 'json'

        file = File.read('data.json')
        data_hash = JSON.parse(file)

        # Extract headers
        headers = data_hash.first.keys

        # Convert hashes to arrays
        data = data_hash.map(&:values)

        # Prepend headers to data
        data.unshift(headers)

        # Create and write data to CSV file
        CSV.open('output.csv', 'wb') do |csv|
        data.each { |row| csv << row }
        end
        ```
        
        In order to read that script from the [Namespace File](https://kestra.io/docs/developer-guide/namespace-files) called `main.rb`, you can leverage the `{{ read('main.rb') }}` function.
        
        Also, note how we use the `inputFiles` option to read additional files into the script's working directory. In this case, we read the `data.json` file, which contains the data that we want to convert to CSV.
        
        Finally, we use the `outputFiles` option to specify that we want to output the `output.csv` file that is generated by the script. This allows us to access the file in the UI's Output tab and download it, or pass it to other tasks.
        """,
        code = """
            id: generate_csv
            namespace: dev
            tasks:
              - id: bash
                type: io.kestra.plugin.scripts.ruby.Script
                inputFiles:
                  data.json: |
                    [
                        {"Name": "Alice", "Age": 30, "City": "New York"},
                        {"Name": "Bob", "Age": 22, "City": "Los Angeles"},
                        {"Name": "Charlie", "Age": 35, "City": "Chicago"}
                    ]
                beforeCommands:
                  - ruby -v
                script: "{{ read('main.rb') }}"
                outputFiles:
                  - "*.csv"
            """
        )        
    }
)
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "ruby";

    @Schema(
        title = "Docker options when using the `DOCKER` runner",
        defaultValue = "{image=" + DEFAULT_IMAGE + ", pullPolicy=ALWAYS}"
    )
    @PluginProperty
    @Builder.Default
    protected DockerOptions docker = DockerOptions.builder().build();

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
            builder.image(DEFAULT_IMAGE);
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        List<String> internalToLocalFiles = new ArrayList<>();
        Path relativeScriptPath = runContext.tempDir().relativize(runContext.tempFile(".rb"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script, internalToLocalFiles)
        );
        commands = commands.withInputFiles(inputFiles);

        List<String> commandsArgs = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(),
            String.join(" ", "ruby", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), this.targetOS)),
            this.targetOS
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
