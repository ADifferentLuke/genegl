package net.lukemcomber.genegl.model;

/*
 * (c) 2025 Luke McOmber
 * This code is licensed under MIT license (see LICENSE.txt for details)
 */

import com.fasterxml.jackson.annotation.JsonProperty;

public class GeneGLConfig {

    @JsonProperty
    public Simulation simulation;

    @JsonProperty
    public Ecosystem ecosystem;
}
