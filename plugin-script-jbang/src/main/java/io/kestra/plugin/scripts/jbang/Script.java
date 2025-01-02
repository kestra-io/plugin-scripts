package io.kestra.plugin.scripts.jbang;

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
    title = "Execute a script written in Java, JShell, Kotlin, Groovy or Markdown with JBang."
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
public class Script extends AbstractExecScript {
    private static final String DEFAULT_IMAGE = "jbangdev/jbang-action";

    @Builder.Default
    private Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    @Schema(
        title = "The inline script content. This property is intended for the script file's content as a (multiline) string, not a path to a file. To run a command from a file such as `jbang hello.java` or an executable JAR, use the `Commands` task instead."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    private String script;

    @Schema(
        title = "The JBang script extension.",
        description = "JBang support more than Java scripts, you can use it with JShell (.jsh), Kotlin (.kt), Groovy (.groovy) or even Markdowns (.md)."
    )
    @PluginProperty(dynamic = true)
    @NotEmpty
    @Builder.Default
    private String extension = ".java";

    @Schema(
        title = "Whether JBang should be quit.",
        description = "By default, JBang logs in stderr so quiet is configured to true by default so no JBang logs are shown except errors."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @Builder.Default
    private Boolean quiet = true;


    @Override
    protected DockerOptions injectDefaults(DockerOptions original) {
        var builder = original.toBuilder();
        if (original.getImage() == null) {
            builder.image(this.getContainerImage().toString());
        }

        return builder.build();
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        CommandsWrapper commands = this.commands(runContext);

        Map<String, String> inputFiles = FilesService.inputFiles(runContext, commands.getTaskRunner().additionalVars(runContext, commands), this.getInputFiles());
        List<String> internalToLocalFiles = new ArrayList<>();
        Path relativeScriptPath = runContext.workingDir().path().relativize(runContext.workingDir().createTempFile(extension));
        inputFiles.put(
            relativeScriptPath.toString(),
            commands.render(runContext, this.script, internalToLocalFiles)
        );
        commands = commands.withInputFiles(inputFiles);

        List<String> commandsArgs  = ScriptService.scriptCommands(
            this.interpreter,
            getBeforeCommandsWithOptions(runContext),
            String.join(" ", "jbang", quiet ? "--quiet" : "", commands.getTaskRunner().toAbsolutePath(runContext, commands, relativeScriptPath.toString(), runContext.render(this.targetOS).as(TargetOS.class).orElse(null))),
            runContext.render(this.targetOS).as(TargetOS.class).orElse(null)
        );

        return commands
            .withCommands(commandsArgs)
            .run();
    }
}
