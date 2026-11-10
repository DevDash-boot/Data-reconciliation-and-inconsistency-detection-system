package com.example.recon.generator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 실행 예)
 *   ./gradlew generateSample -Psize=100000 -PoutDir=data/100k
 *   java ... SampleDataGeneratorMain --size=100000 --missingA=0.02 --outDir=data/100k
 */
public final class SampleDataGeneratorMain {

    private SampleDataGeneratorMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                opts.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        SampleDataGenerator.Config d = SampleDataGenerator.Config.defaults(Path.of("data/sample"));
        SampleDataGenerator.Config cfg = new SampleDataGenerator.Config(
                Integer.parseInt(opts.getOrDefault("size", String.valueOf(d.size()))),
                Double.parseDouble(opts.getOrDefault("missingA", String.valueOf(d.missingInA()))),
                Double.parseDouble(opts.getOrDefault("missingB", String.valueOf(d.missingInB()))),
                Double.parseDouble(opts.getOrDefault("duplicate", String.valueOf(d.duplicate()))),
                Double.parseDouble(opts.getOrDefault("valueDiff", String.valueOf(d.valueDiff()))),
                Double.parseDouble(opts.getOrDefault("excluded", String.valueOf(d.excluded()))),
                Long.parseLong(opts.getOrDefault("seed", String.valueOf(d.seed()))),
                Path.of(opts.getOrDefault("outDir", "data/sample")));

        SampleDataGenerator.Expected e = SampleDataGenerator.generate(cfg);
        System.out.println("생성 완료: " + cfg.outDir().toAbsolutePath());
        System.out.println("  A(결제) 행: " + e.totalA() + " (대조 제외 " + e.excludedA() + ")");
        System.out.println("  B(주문) 행: " + e.totalB() + " (대조 제외 " + e.excludedB() + ")");
        System.out.println("  기대 불일치: MISSING_IN_A=" + e.missingInA()
                + ", MISSING_IN_B=" + e.missingInB()
                + ", DUPLICATE=" + e.duplicate()
                + ", VALUE_DIFF=" + e.valueDiff());
    }
}
