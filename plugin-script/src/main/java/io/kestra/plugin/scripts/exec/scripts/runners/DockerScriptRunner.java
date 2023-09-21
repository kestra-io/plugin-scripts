package io.kestra.plugin.scripts.exec.scripts.runners;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ConnectionClosedException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.utils.RetryUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import lombok.SneakyThrows;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public class DockerScriptRunner {
    private static final ReadableBytesTypeConverter READABLE_BYTES_TYPE_CONVERTER = new ReadableBytesTypeConverter();
    public static final Pattern NEWLINE_PATTERN = Pattern.compile("[\\r\\n]+$");

    private final RetryUtils retryUtils;

    private final Boolean volumesEnabled;

    public DockerScriptRunner(ApplicationContext applicationContext) {
        this.retryUtils = applicationContext.getBean(RetryUtils.class);
        this.volumesEnabled = applicationContext.getProperty(
            "kestra.tasks.scripts.docker.volume-enabled",
            Boolean.class
        ).orElse(false);
    }


    private static DockerClient dockerClient(DockerOptions dockerOptions, RunContext runContext, Path workingDirectory) throws IOException, IllegalVariableEvaluationException {
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(DockerService.findHost(runContext, dockerOptions != null ? dockerOptions.getHost() : null));

        if (dockerOptions != null) {
            if (dockerOptions.getConfig() != null || dockerOptions.getCredentials() != null) {

                Path config = DockerService.createConfig(
                    runContext,
                    dockerOptions.getConfig(),
                    List.of(dockerOptions.getCredentials()),
                    dockerOptions.getImage()
                );

                dockerClientConfigBuilder.withDockerConfig(config.toFile().getAbsolutePath());
            }
        }

        DockerClientConfig dockerClientConfig = dockerClientConfigBuilder.build();

        return DockerService.client(dockerClientConfig);
    }

    public RunnerResult run(CommandsWrapper commands, DockerOptions dockerOptions) throws Exception {
        if (dockerOptions == null) {
            throw new IllegalArgumentException("Missing required docker properties");
        }

        RunContext runContext = commands.getRunContext();
        Logger logger = commands.getRunContext().logger();
        String image = runContext.render(dockerOptions.getImage(), commands.getAdditionalVars());
        AbstractLogConsumer defaultLogConsumer = commands.getLogConsumer();

        try (DockerClient dockerClient = dockerClient(dockerOptions, runContext, commands.getWorkingDirectory())) {
            // create container
            CreateContainerCmd container = configure(commands, dockerClient, dockerOptions);

            // pull image
            if (dockerOptions.getPullPolicy() != DockerOptions.PullPolicy.NEVER) {
                pullImage(dockerClient, image, dockerOptions.getPullPolicy(), logger);
            }

            // start container
            CreateContainerResponse exec = container.exec();
            dockerClient.startContainerCmd(exec.getId()).exec();
            logger.debug(
                "Starting command with container id {} [{}]",
                exec.getId(),
                String.join(" ", commands.getCommands())
            );

            AtomicBoolean ended = new AtomicBoolean(false);

            try {
                dockerClient.logContainerCmd(exec.getId())
                    .withFollowStream(true)
                    .withStdErr(true)
                    .withStdOut(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        private final Map<StreamType, StringBuilder> logBuffers = new HashMap<>();

                        @SneakyThrows
                        @Override
                        public void onNext(Frame frame) {
                            String frameStr = new String(frame.getPayload());

                            Matcher newLineMatcher = NEWLINE_PATTERN.matcher(frameStr);
                            logBuffers.computeIfAbsent(frame.getStreamType(), streamType -> new StringBuilder())
                                .append(newLineMatcher.replaceAll(""));

                            if (newLineMatcher.reset().find()) {
                                StringBuilder logBuffer = logBuffers.get(frame.getStreamType());
                                defaultLogConsumer.accept(logBuffer.toString(), frame.getStreamType() == StreamType.STDERR);
                                logBuffer.setLength(0);
                            }
                        }

                        @Override
                        public void onComplete() {
                            // Still flush last line even if there is no newline at the end
                            try {
                                logBuffers.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).forEach(throwConsumer(entry -> {
                                    String log = entry.getValue().toString();
                                    defaultLogConsumer.accept(log, entry.getKey() == StreamType.STDERR);
                                }));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            ended.set(true);
                            super.onComplete();
                        }
                    });

                WaitContainerResultCallback result = dockerClient.waitContainerCmd(exec.getId()).start();

                Integer exitCode = result.awaitStatusCode();
                Await.until(ended::get);

                if (exitCode != 0) {
                    throw new ScriptException(exitCode, defaultLogConsumer.getStdOutCount(), defaultLogConsumer.getStdErrCount());
                } else {
                    logger.debug("Command succeed with code " + exitCode);
                }

                return new RunnerResult(exitCode, defaultLogConsumer);
            } finally {
                try {
                    var inspect = dockerClient.inspectContainerCmd(exec.getId()).exec();
                    if (Boolean.TRUE.equals(inspect.getState().getRunning())) {
                        // kill container as it's still running, this means there was an exception and the container didn't
                        // come to a normal end.
                        try {
                            dockerClient.killContainerCmd(exec.getId()).exec();
                        } catch (Exception e) {
                            logger.error("Unable to kill a running container", e);
                        }
                    }
                    dockerClient.removeContainerCmd(exec.getId()).exec();
                } catch (Exception ignored) {

                }
            }
        }
    }

    private CreateContainerCmd configure(CommandsWrapper commands, DockerClient dockerClient, DockerOptions dockerOptions) throws IllegalVariableEvaluationException {
        if (dockerOptions.getImage() == null) {
            throw new IllegalArgumentException("Missing docker image");
        }

        RunContext runContext = commands.getRunContext();
        Path workingDirectory = commands.getWorkingDirectory();
        Map<String, Object> additionalVars = commands.getAdditionalVars();
        String image = runContext.render(dockerOptions.getImage(), additionalVars);


        CreateContainerCmd container = dockerClient.createContainerCmd(image);
        addMetadata(runContext, container);

        HostConfig hostConfig = new HostConfig();

        if (commands.getEnv() != null && !commands.getEnv().isEmpty()) {
            container.withEnv(commands.getEnv()
                .entrySet()
                .stream()
                .map(throwFunction(r -> r.getKey() + "=" + r.getValue()))
                .collect(Collectors.toList())
            );
        }

        if (workingDirectory != null) {
            container.withWorkingDir(workingDirectory.toFile().getAbsolutePath());
        }

        List<Bind> binds = new ArrayList<>();

        if (workingDirectory != null) {
            binds.add(new Bind(
                workingDirectory.toAbsolutePath().toString(),
                new Volume(workingDirectory.toAbsolutePath().toString()),
                AccessMode.rw
            ));
        }

        if (dockerOptions.getUser() != null) {
            container.withUser(runContext.render(dockerOptions.getUser(), additionalVars));
        }

        if (dockerOptions.getEntryPoint() != null) {
            container.withEntrypoint(runContext.render(dockerOptions.getEntryPoint(), additionalVars));
        }

        if (dockerOptions.getExtraHosts() != null) {
            hostConfig.withExtraHosts(runContext.render(dockerOptions.getExtraHosts(), additionalVars)
                .toArray(String[]::new));
        }

        if (this.volumesEnabled && dockerOptions.getVolumes() != null) {
            binds.addAll(runContext.render(dockerOptions.getVolumes())
                .stream()
                .map(Bind::parse)
                .toList()
            );
        }

        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }

        if (dockerOptions.getDeviceRequests() != null) {
            hostConfig.withDeviceRequests(dockerOptions
                .getDeviceRequests()
                .stream()
                .map(throwFunction(deviceRequest -> new DeviceRequest()
                    .withDriver(runContext.render(deviceRequest.getDriver()))
                    .withCount(deviceRequest.getCount())
                    .withDeviceIds(runContext.render(deviceRequest.getDeviceIds()))
                    .withCapabilities(deviceRequest.getCapabilities())
                    .withOptions(deviceRequest.getOptions())
                ))
                .collect(Collectors.toList())
            );
        }

        if (dockerOptions.getCpu() != null) {
            if (dockerOptions.getCpu().getCpus() != null) {
                hostConfig.withCpuQuota(dockerOptions.getCpu().getCpus() * 10000L);
            }
        }

        if (dockerOptions.getMemory() != null) {
            if (dockerOptions.getMemory().getMemory() != null) {
                hostConfig.withMemory(convertBytes(runContext.render(dockerOptions.getMemory().getMemory())));
            }

            if (dockerOptions.getMemory().getMemorySwap() != null) {
                hostConfig.withMemorySwap(convertBytes(runContext.render(dockerOptions.getMemory()
                    .getMemorySwap())));
            }

            if (dockerOptions.getMemory().getMemorySwappiness() != null) {
                hostConfig.withMemorySwappiness(convertBytes(runContext.render(dockerOptions.getMemory()
                    .getMemorySwappiness())));
            }

            if (dockerOptions.getMemory().getMemoryReservation() != null) {
                hostConfig.withMemoryReservation(convertBytes(runContext.render(dockerOptions.getMemory()
                    .getMemoryReservation())));
            }

            if (dockerOptions.getMemory().getKernelMemory() != null) {
                hostConfig.withKernelMemory(convertBytes(runContext.render(dockerOptions.getMemory()
                    .getKernelMemory())));
            }

            if (dockerOptions.getMemory().getOomKillDisable() != null) {
                hostConfig.withOomKillDisable(dockerOptions.getMemory().getOomKillDisable());
            }
        }

        if (dockerOptions.getNetworkMode() != null) {
            hostConfig.withNetworkMode(runContext.render(dockerOptions.getNetworkMode(), additionalVars));
        }

        return container
            .withHostConfig(hostConfig)
            .withCmd(commands.getCommands())
            .withAttachStderr(true)
            .withAttachStdout(true);
    }

    @SuppressWarnings("unchecked")
    private static void addMetadata(RunContext runContext, CreateContainerCmd container) {
        Map<String, String> flow = (Map<String, String>) runContext.getVariables().get("flow");
        Map<String, String> task = (Map<String, String>) runContext.getVariables().get("task");
        Map<String, String> execution = (Map<String, String>) runContext.getVariables().get("execution");
        Map<String, String> taskrun = (Map<String, String>) runContext.getVariables().get("taskrun");

        container.withLabels(ImmutableMap.of(
            "flow.kestra.io/id", flow.get("id"),
            "flow.kestra.io/namespace", flow.get("namespace"),
            "task.kestra.io/id", task.get("id"),
            "execution.kestra.io/id", execution.get("id"),
            "taskrun.kestra.io/id", taskrun.get("id")
        ));
    }

    private static Long convertBytes(String bytes) {
        return READABLE_BYTES_TYPE_CONVERTER.convert(bytes, Number.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + bytes + "'"))
            .longValue();
    }

    private void pullImage(DockerClient dockerClient, String image, DockerOptions.PullPolicy policy, Logger logger) {
        NameParser.ReposTag imageParse = NameParser.parseRepositoryTag(image);

        if (policy.equals(DockerOptions.PullPolicy.IF_NOT_PRESENT)) {
            try {
                dockerClient.inspectImageCmd(image).exec();
                return;
            } catch (NotFoundException ignored) {

            }
        }

        try (PullImageCmd pull = dockerClient.pullImageCmd(image)) {
            retryUtils.<Boolean, InternalServerErrorException>of(
                Exponential.builder()
                    .delayFactor(2.0)
                    .interval(Duration.ofSeconds(5))
                    .maxInterval(Duration.ofSeconds(120))
                    .maxAttempt(5)
                    .build()
            ).run(
                (bool, throwable) -> throwable instanceof InternalServerErrorException ||
                    throwable.getCause() instanceof ConnectionClosedException,
                () -> {
                    String tag = !imageParse.tag.isEmpty() ? imageParse.tag : "latest";
                    String repository = pull.getRepository().contains(":") ? pull.getRepository().split(":")[0] : pull.getRepository();
                    pull
                        .withTag(tag)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();

                    logger.debug("Image pulled [{}:{}]", repository, tag);

                    return true;
                }
            );
        }
    }
}
