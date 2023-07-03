package io.kestra.plugin.scripts.exec.scripts.models;

import io.kestra.core.models.annotations.PluginProperty;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@NoArgsConstructor
@Getter
@Introspected
public class DockerOptions {
    @Schema(
        title = "Docker api uri"
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Docker config file",
        description = "Full file that can be used to configure private registries, ..."
    )
    @PluginProperty(dynamic = true)
    private String config;

    @Schema(
        title = "Docker image to use"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String image;

    @Schema(
        title = "Docker user to use"
    )
    @PluginProperty(dynamic = true)
    protected String user;

    @Schema(
        title = "Docker entrypoint to use"
    )
    @PluginProperty(dynamic = true)
    protected List<String> entryPoint;

    @Schema(
        title = "Docker extra host to use"
    )
    @PluginProperty(dynamic = true)
    protected List<String> extraHosts;

    @Schema(
        title = "Docker network mode to use"
    )
    @PluginProperty(dynamic = true)
    protected String networkMode;

    @Schema(
        title = "List of volumes to mount",
        description = "Must be a valid mount expression as string, example : `/home/user:/app`\n\n" +
            "Volumes mount are disabled by default for security reasons, you must enabled on server configuration with `kestra.tasks.scripts.docker.volume-enabled` to `true`"
    )
    @PluginProperty(dynamic = true)
    protected List<String> volumes;

    @Schema(
        title = "The pull policy for an image",
        description = "Can be used to prevent pulling of image locally build and to avoid pull again the same image"
    )
    @PluginProperty(dynamic = false)
    @Builder.Default
    protected PullPolicy pullPolicy = PullPolicy.IF_NOT_PRESENT;

    @Schema(
        title = "A list of request for devices to be sent to device drivers"
    )
    @PluginProperty(dynamic = false)
    protected List<DeviceRequest> deviceRequests;

    @Schema(
        title = "Limits cpu usage.",
        description = "By default, each container’s access to the host machine’s CPU cycles is unlimited. " +
            "You can set various constraints to limit a given container’s access to the host machine’s CPU cycles."
    )
    @PluginProperty(dynamic = false)
    protected Cpu cpu;

    @Schema(
        title = "Limits memory usage.",
        description = "Docker can enforce hard memory limits, which allow the container to use no more than a " +
            "given amount of user or system memory, or soft limits, which allow the container to use as much " +
            "memory as it needs unless certain conditions are met, such as when the kernel detects low memory " +
            "or contention on the host machine. Some of these options have different effects when used alone or " +
            "when more than one option is set."
    )
    @PluginProperty(dynamic = false)
    protected Memory memory;

    @Introspected
    @Schema(
        title = "The PullPolicy for a container and the tag of the image affect when docker attempts to pull (download) the specified image."
    )
    public enum PullPolicy {
        IF_NOT_PRESENT,
        ALWAYS,
        NEVER
    }


    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Introspected
    @Schema(
        title = "A request for devices to be sent to device drivers"
    )
    public static class DeviceRequest {
        private String driver;
        private Integer count;
        private List<String> deviceIds;

        @Schema(
            title = "A list of capabilities; an OR list of AND lists of capabilities."
        )
        private List<List<String>> capabilities;

        @Schema(
            title = "Driver-specific options, specified as a key/value pairs.",
            description = "These options are passed directly to the driver."
        )
        private Map<String, String> options;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Introspected
    public static class Cpu {
        @Schema(
            title = "Specify how much of the available CPU resources a container can use.",
            description = "For instance, if the host machine has two CPUs and you set `cpus:\"1.5\"`, the container is guaranteed at most one and a half of the CPUs"
        )
        private Long cpus;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Introspected
    public static class Memory {
        @Schema(
            title = "The maximum amount of memory the container can use.",
            description = "That is, you must set the value to at least 6 megabytes."
        )
        @PluginProperty(dynamic = true)
        private String memory;

        @Schema(
            title = "The amount of memory this container is allowed to swap to disk",
            description = "If `memory` and `memorySwap` are set to the same value, this prevents containers from " +
                "using any swap. This is because `memorySwap` is the amount of combined memory and swap that can be " +
                "used, while `memory` is only the amount of physical memory that can be used."
        )
        @PluginProperty(dynamic = true)
        private String memorySwap;

        @Schema(
            title = "The amount of memory this container is allowed to swap to disk",
            description = "By default, the host kernel can swap out a percentage of anonymous pages used by a " +
                "container. You can set `memorySwappiness` to a value between 0 and 100, to tune this percentage."
        )
        @PluginProperty(dynamic = true)
        private String memorySwappiness;

        @Schema(
            title = "Allows you to specify a soft limit smaller than --memory which is activated when Docker detects contention or low memory on the host machine.",
            description = "If you use `memoryReservation`, it must be set lower than `memory` for it to take precedence. " +
                "Because it is a soft limit, it does not guarantee that the container doesn’t exceed the limit."
        )
        @PluginProperty(dynamic = true)
        private String memoryReservation;

        @Schema(
            title = "The maximum amount of kernel memory the container can use.",
            description = "The minimum allowed value is 4m. Because kernel memory cannot be swapped out, a " +
                "container which is starved of kernel memory may block host machine resources, which can have " +
                "side effects on the host machine and on other containers. " +
                "See [--kernel-memory](https://docs.docker.com/config/containers/resource_constraints/#--kernel-memory-details) details."
        )
        @PluginProperty(dynamic = true)
        private String kernelMemory;

        @Schema(
            title = "By default, if an out-of-memory (OOM) error occurs, the kernel kills processes in a container.",
            description = "To change this behavior, use the `oomKillDisable` option. Only disable the OOM killer " +
                "on containers where you have also set the `memory` option. If the `memory` flag is not set, the host " +
                "can run out of memory and the kernel may need to kill the host system’s processes to free memory."
        )
        @PluginProperty(dynamic = false)
        private Boolean oomKillDisable;
    }
}
