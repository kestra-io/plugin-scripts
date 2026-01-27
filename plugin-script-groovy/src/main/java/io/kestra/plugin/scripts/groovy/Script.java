package io.kestra.plugin.scripts.groovy;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.runners.TargetOS;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Groovy script inline with your Flow Code."
)
@Plugin(examples = {
    @Example(
        title = "Create a Groovy script and execute it.",
        full = true,
        code = """
            id: groovy_script
            namespace: company.team

            tasks:
              - id: script
                type: io.kestra.plugin.scripts.groovy.Script
                script: |
                  println "Hello, World!"
            """
    ),
    @Example(
        title = "Fetch data from an API and save it to a file.",
        full = true,
        code = """
            id: groovy_api_fetch
            namespace: company.team

            tasks:
              - id: groovy_script
                type: io.kestra.plugin.scripts.groovy.Script
                outputFiles:
                  - users.json
                script: |
                  import groovy.json.JsonOutput

                  def url = "https://jsonplaceholder.typicode.com/users"
                  def users = new URL(url).text

                  new File("users.json").write(JsonOutput.prettyPrint(users))
                  println "Successfully fetched users and created users.json"
            """
    ),
})
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "groovy";

    @Builder.Default
    protected Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file."
    )
    @NotNull
    protected Property<String> script;

    @Override
    protected DockerOptions injectDefaults(RunContext runContext, DockerOptions original) throws IllegalVariableEvaluationException {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(runContext.render(this.getContainerImage()).as(String.class).orElse(null));
        }

        builder.user("root");
        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(".groovy"));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script)
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);
        return commands
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withTaskRunner(
                // because of, we are mounting a volume and the uid running Docker is not 1000, so it should run as user root (-u root).
                Docker.builder()
                    .user("root")
                    .build())
            .withCommands(Property.ofValue(List.of(
                String.join(" ", "groovy", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .withTargetOS(os)
            .run();
    }
}