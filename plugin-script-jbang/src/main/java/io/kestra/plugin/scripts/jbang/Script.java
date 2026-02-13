package io.kestra.plugin.scripts.jbang;

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
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Run inline JBang script",
    description = "Executes Java/JShell/Kotlin/Groovy or Markdown (code blocks) via JBang inside the default 'jbangdev/jbang-action' image. Saves the rendered script to a temp file with the chosen extension and runs `jbang` (quiet by default). Add //DEPS lines for dependencies."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a script written in Java",
            full = true,
            code = """
                id: jbang_script
                namespace: company.team

                tasks:
                  - id: script
                    type: io.kestra.plugin.scripts.jbang.Script
                    script: |
                      class helloworld {
                          public static void main(String[] args) {
                              if(args.length==0) {
                                  System.out.println("Hello World!");
                              } else {
                                  System.out.println("Hello " + args[0]);
                              }
                          }
                      }
                """
        ),
        @Example(
            title = "Execute a script written in Java with dependencies",
            full = true,
            code = """
                id: jbang_script
                namespace: company.team

                tasks:
                  - id: script_with_dependency
                    type: io.kestra.plugin.scripts.jbang.Script
                    script: |
                      //DEPS ch.qos.reload4j:reload4j:1.2.19

                      import org.apache.log4j.Logger;
                      import org.apache.log4j.BasicConfigurator;

                      class classpath_example {

                        static final Logger logger = Logger.getLogger(classpath_example.class);

                        public static void main(String[] args) {
                          BasicConfigurator.configure();\s
                          logger.info("Hello World");
                        }
                      }
                """
        ),
        @Example(
            title = "Execute a script written in Kotlin.",
            full = true,
            code = """
                id: jbang_script
                namespace: company.team

                tasks:
                  - id: script_kotlin
                    type: io.kestra.plugin.scripts.jbang.Script
                    extension: .kt
                    script: |
                      public fun main() {
                          println("Hello World");
                      }
                """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    private static final String DEFAULT_IMAGE = "jbangdev/jbang-action";

    @Schema(
        title = "Container image for JBang runtime",
        description = "Docker image used to run the script; defaults to 'jbangdev/jbang-action'. Provide an image that includes JBang if overriding."
    )
    @Builder.Default
    private Property<String> containerImage = Property.ofValue(DEFAULT_IMAGE);

    @Schema(
        title = "Inline JBang script",
        description = "Script body as a multi-line string; written to a temp file and executed with `jbang`. For existing files or JARs, use the Commands task."
    )
    @NotNull
    private Property<String> script;

    @Schema(
        title = "Script extension",
        description = "File extension to write (e.g., .java, .jsh, .kt, .groovy, .md); defaults to .java."
    )
    @Builder.Default
    @NotNull
    private Property<String> extension = Property.ofValue(".java");

    @Schema(
        title = "Quiet mode",
        description = "When true (default), runs JBang with --quiet to suppress non-error logs."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> quiet = Property.ofValue(true);


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
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(runContext.render(extension).as(String.class).orElseThrow()));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, runContext.render(this.script).as(String.class).orElse(null), new ArrayList<>())
        );
        commands = commands.withInputFiles(inputFiles);

        TargetOS os = runContext.render(this.targetOS).as(TargetOS.class).orElse(null);

        return commands
            .withTargetOS(os)
            .withInterpreter(this.interpreter)
            .withBeforeCommands(beforeCommands)
            .withBeforeCommandsWithOptions(true)
            .withCommands(Property.ofValue(List.of(
                String.join(" ", "jbang", runContext.render(quiet).as(Boolean.class).orElseThrow() ? "--quiet" : "", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .run();
    }
}
