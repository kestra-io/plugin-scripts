package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.services.LogService;
import io.kestra.plugin.scripts.exec.scripts.services.ScriptService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@AllArgsConstructor
@Getter
public class Commands {
    RunContext runContext;

    Path workingDirectory;

    Path outputDirectory;

    Map<String, Object> additionalVars;

    List<String> commands;

    Map<String, String> env;

    @With
    LogSupplierInterface logSupplier;

    @With
    RunnerType runnerType;

    @With
    DockerOptions dockerOptions;

    @With
    Boolean warningOnStdErr;

    public Commands(RunContext runContext, DockerOptions defaultDockerOptions) {
        this.runContext = runContext;

        this.workingDirectory = runContext.tempDir();
        this.outputDirectory = this.workingDirectory.resolve(IdUtils.create());
        //noinspection ResultOfMethodCallIgnored
        this.outputDirectory.toFile().mkdirs();

        this.additionalVars = new HashMap<>(Map.of(
            "workingDir", workingDirectory.toAbsolutePath().toString(),
            "outputDir", outputDirectory.toString()
        ));

        this.logSupplier = LogService.defaultLogSupplier(runContext);

        this.dockerOptions = defaultDockerOptions;
    }

    public Commands withCommands(List<String> commands) throws IOException, IllegalVariableEvaluationException {
        return new Commands(
            runContext,
            workingDirectory,
            outputDirectory,
            additionalVars,
            ScriptService.uploadInputFiles(runContext, runContext.render(commands, this.additionalVars)),
            env,
            logSupplier,
            runnerType,
            dockerOptions,
            warningOnStdErr
        );
    }

    public Commands withEnv(Map<String, String> envs) throws IllegalVariableEvaluationException {
        return new Commands(
            runContext,
            workingDirectory,
            outputDirectory,
            additionalVars,
            commands,
            (envs == null ? Map.<String, String>of() : envs)
                .entrySet()
                .stream()
                .map(throwFunction(r -> new AbstractMap.SimpleEntry<>(
                        runContext.render(r.getKey(), additionalVars),
                        runContext.render(r.getValue(), additionalVars)
                    )
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
            logSupplier,
            runnerType,
            dockerOptions,
            warningOnStdErr
        );
    }

    public Commands addAdditionalVars(Map<String, Object> additionalVars) {
        this.additionalVars.putAll(additionalVars);

        return this;
    }

    public Commands addEnv(Map<String, String> envs) {
        this.env.putAll(envs);

        return this;
    }

    public ScriptOutput run() throws Exception {
        RunnerResult runnerResult;

        if (runnerType.equals(RunnerType.DOCKER)) {
            runnerResult = new DockerScriptRunner(runContext.getApplicationContext()).run(this, this.dockerOptions);
        } else {
            runnerResult = new ProcessBuilderScriptRunner().run(this);
        }

        Map<String, URI> outputFiles = ScriptService.uploadOutputFiles(runContext, outputDirectory);

        Map<String, Object> outputsVars = new HashMap<>();
        outputsVars.putAll(runnerResult.getStdOut().getOutputs());
        outputsVars.putAll(runnerResult.getStdErr().getOutputs());

        return ScriptOutput.builder()
            .exitCode(runnerResult.getExitCode())
            .stdOutLineCount(runnerResult.getStdOut().getLogsCount())
            .stdErrLineCount(runnerResult.getStdErr().getLogsCount())
            .warningOnStdErr(this.warningOnStdErr)
            .vars(outputsVars)
             .outputFiles(outputFiles)
            .build();
    }
}

