package net.lukemcomber.genegl.ui;

/*
 * (c) 2025 Luke McOmber
 * This code is licensed under MIT license (see LICENSE.txt for details)
 */

import net.lukemcomber.genetics.Ecosystem;
import net.lukemcomber.genetics.biology.Cell;
import net.lukemcomber.genetics.biology.Organism;
import net.lukemcomber.genetics.biology.plant.cells.EjectedSeedCell;
import net.lukemcomber.genetics.io.CellHelper;
import net.lukemcomber.genetics.model.SpatialCoordinates;
import net.lukemcomber.genetics.model.TemporalCoordinates;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32C.GL_PROGRAM_POINT_SIZE;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ViewPort {

    // NanoVG HUD
    static long vg = 0;           // NanoVG context
    static int fontId = -1;       // font handle

    static int vao, vboPos, vboColor, prog;
    static long window;
    static GLFWErrorCallback errCallback;

    static final int INITIAL_POINT_CAPACITY = 1024;

    // Streaming buffer bookkeeping
    static int posCapacityFloats = 0;
    static int colCapacityFloats = 0;
    static int pointCount = 0;
    static float[] posBuf = new float[0];
    static float[] colBuf = new float[0];
    // Reusable native buffers to avoid per-frame stack allocations
    static FloatBuffer posFB = null;
    static FloatBuffer colFB = null;

    // Stats / title cadence
    static long simStep = 0;
    static int frames = 0;
    static double lastTitleUpdate = 0.0;
    static double fps = 0.0;

    // --- Camera (zoom/pan) & interaction ---
    static float zoom = 1.0f;        // 1 = 1:1, >1 zooms in, <1 zooms out
    static float panX = 0.0f;        // screen-space pan in pixels
    static float panY = 0.0f;
    static boolean isPanning = false;
    static double lastMouseX = 0.0;
    static double lastMouseY = 0.0;
    static float basePointSize = 6.0f; // bumped default size for visibility
    static boolean gridMode = true; // draw each world cell as a colored square (snap to grid)
    static int pixelScale = 4; // render scale: each world cell = 4×4 screen pixels

    private final int width;
    private final int height;

    static int framebufferW, framebufferH;


    public ViewPort(final SpatialCoordinates dimensions) {
        width = dimensions.xAxis();
        height = dimensions.yAxis();

        renderWindow(width * pixelScale, height * pixelScale);
        init(dimensions);
    }

    private void drawHUD(final Ecosystem ecosystem) {
        if (vg == 0) return; // HUD disabled if No VG; draw panel even without font

        // HiDPI scale factor
        int[] fbW = new int[1], fbH = new int[1], winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        glfwGetWindowSize(window, winW, winH);
        float pxRatio = (winW[0] > 0) ? (float) fbW[0] / (float) winW[0] : 1.0f;

        NanoVG.nvgBeginFrame(vg, winW[0], winH[0], pxRatio);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Panel color (semi-transparent black)
            NVGColor panel = NVGColor.malloc(stack);
            NanoVG.nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 120, panel);

            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgRect(vg, 10, 10, 480, 64);
            NanoVG.nvgFillColor(vg, panel);
            NanoVG.nvgFill(vg);

            // Text setup and color (white) — only if font is available
            if (fontId != -1) {
                NanoVG.nvgFontSize(vg, 20f);
                NanoVG.nvgFontFaceId(vg, fontId);

                NVGColor white = NVGColor.malloc(stack);
                NanoVG.nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, white);
                NanoVG.nvgFillColor(vg, white);

                final TemporalCoordinates temporalCoordinates = ecosystem.getTime();
                String line1 = String.format("Total Ticks %,d  |  Day %,d  |  Tick %d ", temporalCoordinates.totalTicks(),temporalCoordinates.totalDays(), temporalCoordinates.currentTick());
                String line2 = String.format("%s  |  zoom %.2fx  |  pan(%.0f,%.0f)  |  grid %s", ecosystem.getName(), zoom, panX, panY, gridMode ? "ON" : "OFF");

                NanoVG.nvgText(vg, 20, 32, line1);
                NanoVG.nvgText(vg, 20, 56, line2);
            }
        }

        NanoVG.nvgEndFrame(vg);
    }

    public void runEventLoop(final ConcurrentLinkedDeque<Ecosystem> supplier) {
        try {
            while (!glfwWindowShouldClose(window)) {
                simStep++;
                frames++;
                double t = glfwGetTime();
                if (t - lastTitleUpdate >= 1.0) {
                    fps = frames / Math.max(1e-6, (t - lastTitleUpdate));
                    frames = 0;
                    lastTitleUpdate = t;
                    String title = String.format("GeneGL — cells %,d | FPS %.1f", pointCount, fps);
                    glfwSetWindowTitle(window, title);
                }

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                final Set<Cell> allCells = new HashSet<>();
                final Ecosystem ecosystem = supplier.peekLast();
                if( null != ecosystem ) {
                    final Iterator<Organism> iter = ecosystem.getTerrain().getOrganisms();
                    while (iter.hasNext()) {
                        try {
                            allCells.addAll(CellHelper.getAllOrganismsCells(iter.next().getFirstCell()));
                        } catch( final RuntimeException e ){
                            //System.err.println("Resetting");
                        }
                    }

                    // Pack + stream upload current cells, then draw points
                    updateAndUploadFromCells(allCells);
                    drawPoints();
                    // HUD last (overlay)
                    drawHUD(ecosystem);
                }


                glfwSwapBuffers(window);
                glfwPollEvents();
            }
        } finally {
            cleanup();
        }
    }

    private void init(final SpatialCoordinates dimensions) {
        // --- shaders ---
        String vsSrc = "#version 330 core\n" +
                "layout(location=0) in vec2 aPos;\n" +
                "layout(location=1) in vec4 aColor;\n" +
                "uniform vec2 uResolution;\n" +
                "uniform float uScale;\n" +
                "uniform vec2 uPan;\n" +
                "uniform float uPointSize;\n" +
                "uniform float uGridMode;\n" +
                "out vec4 vColor;\n" +
                "void main(){\n" +
                "  // When grid mode is on, snap to the center of the integer world cell and size = 1 cell in pixels.\n" +
                "  vec2 world = mix(aPos, floor(aPos) + vec2(0.5), uGridMode);\n" +
                "  float pointPx = max(1.0, mix(uPointSize * uScale, uScale, uGridMode));\n" +
                "  vec2 screen = world * uScale + uPan;\n" +
                "  vec2 zeroToOne = screen / uResolution;\n" +
                "  vec2 zeroToTwo = zeroToOne * 2.0;\n" +
                "  vec2 clip = zeroToTwo - 1.0;\n" +
                "  gl_Position = vec4(clip * vec2(1.0,-1.0), 0.0, 1.0);\n" +
                "  gl_PointSize = pointPx;\n" +
                "  vColor = aColor;\n" +
                "}";

        String fsSrc = "#version 330 core\n" +
                "in vec4 vColor;\n" +
                "out vec4 fragColor;\n" +
                "void main(){ fragColor = vColor; }";

        int vs = createShader(GL_VERTEX_SHADER, vsSrc);
        int fs = createShader(GL_FRAGMENT_SHADER, fsSrc);

        prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == 0)
            throw new RuntimeException("Program link failed: " + glGetProgramInfoLog(prog));
        glDeleteShader(vs);
        glDeleteShader(fs);

        // --- buffers ---
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vboPos = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboPos);

        glBufferData(GL_ARRAY_BUFFER,
                (long) INITIAL_POINT_CAPACITY * 2L * Float.BYTES,  // capacity for N points * 2 floats
                GL_STREAM_DRAW);                                   // streaming hint
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);

        vboColor = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboColor);
        glBufferData(GL_ARRAY_BUFFER,
                (long) INITIAL_POINT_CAPACITY * 4L * Float.BYTES,  // capacity for N points * 4 floats
                GL_STREAM_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 0, 0L);

        // Set initial capacities to avoid zero-sized uploads
        posCapacityFloats = INITIAL_POINT_CAPACITY * 2;
        colCapacityFloats = INITIAL_POINT_CAPACITY * 4;

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        initOverlay();
    }

    // --- Streaming helpers ---
    private static void ensureBufferCapacity(int points) {
        int needPos = points * 2; // floats
        int needCol = points * 4;

        if (needPos > posCapacityFloats) {
            posCapacityFloats = Math.max(needPos, posCapacityFloats * 2 + 1024);
            glBindBuffer(GL_ARRAY_BUFFER, vboPos);
            glBufferData(GL_ARRAY_BUFFER, (long) posCapacityFloats * Float.BYTES, GL_STREAM_DRAW);
        }
        if (needCol > colCapacityFloats) {
            colCapacityFloats = Math.max(needCol, colCapacityFloats * 2 + 2048);
            glBindBuffer(GL_ARRAY_BUFFER, vboColor);
            glBufferData(GL_ARRAY_BUFFER, (long) colCapacityFloats * Float.BYTES, GL_STREAM_DRAW);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private static void ensureCpuArrays(int points) {
        int needPos = points * 2;
        int needCol = points * 4;
        if (posBuf.length < needPos) posBuf = new float[Math.max(needPos, posBuf.length * 2 + 1024)];
        if (colBuf.length < needCol) colBuf = new float[Math.max(needCol, colBuf.length * 2 + 2048)];
    }

    private static void ensureDirectBuffers(int points) {
        int needPos = points * 2; // floats
        int needCol = points * 4;
        if (posFB == null || posFB.capacity() < needPos) {
            posFB = BufferUtils.createFloatBuffer(Math.max(needPos, 1 << 15)); // grow generously
        }
        if (colFB == null || colFB.capacity() < needCol) {
            colFB = BufferUtils.createFloatBuffer(Math.max(needCol, 1 << 15));
        }
    }

    private static void updateAndUploadFromCells(Set<Cell> cells) {
        pointCount = cells.size();
        ensureBufferCapacity(pointCount);
        ensureCpuArrays(pointCount);

        int pj = 0, cj = 0;
        for (Cell c : cells) {
            posBuf[pj++] = (float) c.getCoordinates().xAxis();
            posBuf[pj++] = (float) c.getCoordinates().yAxis();

            float r, g, b, a = 1f;
            switch (c.getCellType()) {
                case "leaf": r = 0.07843138f; g = 1.0f; b = 0.07843138f; break; //leaf
                case "stem": r = 0.19607843f; g = 0.65882355f; b = 0.32156864f; break;// stem
                case "seed": {
                    if( c instanceof EjectedSeedCell && !((EjectedSeedCell)c).isActivated()) {
                        r = 0.9411765f; g = 0.019607844f; b = 0.019607844f; // #f00505
                    } else {
                        r = 0.9411765f;
                        g = 0.8156863f;
                        b = 0.019607844f;
                    }
                    break;// #f0d005
                }
                case "root": r = 0.49019608f; g = 0.3764706f; b = 0.16078432f; break;// root
                default: r = 0.90f; g = 0.90f; b = 0.90f;
            }
            colBuf[cj++] = r; colBuf[cj++] = g; colBuf[cj++] = b; colBuf[cj++] = a;
        }

        // STREAM: orphan + subdata to avoid stalls, using persistent direct buffers
        ensureDirectBuffers(pointCount);

        // positions
        posFB.clear();
        posFB.put(posBuf, 0, pointCount * 2).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboPos);
        glBufferData(GL_ARRAY_BUFFER, (long) posCapacityFloats * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, posFB);

        // colors
        colFB.clear();
        colFB.put(colBuf, 0, pointCount * 4).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboColor);
        glBufferData(GL_ARRAY_BUFFER, (long) colCapacityFloats * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, colFB);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void drawPoints() {
        if (pointCount == 0) return;
        glUseProgram(prog);
        glBindVertexArray(vao);
        int uRes = glGetUniformLocation(prog, "uResolution");
        glUniform2f(uRes, (float) framebufferW, (float) framebufferH);
        int uScale = glGetUniformLocation(prog, "uScale");
        int uPan = glGetUniformLocation(prog, "uPan");
        int uPt = glGetUniformLocation(prog, "uPointSize");

        // Zoom around origin by scaling; pan is in pixels (positive panX moves right, panY down)
        glUniform1f(uScale, zoom);
        glUniform2f(uPan, panX, panY);
        // Keep points visible even when very zoomed out (clamp done in shader too)
        glUniform1f(uPt, basePointSize);

        int uGrid = glGetUniformLocation(prog, "uGridMode");
        glUniform1f(uGrid, gridMode ? 1.0f : 0.0f);

        glDrawArrays(GL_POINTS, 0, pointCount);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    void initOverlay() {
        // Create NanoVG context (GL3 backend)
        vg = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);

        if (vg == 0) {
            System.err.println("[Overlay] Failed to create NanoVG context; Overlay disabled.");
            return;
        }
        try (InputStream in = ViewPort.class.getResourceAsStream("/fonts/Roboto.ttf")) {
            if (in != null) {
                Path tmp = Files.createTempFile("roboto", ".ttf");
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                fontId = NanoVG.nvgCreateFont(vg, "ui", tmp.toString());
            } else {
                System.err.println("[Overlay] /fonts/Roboto.ttf not found on classpath; HUD text disabled.");
                fontId = -1;
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (fontId == -1) {
            System.err.println("[HUD] Could not load a system font. Set -Dgenegl.font=/path/to/font.ttf to enable HUD text.");
        }
    }

    private void renderWindow(final int width, final int height) {
        errCallback = GLFWErrorCallback.createPrint(System.err);
        errCallback.set();

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwSetTime(0.0);


        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        // macOS compatibility
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        // Create the window
        window = glfwCreateWindow(width, height, "GeneGL", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Show the window
        glfwShowWindow(window);

        // Create OpenGL capabilities for the current context
        GL.createCapabilities();

        int[] fbW = new int[1], fbH = new int[1];
        int[] winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        glfwGetWindowSize(window, winW, winH);
        framebufferW = fbW[0];
        framebufferH = fbH[0];

        // HiDPI ratio: device pixels per window pixel (e.g., 2.0 on Retina)
        float pxRatio = (winW[0] > 0) ? (float) framebufferW / (float) winW[0] : 1.0f;

        // Initialize zoom in *device pixels per world unit* so a 480x270 world at pixelScale=4
        // exactly fills a 1920x1080 framebuffer even on Retina (pxRatio ~ 2.0 → zoom = 8.0).
        zoom = pixelScale * pxRatio;

        // Let the vertex shader control point size
        glEnable(GL_PROGRAM_POINT_SIZE);
        // Enable blending for alpha in cell colors
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glViewport(0, 0, framebufferW, framebufferH);
        // Basic GL setup
        glClearColor(0f, 0f, 0f, 1f); // black background

        //initGLResources();
        //initHUD();

        // Mouse wheel zoom (scale multiplicatively)
        glfwSetScrollCallback(window, (w, xoff, yoff) -> {
            // Zoom towards the cursor position (simple version: around origin). Positive yoff zooms in.
            float factor = (float)Math.exp(yoff * 0.1);
            zoom *= factor;
            if (zoom < 0.1f) zoom = 0.1f;
            if (zoom > 100f) zoom = 100f;
        });

        // Middle mouse (or right mouse) drag to pan
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if ((button == GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW_MOUSE_BUTTON_RIGHT)) {
                if (action == GLFW_PRESS) {
                    isPanning = true;
                    double[] mx = new double[1], my = new double[1];
                    glfwGetCursorPos(window, mx, my);
                    lastMouseX = mx[0];
                    lastMouseY = my[0];
                } else if (action == GLFW_RELEASE) {
                    isPanning = false;
                }
            }
        });

        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (isPanning) {
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                lastMouseX = xpos;
                lastMouseY = ypos;
                // Pan in screen pixels (dy positive is down)
                panX += (float) dx;
                panY += (float) dy;
            }
        });

        // Keyboard: ESC to quit, WASD to pan, +/- to zoom, SPACE to reset view
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS && action != GLFW_REPEAT) return;

            // Quit
            if (key == GLFW_KEY_ESCAPE) {
                //glfwSetWindowShouldClose(w, true);
                return;
            }

            // Pan step in pixels (independent of zoom since pan is screen-space)
            float panStep = 50.0f;

            switch (key) {
                // WASD panning
                case GLFW_KEY_W: panY += panStep; break; // up
                case GLFW_KEY_S: panY -= panStep; break; // down
                case GLFW_KEY_A: panX += panStep; break; // left
                case GLFW_KEY_D: panX -= panStep; break; // right

                // Zoom in (+) and out (-), including keypad variants
                case GLFW_KEY_EQUAL: // '+' shares '=' key without shift on many layouts
                case GLFW_KEY_KP_ADD: {
                    float factor = 1.10f; // +10%
                    zoom *= factor;
                    if (zoom > 100f) zoom = 100f;
                    break;
                }
                case GLFW_KEY_MINUS:
                case GLFW_KEY_KP_SUBTRACT: {
                    float factor = 1.0f / 1.10f; // -10%
                    zoom *= factor;
                    if (zoom < 0.1f) zoom = 0.1f;
                    break;
                }

                // Reset view
                case GLFW_KEY_SPACE:
                    zoom = 1.0f;
                    panX = 0.0f;
                    panY = 0.0f;
                    break;

                case GLFW_KEY_G:
                    gridMode = !gridMode; // toggle grid highlighting
                    break;

                default:
                    // no-op
            }
        });

        glfwSetFramebufferSizeCallback(window, (w, wfb, hfb) -> {
            framebufferW = wfb;
            framebufferH = hfb;
            glViewport(0, 0, framebufferW, framebufferH);

            // Update pxRatio so future zoom resets (SPACE) or pixel-locked modes remain correct
            int[] ww = new int[1], wh = new int[1];
            glfwGetWindowSize(window, ww, wh);
            float pxRatioNow = (ww[0] > 0) ? (float) framebufferW / (float) ww[0] : 1.0f;
            // Do not override user zoom here; just keep pixelScale-based resets correct
            // (If you want to lock zoom to pxRatio, set zoom = pixelScale * pxRatioNow instead.)
        });
    }

    private void cleanup() {
        if (prog != 0) glDeleteProgram(prog);
        if (vboPos != 0) glDeleteBuffers(vboPos);
        if (vboColor != 0) glDeleteBuffers(vboColor);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vg != 0) {
            NanoVGGL3.nvgDelete(vg);
            vg = 0;
        }
        glfwDestroyWindow(window);
        glfwTerminate();
        if (errCallback != null) errCallback.free();
    }

    private int createShader(int type, String src) {
        int sh = glCreateShader(type);
        glShaderSource(sh, src);
        glCompileShader(sh);
        if (glGetShaderi(sh, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(sh));
        }
        return sh;
    }
/*
    static void drawPoints() {
        glUseProgram(prog);
        glBindVertexArray(vao);
        int uRes = glGetUniformLocation(prog, "uResolution");
        glUniform2f(uRes, WORLD_W, WORLD_H);
        glDrawArrays(GL_POINTS, 0, N);
        glBindVertexArray(0);
        glUseProgram(0);
    }

 */
}
