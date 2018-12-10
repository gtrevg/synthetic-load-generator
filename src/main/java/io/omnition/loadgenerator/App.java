package io.omnition.loadgenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import io.omnition.loadgenerator.LoadGeneratorParams.RootServiceRoute;
import io.omnition.loadgenerator.util.JaegerTraceEmitter;
import io.omnition.loadgenerator.util.ScheduledTraceGenerator;

public class App {
    private final static Logger logger = Logger.getLogger(App.class);

    @Parameter(names = "--paramsFile", description = "Name of the file containing the topology params", required = true)
    private String topologyFile;

    @Parameter(names = "--jaegerCollectorUrl", description = "URL of the jaeger collector", required = true)
    private String jaegerCollectorUrl;

    @Parameter(names = "--flushIntervalMillis", description = "How often to flush traces", required = false)
    private long flushIntervalMillis = TimeUnit.SECONDS.toMillis(5);

    @Parameter(names = { "--help", "-h" }, help = true)
    private boolean help;

    private List<ScheduledTraceGenerator> scheduledTraceGenerators = new ArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    public static void main(String[] args) {
        App app = new App();
        JCommander.newBuilder().addObject(app).build().parse(args);
        try {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        app.shutdown();
                    } catch (Exception e) {
                        logger.error("Error shutting down: " + e, e);
                    }
                }
            });
            app.init();
            app.start();
        } catch (Exception e) {
            logger.error("Error running load generator: " + e, e);
            System.exit(1);
        }
    }

    public void init() throws Exception {
        File f = new File(this.topologyFile);
        if(!f.exists() || f.isDirectory()) {
            logger.error("Invalid topology file specified: " + this.topologyFile);
            throw new FileNotFoundException(this.topologyFile);
        }

        String json = new String(Files.readAllBytes(Paths.get(this.topologyFile)), "UTF-8");
        Gson gson = new Gson();
        LoadGeneratorParams params = gson.fromJson(json, LoadGeneratorParams.class);
        logger.info("Params: " + gson.toJson(params));
        JaegerTraceEmitter emitter = new JaegerTraceEmitter(jaegerCollectorUrl, (int)flushIntervalMillis);
        for (RootServiceRoute route : params.rootRoutes) {
            this.scheduledTraceGenerators.add(new ScheduledTraceGenerator(
                    params.topology, route.service, route.route,
                    route.tracesPerHour, emitter));
        }
    }

    public void start() throws Exception {
        for (ScheduledTraceGenerator gen : this.scheduledTraceGenerators) {
            gen.start();
        }
        latch.await();
    }

    public void shutdown() throws Exception {
        for (ScheduledTraceGenerator gen : this.scheduledTraceGenerators) {
            gen.shutdown();
        }
        for (ScheduledTraceGenerator gen : this.scheduledTraceGenerators) {
            gen.awaitTermination();
        }
        latch.countDown();
    }
}