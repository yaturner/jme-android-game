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
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import java.util.Random;

public class CubeGame extends SimpleApplication {

    private Geometry cube;
    private Node     enterpriseNode;
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
        setDisplayFps(false);
        setDisplayStatView(false);
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

        // ── NCC-1701 Enterprise (procedural) ─────────────────────────────────
        // All cylinders default to the Y axis; rotate HALF_PI around X to align along Z.
        enterpriseNode = new Node("Enterprise");
        orbitNode.attachChild(enterpriseNode);

        // Hull material — metallic light gray
        Material hullMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        hullMat.setBoolean("UseMaterialColors", true);
        hullMat.setColor("Ambient",  new ColorRGBA(0.55f, 0.55f, 0.60f, 1f));
        hullMat.setColor("Diffuse",  new ColorRGBA(0.70f, 0.70f, 0.75f, 1f));
        hullMat.setColor("Specular", ColorRGBA.White);
        hullMat.setFloat("Shininess", 48f);

        // Warp / deflector material — glowing blue (picked up by BloomFilter)
        Material warpMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        warpMat.setBoolean("UseMaterialColors", true);
        warpMat.setColor("Ambient",  new ColorRGBA(0.3f, 0.5f, 1.0f, 1f));
        warpMat.setColor("Diffuse",  new ColorRGBA(0.2f, 0.4f, 0.9f, 1f));
        warpMat.setColor("Specular", ColorRGBA.White);
        warpMat.setFloat("Shininess", 16f);
        warpMat.setColor("GlowColor", new ColorRGBA(0.4f, 0.6f, 1.0f, 1f));

        // Saucer section — flat disk in the XY plane, slightly forward
        Geometry saucer = new Geometry("Saucer", new Cylinder(8, 32, 0.40f, 0.06f, true));
        saucer.setMaterial(hullMat);
        saucer.setLocalTranslation(0, 0.06f, 0.22f);
        saucer.rotate(FastMath.HALF_PI, 0, 0);
        enterpriseNode.attachChild(saucer);

        // Bridge dome on top of saucer center
        Geometry bridge = new Geometry("Bridge", new Sphere(8, 16, 0.055f));
        bridge.setMaterial(hullMat);
        bridge.setLocalTranslation(0, 0.12f, 0.22f);
        enterpriseNode.attachChild(bridge);

        // Neck connecting saucer to secondary hull, rotated to match nacelle orientation
        Geometry neck = new Geometry("Neck", new Box(0.055f, 0.075f, 0.055f));
        neck.setMaterial(hullMat);
        neck.setLocalTranslation(0, -0.01f, 0.04f);
        neck.rotate(0, 0, FastMath.HALF_PI);
        enterpriseNode.attachChild(neck);

        // Secondary hull — elongated cylinder behind and below the saucer
        Geometry secHull = new Geometry("SecHull", new Cylinder(8, 16, 0.13f, 0.60f, true));
        secHull.setMaterial(hullMat);
        secHull.setLocalTranslation(0, -0.12f, -0.08f);
        secHull.rotate(FastMath.HALF_PI, 0, 0);
        enterpriseNode.attachChild(secHull);

        // Deflector dish — glowing blue sphere at front of secondary hull
        Geometry deflector = new Geometry("Deflector", new Sphere(8, 16, 0.09f));
        deflector.setMaterial(warpMat);
        deflector.setLocalTranslation(0, -0.12f, 0.22f);
        enterpriseNode.attachChild(deflector);

        // Pylons — angled struts connecting secondary hull to nacelles
        Box pylonBox = new Box(0.035f, 0.10f, 0.035f);
        Geometry portPylon = new Geometry("PortPylon", pylonBox);
        portPylon.setMaterial(hullMat);
        portPylon.setLocalTranslation(-0.22f, -0.09f, -0.06f);
        portPylon.rotate(0, 0, FastMath.DEG_TO_RAD * 25f);
        enterpriseNode.attachChild(portPylon);

        Geometry starPylon = new Geometry("StarPylon", pylonBox);
        starPylon.setMaterial(hullMat);
        starPylon.setLocalTranslation(0.22f, -0.09f, -0.06f);
        starPylon.rotate(0, 0, FastMath.DEG_TO_RAD * -25f);
        enterpriseNode.attachChild(starPylon);

        // Nacelles — long cylinders flanking the secondary hull
        Cylinder nacelleCyl = new Cylinder(8, 16, 0.055f, 0.65f, true);
        // Nacelles rotated around Z so their long axis runs along X, parallel to the saucer plane
        Geometry portNacelle = new Geometry("PortNacelle", nacelleCyl);
        portNacelle.setMaterial(hullMat);
        portNacelle.setLocalTranslation(-0.38f, -0.13f, -0.04f);
        portNacelle.rotate(0, 0, FastMath.HALF_PI);
        enterpriseNode.attachChild(portNacelle);

        Geometry starNacelle = new Geometry("StarNacelle", nacelleCyl);
        starNacelle.setMaterial(hullMat);
        starNacelle.setLocalTranslation(0.38f, -0.13f, -0.04f);
        starNacelle.rotate(0, 0, FastMath.HALF_PI);
        enterpriseNode.attachChild(starNacelle);

        // Warp caps at the outer ends of each nacelle (nacelle half-length = 0.325)
        Sphere capSphere = new Sphere(8, 16, 0.062f);
        Geometry portCap = new Geometry("PortCap", capSphere);
        portCap.setMaterial(warpMat);
        portCap.setLocalTranslation(-0.705f, -0.13f, -0.04f);
        enterpriseNode.attachChild(portCap);

        Geometry starCap = new Geometry("StarCap", capSphere);
        starCap.setMaterial(warpMat);
        starCap.setLocalTranslation(0.705f, -0.13f, -0.04f);
        enterpriseNode.attachChild(starCap);

        // Trail — particles emitted at the cube's world position, then left behind as it moves
        ParticleEmitter trail = new ParticleEmitter("OrbitTrail", ParticleMesh.Type.Triangle, 150);
        Material trailMat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        trailMat.setTexture("Texture", assetManager.loadTexture("Textures/Particle.png"));
        trail.setMaterial(trailMat);
        trail.setImagesX(1);
        trail.setImagesY(1);
        trail.setStartColor(new ColorRGBA(0.7f, 0.85f, 1.0f, 1f));  // pale blue-white
        trail.setEndColor(  new ColorRGBA(0.1f, 0.2f,  0.8f, 0f));  // deep blue, transparent
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
        bloom.setBloomIntensity(1.5f);
        bloom.setBlurScale(1.2f);
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
        // Enterprise rotates slowly so all angles are visible during the orbit
        enterpriseNode.rotate(0, tpf * 0.6f, 0);

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
