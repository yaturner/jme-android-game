package com.example.jmegame;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.util.Random;

public class CubeGame extends SimpleApplication {

    private Geometry cube;
    private Geometry orbitCube;
    private Node     orbitPivot;
    private int score = 0;
    private BitmapText scoreText;

    // Drag state
    private boolean isDragging = false;
    private float lastX, lastY;
    private float totalDragDistance = 0f;

    // Inertia: carry over drag velocity after finger lifts
    private float velocityX = 0f;
    private float velocityY = 0f;

    private static final float DRAG_SENSITIVITY = 0.008f;
    private static final float TAP_THRESHOLD    = 15f;   // pixels
    private static final float INERTIA_DECAY    = 0.92f;

    private final RawInputListener touchListener = new RawInputListener() {

        @Override
        public void onTouchEvent(TouchEvent evt) {
            switch (evt.getType()) {
                case DOWN:
                    isDragging = true;
                    lastX = evt.getX();
                    lastY = evt.getY();
                    totalDragDistance = 0f;
                    // Kill inertia when finger touches down
                    velocityX = 0f;
                    velocityY = 0f;
                    break;

                case MOVE:
                    if (isDragging) {
                        float dx = evt.getX() - lastX;
                        float dy = evt.getY() - lastY;

                        rotateCube(dx, dy);

                        // Store as inertia velocity (pixels/frame, decayed on lift)
                        velocityX = dx;
                        velocityY = dy;

                        totalDragDistance += FastMath.sqrt(dx * dx + dy * dy);
                        lastX = evt.getX();
                        lastY = evt.getY();
                    }
                    break;

                case UP:
                    isDragging = false;
                    // Small movement = tap: award score and change color
                    if (totalDragDistance < TAP_THRESHOLD) {
                        score += 10;
                        cycleCubeColor();
                    }
                    break;

                default:
                    break;
            }
            evt.setConsumed();
        }

        // Required no-ops for non-touch events
        @Override public void beginInput() {}
        @Override public void endInput() {}
        @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
        @Override public void onMouseButtonEvent(MouseButtonEvent evt) {}
        @Override public void onKeyEvent(KeyInputEvent evt) {}
        @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    };

