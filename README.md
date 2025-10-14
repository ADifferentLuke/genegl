# GeneGL

An OpenGL-powered simulation engine for evolving artificial life through genetics and visualization.

## Overview

**GeneGL** (a portmanteau of *Genetics* and *OpenGL*) is a cross-platform Java application that visualizes evolving organisms using GPU-accelerated rendering. It combines real-time OpenGL rendering with a genetic algorithm simulation to explore emergent biological behaviors.

GeneGL uses [Genetics](https://github.com/ADifferentLuke/Genetics) as its genetic algorithm framework, providing robust tools for genome evolution, mutation, and selection.

## Features

- 🧬 Genetic algorithm simulation of evolving organisms  
- 🌿 Dynamic ecosystem and environmental interactions  
- ⚡ High-performance OpenGL rendering (via LWJGL)  
- 🔍 Adjustable zoom, pan, and grid overlay  
- 🖥️ Real-time HUD showing simulation stats (ticks, epochs, etc.)  

## Requirements

- Java 17 or later  
- OpenGL 3.3+ capable GPU  

## Running the Application

GeneGL expects a single command-line argument: a path or classpath resource to a JSON configuration file that defines the simulation parameters (e.g., world size, organism count, mutation rate, etc.).

Two example configuration files are included in the JAR:

- [`sim.json`](https://github.com/ADifferentLuke/genegl/blob/main/src/main/resources/sim.json) — a minimal configuration example.
- [`sim-parameters.json`](https://github.com/ADifferentLuke/genegl/blob/main/src/main/resources/sim-parameters.json) — a more detailed setup with extended simulation parameters.

You can run GeneGL directly with either of these included files:

```bash
java -jar genegl-0.0.1-SNAPSHOT.jar path/to/config.json 
```

If the file isn’t found on disk, GeneGL will automatically look for it on the classpath (inside the JAR).

### macOS Users

Due to how macOS handles OpenGL and the main application thread, **you must include**:

```bash
java -XstartOnFirstThread -jar genegl-0.0.1-SNAPSHOT.jar sim-parameters.json
```

### Color Key

| Cell Type | Description | Color Preview | RGB (Hex) |
|------------|--------------|----------------|------------|
| Leaf | Energy-producing cell | 🟩 | `#14ff14` |
| Stem | Structural support | 🟢 | `#32a852` |
| Seed (Inactive) | Unactivated ejected seed | 🟥 | `#f00505` |
| Seed (Active) | Activated or rooted seed | 🟨 | `#f0d005` |
| Root | Nutrient absorption cell | 🟫 | `#7d6029` |
| Default | Unspecified / debug cell | ⚪ | `#e6e6e6` |