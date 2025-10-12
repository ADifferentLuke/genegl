package net.lukemcomber.genegl.model;

/*
 * (c) 2025 Luke McOmber
 * This code is licensed under MIT license (see LICENSE.txt for details)
 */

import com.fasterxml.jackson.annotation.JsonProperty;

public class Simulation {

    @JsonProperty
    public String name;

    @JsonProperty
    public Integer width;

    @JsonProperty
    public Integer height;

    @JsonProperty
    public Integer ticksPerDay;

    @JsonProperty
    public Integer tickDelayMs;

    @JsonProperty
    public Integer maxDays;

    @JsonProperty
    public Integer epochs;

    @JsonProperty
    public Integer initialPopulationSize;

    @JsonProperty
    public Integer reusePopulationSize;
}
