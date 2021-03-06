package io.omnition.loadgenerator.model.topology.taggen;

import io.omnition.loadgenerator.model.trace.KeyValue;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class OverTimeTagGenerator implements TagGenerator {

    private Random random = new Random();
    private AtomicReference<Double> currVal = new AtomicReference<>();
    private AtomicLong lastStep = new AtomicLong(Instant.now().getEpochSecond());

    public int step;
    public int period;
    public int amplitude;
    public int jitter;
    public int offset;
    public String name;

    @Override
    public void addTagsTo(Map<String, KeyValue> tags) {
        tags.put(name, KeyValue.ofLongType(name, genVal().longValue()));
    }

    private Double genVal() {
        long t = Instant.now().getEpochSecond();
        // this might get updated more than once per step, and that is ok.
        if (t > lastStep.get() + step || currVal.get() == null) {
            // Add jitter here as well just for some extra randomness to the sin curve
            currVal.set(
                amplitude * Math.sin(((double) t) / period * 2 * Math.PI)
                    + offset
                    + random.nextInt(jitter) - (jitter/2)
            );
            lastStep.set(t);
        }

        return currVal.get() + random.nextInt(jitter) - (jitter/2);
    }
}
