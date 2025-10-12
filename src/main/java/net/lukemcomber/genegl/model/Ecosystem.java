package net.lukemcomber.genegl.model;

/*
 * (c) 2025 Luke McOmber
 * This code is licensed under MIT license (see LICENSE.txt for details)
 */

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Ecosystem {

    @JsonProperty("name")
    public String name;

    @JsonProperty("configuration")
    public Map<String,Object> configuration;
}
