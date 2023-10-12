package com.mygdx.rocket.screens;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.mygdx.rocket.Rocket;
import com.mygdx.rocket.RocketFlight;
import com.mygdx.rocket.Saves;
import com.mygdx.rocket.build.Engine;
import com.mygdx.rocket.build.FuelTank;
import com.mygdx.rocket.build.Part;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class SimulationScreen implements Screen, InputProcessor {
    public static boolean simscreen = false;

    private final RocketFlight app;
    private OrthographicCamera camera;
    private Stage stage;
    private Skin skin;
    public BitmapFont font48;
    public BitmapFont font24W;
    public BitmapFont font16W;
    private ShapeRenderer shapeRenderer;

    private Vector2 earthCenter;
    private float earthRadius;
    private Rocket currentRocket;
    private boolean loadDone = false;

    private static final float GRAVITY = -9.8f;
    private Vector2 rocketVelocity = new Vector2(0, 0);
    private boolean earthCollision = false;
    private boolean isPaused = false;
    private Dialog dialog;
    private Slider gasSlider;
    public static Slider zoomSlider;
    public float gasFlow = 0;
    public static CheckBox gasSwitch;
    private boolean gasOn = false;

    private long lastToggleTime = 0;
    private static final long TOGGLE_DELAY = 500;
    private long messageStartTime = 0;
    private static final long MESSAGE_DISPLAY_DURATION = 1200;
    private String currentMessage = null;

    private Array<SmokeParticle> smoke;
    float gimbalAdjustSpeed = 1.0f;
    float gimbalLerpFactor = 0.5f;

    private float messageDuration = 0;
    private final float MESSAGE_TIME = 2.0f;
    private String message = "";



    public SimulationScreen(final RocketFlight app) {
        this.app = app;
        camera = new OrthographicCamera(RocketFlight.V_WIDTH, RocketFlight.V_HEIGHT);
        camera.setToOrtho(false);

        this.stage = new Stage(new ScalingViewport(Scaling.stretch, RocketFlight.V_WIDTH, RocketFlight.V_HEIGHT, app.camera));
        Gdx.input.setInputProcessor(stage);

        shapeRenderer = new ShapeRenderer();

        // Initialize Earth parameters
        earthRadius = 7000000;
        earthCenter = new Vector2(RocketFlight.V_WIDTH / 2, -earthRadius + 150); // 150 can be adjusted

        initResources();

        showLoadDialog();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void initResources() {
        // Initializing skin
        skin = new Skin(Gdx.files.internal("skin/clean-crispy-ui.json"));

        // Initializing fonts
        initFonts();
    }

    private void initFonts() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Iceland-Regular.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter params = new FreeTypeFontGenerator.FreeTypeFontParameter();

        params.size = 48;
        params.color = Color.BLACK;
        params.borderWidth = 1;
        params.borderColor = Color.BLACK;

        font48 = generator.generateFont(params);

        params.color = Color.WHITE;
        params.size = 24;

        font24W = generator.generateFont(params);

        params.size = 20;

        font16W = generator.generateFont(params);

        generator.dispose();
    }

    @Override
    public void show() {
        // Initialization when the screen becomes visible can be done here.
        Gdx.input.setInputProcessor(this);
        simscreen = true;

        // smoke effect
        smoke = new Array<>();

        // pause button
        TextButton pauseButton = new TextButton("Pause", skin);
        pauseButton.setSize(100, 50);
        pauseButton.setPosition(25, RocketFlight.V_HEIGHT-75);
        // clicklistener
        pauseButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Handle button click here
                togglePause();
                Gdx.app.log("Simulation paused", "Clicked");
            }
        });

        // gas flow rate slider
        gasSlider = new Slider(0, 100, 1, true, skin);
        gasSlider.setSize(30, 300);
        gasSlider.setPosition(RocketFlight.V_WIDTH - 100, 200);
        gasSlider.setValue(0);

        // zoom slider
        zoomSlider = new Slider(1, 100000, 1, false, skin);
        zoomSlider.setSize(1200, 30);
        zoomSlider.setPosition(RocketFlight.V_WIDTH/2 - 600, 70);
        zoomSlider.setValue(1);
        zoomSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float zoomValue = zoomSlider.getValue();
                camera.zoom = zoomValue;
                camera.update();
            }
        });

        // gas on/off switch
        gasSwitch = new CheckBox("Gas ON/OFF", skin);
        gasSwitch.setChecked(false);
        gasSwitch.setScale(3.0f);
        gasSwitch.setPosition(RocketFlight.V_WIDTH - 120, 500);
        // switch listener
        gasSwitch.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (gasSwitch.isChecked()) {
                    gasOn = true;
                } else {
                    gasOn = false;
                }
            }
        });

        // add actors to stage
        stage.addActor(pauseButton);
        stage.addActor(gasSlider);
        stage.addActor(zoomSlider);
        stage.addActor(gasSwitch);
    }

    @Override
    public void render(float delta) {
        // clear color to sky blue
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // update the camera to follow the rocket
        if (currentRocket != null) {
            Vector2 rocketCenter = currentRocket.getCenter();
            camera.position.set(rocketCenter.x, rocketCenter.y, 0);
            camera.update();
        }

        // render things specific to the SimulationScreen
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);


        shapeRenderer.setColor(Color.SKY);
        shapeRenderer.circle(earthCenter.x, earthCenter.y, earthRadius + 1000000);
        shapeRenderer.setColor(Color.FOREST);
        shapeRenderer.circle(earthCenter.x, earthCenter.y, earthRadius);

        shapeRenderer.end();

        // move the smoke
        for(int i = 0; i < smoke.size; i++) {
            smoke.get(i).circle.x += smoke.get(i).direction.scl(1).x;
        }
        // make smoke disappear
        Iterator<SmokeParticle> iterator = smoke.iterator();
        while (iterator.hasNext()) {
            SmokeParticle smok = iterator.next();
            smok.update(delta);

            if (smok.isOld()) {
                iterator.remove();
            }
        }

        // draw current rocket
        if (currentRocket != null) {
            currentRocket.draw(shapeRenderer, app.batch, stage);
        }

        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();

        if (!isPaused) {
            // update message time
            if (messageDuration > 0) {
                messageDuration -= delta; // Subtracting the time passed since the last frame
                if (messageDuration < 0) {
                    messageDuration = 0; // Reset it to 0 so it doesn't go negative
                    message = " "; // Clear the message
                }
            }

            if (currentRocket != null) {
                // calculate gravitational force
                Vector2 gravityDirection = earthCenter.cpy().sub(currentRocket.getCenter());
                float distance = gravityDirection.len();
                gravityDirection.nor().scl(-1);

                // checking if left earth's atmosphere
                if(calculateRocketHeight(currentRocket.getCenter()) < 25 * 1000 * 40) {
                    // update rocket velocity
                    rocketVelocity.add(gravityDirection.scl(GRAVITY * delta));
                } else {
                    if (message.isEmpty()) { // condition ensures message is only set once
                        message = "You have left the Earth's atmosphere!";
                        messageDuration = MESSAGE_TIME;
                    }
                }

                Vector2 temp = new Vector2(gravityDirection.cpy());
//                System.out.println("Before thrust : " + rocketVelocity);
                // look for collision
                for (Part part : currentRocket.getParts()) {
                    Rectangle partBounds = part.getBounds();
                    if (isRectangleIntersectingCircle(partBounds, earthCenter, earthRadius)) {
                        earthCollision = true;
                        break;
                    }
                    earthCollision = false;
                }

                if (earthCollision) {
                    rocketVelocity.set(0,0);
                }

                // change values of rocket
                if(gasSwitch.isChecked()) {
                    float fuelUsed = currentRocket.getFuelConsumptionRate() * (gasFlow / 100) * delta;
                 System.out.println("Fuel Used: " + fuelUsed);
                    int numParts = currentRocket.numParts("fuelTank");
                    System.out.println(numParts);
                    for (Part part : currentRocket.getParts()) {
                        if (part.getType().equals("fuelTank")) {
                            FuelTank fuelTank = (FuelTank) part;
                            float newFuel = fuelTank.getCurrentFuel() - (fuelUsed / numParts);
                            System.out.println(newFuel);
                            if(newFuel > 0) {
                                fuelTank.setCurrentFuel(newFuel);
                            } else {
                                fuelTank.setCurrentFuel(0);
                                gasFlow = 0;
                                app.batch.begin();
                                font24W.draw(app.batch, "Warning: Rocket is out of fuel!", RocketFlight.V_WIDTH/2, RocketFlight.V_HEIGHT/2);
                                app.batch.end();
                            }

                        }
                    }
                }

                // changing velocity
                // acceleration calculation (F = ma) --> a = F/m
                float acceleration = (currentRocket.getThrust() * gasFlow / 100) / (currentRocket.getMass());
                //if (acceleration < 0) {acceleration = 0;}
                if(gasSwitch.isChecked()) {
                    Vector2 thrustDirection = currentRocket.getThrustDirection();
                    rocketVelocity.add(thrustDirection.cpy().scl(acceleration * 10.8f * delta));
     //               System.out.println("After thrust : " + rocketVelocity);
                    spawnSmoke((int) gasFlow);
                    // Draw the smoke particles with color changing over time
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    for (SmokeParticle smok : smoke) {
                        // Calculate the lerp value based on the age of the smoke
                        float progress = smok.age / smok.maxAge;
                        Color smokeColor = Color.YELLOW.cpy().lerp(Color.GRAY, progress);

                        shapeRenderer.setColor(smokeColor);
                        shapeRenderer.circle(smok.circle.x, smok.circle.y, smok.circle.radius);
                    }
                    shapeRenderer.end();
                }
                // move rocket
                currentRocket.move(rocketVelocity.cpy());
                moveSmoke(rocketVelocity.cpy());
            }
        }

        // get control values
        app.batch.begin();
        gasFlow = gasSlider.getValue();
        font24W.draw(app.batch, "Gas Flow: " + gasFlow + "%", RocketFlight.V_WIDTH - 160, 200);

        // display stats
        if(currentRocket != null) {
            font16W.draw(app.batch, "Current Mass: " + currentRocket.getMass() + " Tons", 25, RocketFlight.V_HEIGHT - 100);
            font16W.draw(app.batch, "Remaining Fuel: " + currentRocket.getFuel() + "%", 25, RocketFlight.V_HEIGHT - 125);
            font16W.draw(app.batch, "Thrust Utilization: " + currentRocket.getThrustPercentage() * gasFlow / 100 + "%", 25, RocketFlight.V_HEIGHT - 150);
            font16W.draw(app.batch, "Current Thrust: " + currentRocket.getThrust() * gasFlow / 100 + " Tons", 25, RocketFlight.V_HEIGHT - 175);
            font16W.draw(app.batch, "Current Altitude: " + calculateRocketHeight(currentRocket.getCenter()) / 40 / 1000f +  " km", RocketFlight.V_WIDTH-300, RocketFlight.V_HEIGHT - 175);
            font16W.draw(app.batch, "Current Velocity: " + rocketVelocity.cpy().len() +  " km", RocketFlight.V_WIDTH-300, RocketFlight.V_HEIGHT - 150);
            System.out.println("Before Update: " + currentRocket.getCenter() + ", " + currentRocket.getCenter());
            currentRocket.update(delta);
            System.out.println("After Update: " + currentRocket.getCenter() + ", " + currentRocket.getCenter());
            if (!message.isEmpty()) {
                font24W.draw(app.batch, message, RocketFlight.V_WIDTH / 2, RocketFlight.V_HEIGHT / 2);
            }
        }
        app.batch.end();

        if(Gdx.input.isTouched()) {
            System.out.println("clicked");

            // Check if enough time has passed since the last toggle
            if(System.currentTimeMillis() - lastToggleTime < TOGGLE_DELAY) {
                return; // exit early if not enough time has passed
            }

            float x = Gdx.input.getX();
            float y = Gdx.input.getY();
            // Convert screen coordinates to world coordinates if you're using a camera.
            Vector3 touchPos = new Vector3(x, y, 0);
            camera.unproject(touchPos);

            if(currentRocket != null) {
                for (Part part : currentRocket.getParts()) {
                    if (part.getBounds().contains(touchPos.x, touchPos.y)) {
                        // The part of the rocket was clicked.
                        System.out.println("clicked " + part.getName());
                        if (part.getType().equals("engine")) {
                            Engine engine = (Engine) part;
                            engine.toggleState();

                            // Update the last toggle time
                            lastToggleTime = System.currentTimeMillis();
                            // Update the message start time
                            messageStartTime = System.currentTimeMillis();

                            if(engine.getState()) {
                                currentMessage = "Engine On";
                            } else {
                                currentMessage = "Engine Off";
                            }
                        }
                    }
                }
            }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            adjustAllEnginesGimbal(gimbalAdjustSpeed);
        } else if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            adjustAllEnginesGimbal(-gimbalAdjustSpeed);
        } else {
            // No keys pressed, move gimbal back to neutral
            returnGimbalToNeutral(gimbalLerpFactor);
        }

        long elapsed = System.currentTimeMillis() - messageStartTime;
        if(elapsed < MESSAGE_DISPLAY_DURATION && currentMessage != null) {
            app.batch.begin();
            font24W.draw(app.batch, currentMessage, RocketFlight.V_WIDTH/2, RocketFlight.V_HEIGHT/2);
            app.batch.end();
        } else {
            currentMessage = null; // Reset the message after the duration
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
        // Handle pause if necessary
    }

    @Override
    public void resume() {
        // Handle resume if necessary
    }

    @Override
    public void hide() {
        // Handle when the screen becomes hidden
    }

    @Override
    public void dispose() {
        skin.dispose();
        font48.dispose();
        font24W.dispose();
        stage.dispose();
    }

    public void showLoadDialog() {
        // Create the dialog
        final Dialog dialog = new Dialog("Load Rocket for Simulation", skin);

        // Fetching the list of all text files from the "saves" folder
        FileHandle dirHandle = Gdx.files.internal("saves");
        Array<String> options = new Array<>();
        for (FileHandle entry: dirHandle.list()) {
            if (entry.extension().equals("txt")) {
                options.add(entry.nameWithoutExtension());
            }
        }

        // Create the SelectBox
        final SelectBox<String> selectBox = new SelectBox<>(skin);
        selectBox.setItems(options);

        // Add the SelectBox to the dialog
        dialog.text("Select a rocket to load into the simulation:");
        dialog.getContentTable().row();
        dialog.getContentTable().add(selectBox);

        // Add "Load" button
        TextButton loadButton = new TextButton("Load", skin);
        dialog.button(loadButton);

        // Add "Cancel" button
        TextButton cancelButton = new TextButton("Cancel", skin);
        dialog.button(cancelButton);

        // Show the dialog
        dialog.show(stage);

        // Add listeners to buttons
        loadButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String selectedRocket = selectBox.getSelected();
                // Load the selected rocket into the simulation
                try {
                    File file = new File("saves/" + selectedRocket + ".txt");
                    currentRocket = Saves.decode(file);
                    currentRocket.setMomentOfInertia((1/12) * currentRocket.getMass() * 400*400);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Gdx.app.log("Load for Simulation", "Rocket " + selectedRocket + " is loaded into simulation");
                loadDone = true;
                dialog.hide();
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                app.setScreen(new MainMenu(app));
                dialog.hide();
            }
        });
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.P) {
            togglePause();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return stage.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return stage.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        // how fast you zoom in and out
        float zoomAmount = 0.1f;
        camera.zoom += amountY * zoomAmount;

        // limits to the zoom (limiting between 0.5 (2x zoom in) and 2 (2x zoom out))
        camera.zoom = MathUtils.clamp(camera.zoom, 0.5f, 100f);

        camera.update();
        return true; // input was handled
    }

    public boolean isRectangleIntersectingCircle(Rectangle rect, Vector2 circleCenter, float circleRadius) {
        float closestX = MathUtils.clamp(circleCenter.x, rect.x, rect.x + rect.width);
        float closestY = MathUtils.clamp(circleCenter.y, rect.y, rect.y + rect.height);

        float distanceX = circleCenter.x - closestX;
        float distanceY = circleCenter.y - closestY;

        float distanceSquared = distanceX * distanceX + distanceY * distanceY;

        return distanceSquared <= circleRadius * circleRadius;
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            showPauseDialog();
        } else {
            // You can hide the pause dialog here if you want to.
            hidePauseDialog();
        }
    }

    public void showPauseDialog() {
        dialog = new Dialog("Paused", skin);

        // Add buttons or settings options
        TextButton resumeButton = new TextButton("Resume", skin);
        TextButton settingsButton = new TextButton("Settings", skin);
        TextButton quitButton = new TextButton("Quit", skin);

        dialog.button(resumeButton);
        dialog.button(settingsButton);
        dialog.button(quitButton);

        // Add listeners to buttons
        resumeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                togglePause();  // unpause the game and hide the dialog
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Open settings menu or overlay here
            }
        });

        quitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                app.setScreen(new MainMenu(app));
                dispose();
            }
        });

        dialog.show(stage);
    }

    public void hidePauseDialog() {
        if (dialog != null) {
            dialog.hide();
        }
    }

    public void spawnSmoke(int percentage) {
        for(int i = 0; i < percentage / 2; i++) {
            for(Part part : currentRocket.getParts()) {
                if(part.getType().equals("engine")) {
                    Engine engine = (Engine) part;
                    if(engine.getState()) {
                        Vector2 direction = new Vector2(MathUtils.random(-0.5f, 0.5f), MathUtils.random(-1f, 0f));
                        SmokeParticle smok;
                        if(engine.getRotation() == 1 || engine.getRotation() == 3) {
                            smok = new SmokeParticle(part.getCosmeticLocation().x + part.getSize().x * 20, part.getCosmeticLocation().y, MathUtils.random(10, percentage / 5 + 20), direction);

                        } else {
                            smok = new SmokeParticle(part.getCosmeticLocation().x, part.getCosmeticLocation().y + part.getSize().x * 20, MathUtils.random(10, percentage / 5 + 20), direction);
                        }
                        smoke.add(smok);
                    }
                }
            }
        }
    }

    public void moveSmoke(Vector2 velocity) {
        for(SmokeParticle smok : smoke) {
            smok.circle.y -= velocity.y;
        }
    }

    public float calculateRocketHeight(Vector2 rocketPosition) {
        // Calculate distance from rocket to Earth's center
        float distanceToCenter = rocketPosition.dst(earthCenter);

        // Subtract Earth's radius to get height above the surface
        float heightAboveSurface = distanceToCenter - earthRadius;

        return heightAboveSurface;
    }

    public void adjustAllEnginesGimbal(float deltaAngle) {
        for (Part part : currentRocket.getParts()) {
            if (part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                engine.adjustGimbal(deltaAngle);
            }
        }
    }

    public void returnGimbalToNeutral(float lerpFactor) {
        if (currentRocket != null) {
            for (Part part : currentRocket.getParts()) {
                if (part.getType().equals("engine")) {
                    Engine engine = (Engine) part;
                    float currentAngle = engine.getGimbal();
                    float newAngle = currentAngle * (1 - lerpFactor);
                    engine.setGimbal(newAngle);
                }
            }
        }
    }
}
