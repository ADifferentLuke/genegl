package net.lukemcomber.genegl;

/*
 * (c) 2025 Luke McOmber
 * This code is licensed under MIT license (see LICENSE.txt for details)
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import net.lukemcomber.genegl.model.GeneGLConfig;
import net.lukemcomber.genegl.model.Simulation;
import net.lukemcomber.genetics.MultiEpochEcosystem;
import net.lukemcomber.genetics.model.SpatialCoordinates;
import net.lukemcomber.genetics.model.ecosystem.impl.MultiEpochConfiguration;
import net.lukemcomber.genetics.universes.CustomUniverse;
import net.lukemcomber.genegl.ui.ViewPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class App {

    private final MultiEpochEcosystem ecosystem;
    private final ViewPort viewPort;

    public App(final GeneGLConfig config) throws IOException {

        final CustomUniverse myUniverse = new CustomUniverse(config.ecosystem.configuration);
        final Simulation jsonEcosystem = config.simulation;

        final SpatialCoordinates dimensionsSpace = new SpatialCoordinates(jsonEcosystem.width,
                jsonEcosystem.height, 1);

        ecosystem = new MultiEpochEcosystem(myUniverse,
                MultiEpochConfiguration.builder()
                        .ticksPerDay(jsonEcosystem.ticksPerDay)
                        .size(dimensionsSpace)
                        .maxDays(jsonEcosystem.maxDays)
                        .tickDelayMs(jsonEcosystem.tickDelayMs)
                        .name(jsonEcosystem.name)
                        .epochs(jsonEcosystem.epochs)
                        .reusePopulation(jsonEcosystem.reusePopulationSize)
                        .initialPopulation(jsonEcosystem.initialPopulationSize)
                        .build());
        viewPort = new ViewPort(dimensionsSpace);
    }

    public void simulate(){
        ecosystem.initialize(()-> null);

        viewPort.runEventLoop(ecosystem.getEpochs());
    }

    public static void main(final String[] args) {

        if (1 != args.length) {
            System.err.println("Usage: GeneGL <file>");
            return;
        }

        try (InputStream is = App.class.getResourceAsStream("/logging.properties")) {
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

        System.out.println(args[0]);

        try {
            System.out.println("Loading configuration ....");
            GeneGLConfig config;
            File configFile = new File(args[0]);
            if (configFile.exists()) {
                config = objectMapper.readValue(configFile, GeneGLConfig.class);
            } else {
                // Try classpath fallback
                try (InputStream resourceStream = App.class.getResourceAsStream("/" + args[0])) {
                    if (resourceStream == null) {
                        throw new IOException("Configuration file not found on filesystem or classpath: " + args[0]);
                    }
                    config = objectMapper.readValue(resourceStream, GeneGLConfig.class);
                }
            }
            System.out.println("Loading App ....");
            final App app = new App(config);
            System.out.println("Running ....");
            app.simulate();

        } catch (final IOException e) {
            throw new RuntimeException(e);
        }


    }
}
