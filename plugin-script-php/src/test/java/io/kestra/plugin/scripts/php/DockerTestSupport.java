package io.kestra.plugin.scripts.php;

import java.util.concurrent.TimeUnit;

final class DockerTestSupport {

    private DockerTestSupport() {
    }

    static boolean dockerAvailable() {
        try {
            var process = new ProcessBuilder("docker", "info").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