    @Override
    public void simpleInitApp() {
        cam.setLocation(new Vector3f(0, 0, 6));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        flyCam.setEnabled(false);
        viewPort.setBackgroundColor(ColorRGBA.Black);

        // Starfield — 250 tiny boxes on a sphere shell, batched to one draw call
        BatchNode starBatch = new BatchNode("Stars");
        Material starMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        starMat.setColor("Color", ColorRGBA.White);
        Box starMesh = new Box(0.06f, 0.06f, 0.06f);
        Random rand = new Random(12345L); // fixed seed for deterministic layout

        for (int i = 0; i < 250; i++) {
            // Uniform distribution on sphere surface
            float theta = rand.nextFloat() * FastMath.TWO_PI;
            float phi   = FastMath.acos(2f * rand.nextFloat() - 1f);
            float r     = 25f + rand.nextFloat() * 15f; // shell between radius 25 and 40

            Geometry star = new Geometry("s" + i, starMesh);
            star.setMaterial(starMat);
            star.setLocalTranslation(
                r * FastMath.sin(phi) * FastMath.cos(theta),
                r * FastMath.sin(phi) * FastMath.sin(theta),
                r * FastMath.cos(phi));
            star.setLocalScale(0.5f + rand.nextFloat()); // vary star size
            starBatch.attachChild(star);
        }
        starBatch.batch(); // merges all children into a single mesh per material
        rootNode.attachChild(starBatch);

        // Cube
        Box box = new Box(1f, 1f, 1f);
        cube = new Geometry("Cube", box);
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient",  new ColorRGBA(0.2f, 0.4f, 0.8f, 1f));
        mat.setColor("Diffuse",  new ColorRGBA(0.2f, 0.4f, 0.8f, 1f));
        mat.setColor("Specular", ColorRGBA.White);
        mat.setFloat("Shininess", 64f);
        cube.setMaterial(mat);
        rootNode.attachChild(cube);

        // Orbit pivot — sits at world origin; rotating it sweeps orbitCube in a circle
        orbitPivot = new Node("OrbitPivot");
        // Tilt the orbit plane 20° around X so it reads as 3-D from the camera
        orbitPivot.rotate(FastMath.DEG_TO_RAD * 20f, 0, 0);
        rootNode.attachChild(orbitPivot);

        // orbitNode carries both the cube geometry and the trail emitter at the orbit radius.
        // Geometry is a scene-graph leaf and cannot have children, so a Node wrapper is required.
        Node orbitNode = new Node("OrbitNode");
        orbitNode.setLocalTranslation(2.5f, 0, 0);
        orbitPivot.attachChild(orbitNode);

        Box orbitBox = new Box(0.4f, 0.4f, 0.4f);
        orbitCube = new Geometry("OrbitCube", orbitBox);
        Material orbitMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        orbitMat.setBoolean("UseMaterialColors", true);
        orbitMat.setColor("Ambient",  new ColorRGBA(0.9f, 0.4f, 0.1f, 1f));
        orbitMat.setColor("Diffuse",  new ColorRGBA(0.9f, 0.4f, 0.1f, 1f));
        orbitMat.setColor("Specular", ColorRGBA.White);
        orbitMat.setFloat("Shininess", 32f);
        orbitMat.setColor("GlowColor", new ColorRGBA(1f, 0.5f, 0.05f, 1f));
        orbitCube.setMaterial(orbitMat);
        orbitNode.attachChild(orbitCube);

        // Trail — particles emitted at the cube's world position, then left behind as it moves
        ParticleEmitter trail = new ParticleEmitter("OrbitTrail", ParticleMesh.Type.Triangle, 150);
        Material trailMat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        trailMat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/Debris.png"));
        trail.setMaterial(trailMat);
        trail.setImagesX(3);
        trail.setImagesY(3);
        trail.setStartColor(new ColorRGBA(1f, 0.75f, 0.1f, 1f));
        trail.setEndColor(  new ColorRGBA(0.8f, 0.1f, 0f,  0f));
        trail.setStartSize(0.25f);
        trail.setEndSize(0.02f);
        trail.setGravity(0f, 0f, 0f);
        trail.setLowLife(0.4f);
        trail.setHighLife(0.65f);
        trail.setParticlesPerSec(120);
        trail.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0, 0));
        trail.getParticleInfluencer().setVelocityVariation(0.05f);
        orbitNode.attachChild(trail);

        // Lights
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.25f, 0.25f, 0.35f, 1f)); // cool blue-white fill
        rootNode.addLight(ambient);

        DirectionalLight sun = new DirectionalLight();
        sun.setColor(ColorRGBA.White.mult(1.5f));
        sun.setDirection(new Vector3f(-1, -1, -2).normalizeLocal());
        rootNode.addLight(sun);

        // Bloom — GlowMode.Objects only blooms geometry that has GlowColor set,
        // so the main cube and background are unaffected
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setBloomIntensity(3f);
        bloom.setBlurScale(1.8f);
        bloom.setDownSamplingFactor(2f);  // halve the FBO resolution for mobile perf
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        fpp.addFilter(bloom);
        viewPort.addProcessor(fpp);

        // HUD
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        scoreText = new BitmapText(guiFont, false);
        scoreText.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        scoreText.setColor(ColorRGBA.White);
        scoreText.setLocalTranslation(10, cam.getHeight() - 10, 0);
        guiNode.attachChild(scoreText);

        inputManager.addRawInputListener(touchListener);
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (isDragging) {
            // Finger is down — cube is driven entirely by touch events
        } else if (velocityX != 0f || velocityY != 0f) {
            // Apply inertia after finger lifts
            rotateCube(velocityX, velocityY);
            velocityX *= INERTIA_DECAY;
            velocityY *= INERTIA_DECAY;
            if (FastMath.abs(velocityX) < 0.01f) velocityX = 0f;
            if (FastMath.abs(velocityY) < 0.01f) velocityY = 0f;
        } else {
            // Idle auto-spin when untouched
            cube.rotate(tpf * 0.5f, tpf * 0.35f, 0);
        }

        // Orbit pivot rotates around its local Y — the 20° tilt makes the path look 3-D
        orbitPivot.rotate(0, tpf * 1.2f, 0);
        // Orbit cube spins on its own axes independently of the orbit motion
        orbitCube.rotate(tpf * 2.5f, tpf * 1.8f, 0);

        scoreText.setText("Score: " + score + "  |  Drag to rotate  |  Tap for points");
    }

    // dx/dy are screen-pixel deltas; horizontal drag rotates around Y, vertical around X
    private void rotateCube(float dx, float dy) {
        Quaternion rotY = new Quaternion().fromAngleAxis( dx * DRAG_SENSITIVITY, Vector3f.UNIT_Y);
        Quaternion rotX = new Quaternion().fromAngleAxis(-dy * DRAG_SENSITIVITY, Vector3f.UNIT_X);
        cube.setLocalRotation(rotY.mult(rotX).mult(cube.getLocalRotation()));
    }

    private void cycleCubeColor() {
        float hue = (score * 0.05f) % 1f;
        cube.getMaterial().setColor("Diffuse", new ColorRGBA(
                FastMath.sin(hue * FastMath.TWO_PI)         * 0.5f + 0.5f,
                FastMath.sin(hue * FastMath.TWO_PI + 2.09f) * 0.5f + 0.5f,
                FastMath.sin(hue * FastMath.TWO_PI + 4.19f) * 0.5f + 0.5f,
                1f));
    }
}
