package io.kestra.plugin.scripts.r;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void script() throws Exception {
        // lubridate is installed from Debian's prebuilt `r-cran-lubridate` package rather than with
        // `install.packages()`: the `r-base` image has no CRAN binary for its R version, so
        // install.packages() builds lubridate and its dependencies from source (~90s and thousands
        // of log lines). The dates are also written to an output file rather than asserted on the
        // task run logs, as log emission is asynchronous and the script's own output could land
        // behind that backlog, which made this test flaky.
        Script rScript = Script.builder()
            .id("r-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .beforeCommands(
                Property.ofValue(
                    List.of(
                        "apt-get update -qq",
                        "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq r-cran-lubridate"
                    )
                )
            )
            .outputFiles(Property.ofValue(List.of("dates.txt")))
            .script(
                Property.ofValue(
                    """
                        library(lubridate)
                        dates <- c(
                            as.character(ymd("20100604")),
                            as.character(mdy("06-04-2011")),
                            as.character(dmy("04/06/2012"))
                        )
                        writeLines(dates)
                        writeLines(dates, "dates.txt")"""
                )
            )
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, rScript, ImmutableMap.of());
        ScriptOutput run = rScript.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), greaterThan(1));
        assertThat(run.getStdErrLineCount(), greaterThan(1));

        assertThat(run.getOutputFiles().get("dates.txt").toString(), startsWith("kestra://"));
        String dates = new String(
            storageInterface.get(TenantService.MAIN_TENANT, null, run.getOutputFiles().get("dates.txt")).readAllBytes()
        );
        assertThat(dates.lines().toList(), is(List.of("2010-06-04", "2011-06-04", "2012-06-04")));
    }
}
