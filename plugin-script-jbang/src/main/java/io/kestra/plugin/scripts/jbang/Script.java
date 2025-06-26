package io.kestra.plugin.scripts.jbang;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
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
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a script written in Java, JShell, Kotlin, Groovy or render Markdown with JBang.",
    description = "Besides scripting languages, with JBang, it is possible to [write scripts using Markdown](https://www.jbang.dev/documentation/guide/latest/usage.html#running-markdowns-md-experimental). JBang will extract code found in java, jsh, or jshelllanguage code blocks."
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

    @Builder.Default
    private Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `jbang hello.java` or an executable JAR, use the `Commands` task instead."
    )
    @NotNull
    private Property<String> script;

    @Schema(
        title = "The JBang script extension.",
        description = "JBang support more than Java scripts, you can use it with JShell (.jsh), Kotlin (.kt), Groovy (.groovy) or even Markdowns (.md)."
    )
    @Builder.Default
    @NotNull
    private Property<String> extension = Property.of(".java");

    @Schema(
        title = "Whether JBang should be quit.",
        description = "By default, JBang logs in stderr so quiet is configured to true by default so no JBang logs are shown except errors."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> quiet = Property.of(true);


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
            .withCommands(Property.of(List.of(
                String.join(" ", "jbang", runContext.render(quiet).as(Boolean.class).orElseThrow() ? "--quiet" : "", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), os))
            )))
            .run();
    }
}
