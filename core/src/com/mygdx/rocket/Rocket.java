package com.mygdx.rocket;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.mygdx.rocket.build.AttachmentPoint;
import com.mygdx.rocket.build.Engine;
import com.mygdx.rocket.build.FuelTank;
import com.mygdx.rocket.build.Part;
import com.mygdx.rocket.screens.SimulationScreen;

public class Rocket {

    private String name;
    private Array<Part> parts;

    public Color centerColor = new Color(1, 0.8f, 0, 1); // colors for engine ignition
    public Color edgeColor = new Color(1, 0.8f, 0, 0.1f);

    private Vector2 triangle = new Vector2(Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight()/2);
    private float tilt = 0; // in degrees
    float angularVelocity = 0;
    float momentOfInertia;

    public Rocket(String name) {
        this.name = name;
        parts = new Array<Part>();
    }

    public void drawPoints(ShapeRenderer shapeRenderer) {
        for(Part part : parts) {
            part.drawPoints(shapeRenderer);
        }
    }

    public void update(float deltaTime) {
        float totalGimbalAngle = 0;

        for (Part part : parts) {
            if(part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                totalGimbalAngle += engine.getGimbal();
            }
        }

        // Directly use gimbal angle to adjust tilt
        float tiltAdjustmentFactor = 2.0f;  // Adjust this factor as needed
        tilt += totalGimbalAngle * tiltAdjustmentFactor * deltaTime;

        // Dampening effect for tilt (if needed)
        //tilt *= 0.99;
    }


    public void draw(ShapeRenderer shapeRenderer, Batch batch, Stage stage) {
        Vector2 centerOfMass = calculateCenterOfMass();
        shapeRenderer.translate(centerOfMass.x, centerOfMass.y, 0);
        shapeRenderer.rotate(0, 0, 1, tilt); // theta is the accumulated tilt angle
        shapeRenderer.translate(-centerOfMass.x, -centerOfMass.y, 0);
        for (Part selectedPart : parts) {
            if (selectedPart.getType().equals("fuelTank")) {
                // set up for drawing filled shapes
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

                // draw a white rectangle
                shapeRenderer.setColor(Color.WHITE);
                shapeRenderer.rect(selectedPart.getLocation().x, selectedPart.getLocation().y,
                        selectedPart.getSize().x * 40, selectedPart.getSize().y * 40);

                shapeRenderer.end(); // end of drawing filled shapes

                // set up for drawing lines (i.e., the border)
                Gdx.gl.glLineWidth(5f);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

                // draw a gray border around the rectangle
                shapeRenderer.setColor(Color.GRAY);
                shapeRenderer.rect(selectedPart.getLocation().x, selectedPart.getLocation().y,
                        selectedPart.getSize().x * 40, selectedPart.getSize().y * 40);

                shapeRenderer.end();

            } else if (selectedPart.getType().equals("engine")) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                Engine engine = (Engine) selectedPart;
                float enginX = engine.getLocation().x + engine.getSize().x * 20;
                float enginY = engine.getLocation().y;
                Matrix4 originalMatrix = shapeRenderer.getTransformMatrix().cpy();
                Matrix4 transformationMatrix = shapeRenderer.getTransformMatrix().cpy();

                Vector2 attachmentPoint = engine.getAttachmentPoints().get(0).getLocation();
                transformationMatrix.translate(attachmentPoint.x, attachmentPoint.y, 0);
                transformationMatrix.rotate(0, 0, 1, engine.getGimbal());
                transformationMatrix.translate(-attachmentPoint.x, -attachmentPoint.y, 0);
                shapeRenderer.setTransformMatrix(transformationMatrix);

                if(engine.getState() && SimulationScreen.gasSwitch.isChecked()) {
                    float glowRadius = 40;  // Adjust as needed
                    float numberOfRings = 5;  // The number of concentric circles
                    float engineX;
                    float engineY;
                    if(engine.getRotation() == 1 || engine.getRotation() == 3) {
                        engineX = engine.getLocation().x + engine.getSize().x * 20;
                        engineY = engine.getLocation().y;
                    } else {
                        engineX = engine.getLocation().x;
                        engineY = engine.getLocation().y+ engine.getSize().x * 20;
                    }

                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

                    for (int i = 0; i < numberOfRings; i++) {
                        float progress = i / (numberOfRings - 1f);
                        Color currentColor = centerColor.cpy().lerp(edgeColor, progress);
                        float currentRadius = glowRadius * (1 - progress);

                        shapeRenderer.setColor(currentColor);
                        shapeRenderer.circle(engineX, engineY, currentRadius);
                    }

                    shapeRenderer.end();
                }
                float x = selectedPart.getLocation().x;
                float y = selectedPart.getLocation().y;
                float width = selectedPart.getSize().x * 40;
                float height = selectedPart.getSize().y * 40;

                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(Color.DARK_GRAY);

                switch (selectedPart.getRotation()) {
                    case 3: // Up
                        shapeRenderer.triangle(x, y, x + width, y, x + width / 2, y + height);
                        break;
                    case 2: // Right
                        shapeRenderer.triangle(x + height, y, x + height, y + width, x, y + width / 2);
                        break;
                    case 1: // Down
                        shapeRenderer.triangle(x, y + height, x + width, y + height, x + width / 2, y);
                        break;
                    case 4: // Left
                        shapeRenderer.triangle(x, y + width, x, y, x + height, y + width / 2);
                        break;
                }

                shapeRenderer.end();

                // Outline
                Gdx.gl.glLineWidth(5f);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.GRAY);

                switch (selectedPart.getRotation()) {
                    case 3: // Up
                        shapeRenderer.line(x, y, x + width, y);
                        shapeRenderer.line(x + width, y, x + width / 2, y + height);
                        shapeRenderer.line(x + width / 2, y + height, x, y);
                        break;
                    case 2: // Right
                        shapeRenderer.line(x + height, y, x + height, y + width);
                        shapeRenderer.line(x + height, y + width, x, y + width / 2);
                        shapeRenderer.line(x, y + width / 2, x + height, y);
                        break;
                    case 1: // Down
                        shapeRenderer.line(x, y + height, x + width, y + height);
                        shapeRenderer.line(x + width, y + height, x + width / 2, y);
                        shapeRenderer.line(x + width / 2, y, x, y + height);
                        break;
                    case 4: // Left
                        shapeRenderer.line(x, y + width, x, y);
                        shapeRenderer.line(x, y, x + height, y + width / 2);
                        shapeRenderer.line(x + height, y + width / 2, x, y + width);
                        break;
                }

                shapeRenderer.end();
                shapeRenderer.setTransformMatrix(originalMatrix);

            } else if(selectedPart.getType().equals("capsule")) {
                float x = selectedPart.getLocation().x;
                float y = selectedPart.getLocation().y;
                float width = selectedPart.getSize().x * 40;
                float height = selectedPart.getSize().y * 40;

                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(Color.DARK_GRAY);

                switch (selectedPart.getRotation()) {
                    case 1: // Up
                        shapeRenderer.triangle(x, y + height/2, x + width, y + height/2, x + width / 2, y + height);
                        shapeRenderer.rect(x, y, width, height/2);
                        break;
                    case 4: // Right
                        shapeRenderer.triangle(x + height/2, y, x + height/2, y + width, x, y + width / 2);
                        shapeRenderer.rect(x + height/2, y, height/2, width);
                        break;
                    case 3: // Down
                        shapeRenderer.triangle(x, y + height/2, x + width, y + height/2, x + width / 2, y);
                        shapeRenderer.rect(x, y, width, height/2);
                        break;
                    case 2: // Left
                        shapeRenderer.triangle(x + height/2, y + width, x + height/2, y, x + height, y + width / 2);
                        shapeRenderer.rect(x, y, height/2, width);
                        break;
                }

                shapeRenderer.end();

                // Outline
                Gdx.gl.glLineWidth(5f);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.GRAY);

                switch (selectedPart.getRotation()) {
                    case 1: // Up
                        // Draw Triangle Outline
                        shapeRenderer.line(x, y + height/2, x + width, y + height/2);
                        shapeRenderer.line(x + width, y + height/2, x + width / 2, y + height);
                        shapeRenderer.line(x + width / 2, y + height, x, y + height/2);
                        // Draw Rectangle Outline
                        shapeRenderer.rect(x, y, width, height/2);
                        break;
                    case 4: // Right
                        // Draw Triangle Outline
                        shapeRenderer.line(x + height/2, y, x + height/2, y + width);
                        shapeRenderer.line(x + height/2, y + width, x, y + width / 2);
                        shapeRenderer.line(x, y + width / 2, x + height/2, y);
                        // Draw Rectangle Outline
                        shapeRenderer.rect(x + height/2, y, height/2, width);
                        break;
                    case 3: // Down
                        // Draw Triangle Outline
                        shapeRenderer.line(x, y + height/2, x + width, y + height/2);
                        shapeRenderer.line(x + width, y + height/2, x + width / 2, y);
                        shapeRenderer.line(x + width / 2, y, x, y + height/2);
                        // Draw Rectangle Outline
                        shapeRenderer.rect(x, y, width, height/2);
                        break;
                    case 2: // Left
                        // Draw Triangle Outline
                        shapeRenderer.line(x + height/2, y + width, x + height/2, y);
                        shapeRenderer.line(x + height/2, y, x + height, y + width / 2);
                        shapeRenderer.line(x + height, y + width / 2, x + height/2, y + width);
                        // Draw Rectangle Outline
                        shapeRenderer.rect(x, y, height/2, width);
                        break;
                }
                shapeRenderer.end();
                shapeRenderer.identity();
            }
        }
        if(SimulationScreen.simscreen && SimulationScreen.zoomSlider.getValue() > 50) {
            if(triangle != null) {
                drawEquilateralTriangle(shapeRenderer, triangle.x, triangle.y, 10 * SimulationScreen.zoomSlider.getValue(), getThrustDirection());
            } else {
                triangle = new Vector2(Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight()/2);
            }
        }

//        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
//        shapeRenderer.setColor(Color.RED);
//        shapeRenderer.circle(centerOfMass.x, centerOfMass.y, 10);
//        shapeRenderer.end();
        for (Part part : parts) {
            Vector2 newLocation = rotatePointAroundCenter(part.getLocation(), centerOfMass, tilt);
            part.setCosmeticLocation(newLocation);
        }

    }


    private Vector2 rotatePoint(Vector2 point, Vector2 pivot, float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        // Translate point to origin
        point.sub(pivot);

        // Rotate
        float xNew = point.x * cos - point.y * sin;
        float yNew = point.x * sin + point.y * cos;

        // Translate back
        Vector2 newPoint = new Vector2(xNew + pivot.x, yNew + pivot.y);

        return newPoint;
    }

    private Vector2 translatePoint(Vector2 point, Vector2 translation) {
        return point.add(translation);
    }


    @Override
    public String toString() {
        String re = "";
        for(Part part : parts) {
            re += part.getLocation();
            re += "\n";
        }
        return re;
    }

    public void addPart(Part part) {
        parts.add(part);
    }

    public Array<Part> getParts() {
        return parts;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void updatePoints() {

        Array<AttachmentPoint> points = new Array<>();
        for (int i = 0; i < parts.size; i++) {
            for (AttachmentPoint point : parts.get(i).getAttachmentPoints()) {
                points.add(point);
                point.updateLocation(parts.get(i).getLocation(), parts.get(i).getRotation());
            }
        }

        for (AttachmentPoint point : points) {
            point.setOccupied(false);
        }

        //points.get(0).updateLocation(new Vector2(-100, -100));

//        if(points.size > 4)
//            points.get(4).updateLocation(parts.get(1).getLocation());

        // Use a small threshold for comparing float values
        final float EPSILON = 0.05f;

        for (int i = 0; i < points.size; i++) {
            for (int j = i + 1; j < points.size; j++) {
                if (i != j &&
                        Math.abs(points.get(i).getLocation().x - points.get(j).getLocation().x) < EPSILON &&
                        Math.abs(points.get(i).getLocation().y - points.get(j).getLocation().y) < EPSILON) {
//                    System.out.println("Overlapping Points: " + points.get(i).getLocation() + " and " + points.get(j).getLocation());
                    points.get(i).setOccupied(true);
                    points.get(j).setOccupied(true);
                }
            }
        }
    }


    public Array<AttachmentPoint> getAttachmentPoints() {
        Array<AttachmentPoint> re = new Array<AttachmentPoint>();
        for(Part part : parts) {
            for(AttachmentPoint point : part.getAttachmentPoints()) {
                re.add(point);
            }
        }
        return re;
    }

    public void move(Vector2 velocity) {
        for(Part part : parts) {
            part.move(velocity);
        }
        triangle.add(velocity);
    }

    public Vector2 getCenter() {
        Vector2 center = new Vector2(0, 0);

        // if no parts
        if (parts.size == 0) {
            return center;
        }

        for (Part part : parts) {
            center.add(part.getLocation());
        }
        // dividing by number of parts to get the average
        center.scl(1.0f / parts.size);
        return center;
    }

    public float getMass() {
        float mass = 0f;
        for (Part part : parts) {
            mass += part.getMass();
        }
        return mass;
    }
    public float getFuel() {
        float currentFuel = 0f;
        float totalFuel = 0f;
        for(Part part: parts) {
            if(part.getType().equals("fuelTank")) {
                FuelTank fuelTank = (FuelTank) part;
                currentFuel += fuelTank.getCurrentFuel();
                totalFuel += fuelTank.getFuelCapacity();
            }
        }
        return currentFuel/totalFuel * 100;
    }
    public float getThrust() {
        float thrust = 0f;
        float totalThrust = 0f;
        for(Part part: parts) {
            if(part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                if(engine.getState()) {
                    thrust += engine.getThrust();
                }
                totalThrust += engine.getThrust();
            }
        }
        return thrust;
    }
    public float getThrustPercentage() {
        float thrust = 0f;
        float totalThrust = 0f;
        for(Part part: parts) {
            if(part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                if(engine.getState()) {
                    thrust += engine.getThrust();
                }
                totalThrust += engine.getThrust();
            }
        }
        return thrust/totalThrust * 100;
    }

    public float getFuelConsumptionRate() {
        float rate = 0f;
        for(Part part: parts) {
            if(part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                if(engine.getState()) {
                    rate += engine.getFuelConsumptionRate();
                }
            }
        }
        return rate;
    }

    public int numParts(String type) {
        int numparts = 0;
        for(Part part: parts) {
            if(part.getType().equals(type)) {
                numparts++;
            }
        }
        return numparts;
    }

//    public Vector2 getThrustDirection() {
//        Vector2 direction = new Vector2(0,0);
//        for(Part part : parts) {
//            if(part.getType().equals("engine")) {
//                Engine engine = (Engine) part;
//                float thrust = engine.getThrust();
//                if(engine.getRotation() == 1) {
//                    direction.add(0, -thrust);
//                } else if (engine.getRotation() == 2) {
//                    direction.add(thrust, 0);
//                } else if(engine.getRotation() == 3) {
//                    direction.add(0, thrust);
//                }  else if(engine.getRotation() == 4) {
//                    direction.add(-thrust, 0);
//                }
//            }
//        }
//        return direction.nor();
//    }
public Vector2 getThrustDirection(float tilt) {
    Vector2 totalThrustDirection = new Vector2(0,0);

    float tiltRad = MathUtils.degreesToRadians * tilt;  // Convert tilt to radians
    float cosTilt = MathUtils.cos(tiltRad);
    float sinTilt = MathUtils.sin(tiltRad);

    for(Part part : parts) {
        if (part.getType().equals("engine")) {
            Engine engine = (Engine) part;
            if (engine.getState()) {
                float thrust = engine.getThrust();
                float gimbalAngleRad = MathUtils.degreesToRadians * engine.getGimbal();

                Vector2 thrustDirection = new Vector2(0, 0);

                // ... [Your existing thrustDirection calculation code] ...

                // Rotate the thrust direction by the tilt angle
                float rotatedX = thrustDirection.x * cosTilt - thrustDirection.y * sinTilt;
                float rotatedY = thrustDirection.x * sinTilt + thrustDirection.y * cosTilt;

                thrustDirection.set(rotatedX, rotatedY);

                totalThrustDirection.add(thrustDirection);
            }
        }
    }
    return totalThrustDirection.nor();
}

    public Vector2 getThrustDirection() {
        Vector2 totalThrustDirection = new Vector2(0,0);

        for(Part part : parts) {
            if (part.getType().equals("engine")) {
                Engine engine = (Engine) part;
                if (engine.getState()) {
                    float thrust = engine.getThrust();
                    float gimbalAngleRad = MathUtils.degreesToRadians * engine.getGimbal(); // Assuming getGimbalAngle() returns the gimbal tilt in degrees

                    Vector2 thrustDirection = new Vector2(0, 0);

                    if (engine.getRotation() == 1) {
                        thrustDirection.set(MathUtils.sin(gimbalAngleRad) * thrust, -MathUtils.cos(gimbalAngleRad) * thrust);
                    } else if (engine.getRotation() == 4) {
                        thrustDirection.set(MathUtils.cos(gimbalAngleRad) * thrust, MathUtils.sin(gimbalAngleRad) * thrust);
                    } else if (engine.getRotation() == 3) {
                        thrustDirection.set(-MathUtils.sin(gimbalAngleRad) * thrust, MathUtils.cos(gimbalAngleRad) * thrust);
                    } else if (engine.getRotation() == 2) {
                        thrustDirection.set(-MathUtils.cos(gimbalAngleRad) * thrust, -MathUtils.sin(gimbalAngleRad) * thrust);
                    }

                    totalThrustDirection.add(thrustDirection);
                }
            }
        }
        return rotateVector(totalThrustDirection.nor(), tilt);
    }

    public void drawEquilateralTriangle(ShapeRenderer shapeRenderer, float x, float y, float size, Vector2 direction) {
        // Calculate the triangle's vertices based on the given direction.
        Vector2 p1 = new Vector2(0, size).rotateRad(direction.angleRad()).add(x, y);
        Vector2 p2 = new Vector2(0, size).rotateRad(direction.angleRad() + (float)(2 * Math.PI / 3)).add(x, y);
        Vector2 p3 = new Vector2(0, size).rotateRad(direction.angleRad() - (float)(2 * Math.PI / 3)).add(x, y);

        // Ensure the renderer is set up to draw filled shapes.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);

        // Draw the triangle
        shapeRenderer.triangle(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y);

        shapeRenderer.end();
    }

    public Vector2 calculateCenterOfMass() {
        Vector2 weightedPositionSum = new Vector2(0, 0);
        float totalMass = 0.0f;

        for (Part part : parts) {
            float mass = part.getMass();
            Vector2 position = part.getCenter();

            weightedPositionSum.add(position.cpy().scl(mass));
            totalMass += mass;
        }

        if (totalMass == 0) { // Avoid division by zero
            return new Vector2(0, 0);
        }

        return weightedPositionSum.scl(1 / totalMass); // Divide to get center of mass
    }

    public void setMomentOfInertia(float inertia) {
        momentOfInertia = inertia;
    }

    public Vector2 rotatePointAroundCenter(Vector2 point, Vector2 center, float angleInDegrees) {
        // Convert the angle from degrees to radians
        float angleInRadians = MathUtils.degreesToRadians * angleInDegrees;

        // Translate the point back to the origin
        float translatedX = point.x - center.x;
        float translatedY = point.y - center.y;

        // Apply the rotation matrix
        float rotatedX = translatedX * MathUtils.cos(angleInRadians) - translatedY * MathUtils.sin(angleInRadians);
        float rotatedY = translatedX * MathUtils.sin(angleInRadians) + translatedY * MathUtils.cos(angleInRadians);

        // Translate the point back
        float finalX = rotatedX + center.x;
        float finalY = rotatedY + center.y;

        return new Vector2(finalX, finalY);
    }

    public Vector2 rotateVector(Vector2 vector, float angleInDegrees) {
        float angleInRadians = MathUtils.degreesToRadians * angleInDegrees;
        float cos = MathUtils.cos(angleInRadians);
        float sin = MathUtils.sin(angleInRadians);

        float rotatedX = vector.x * cos - vector.y * sin;
        float rotatedY = vector.x * sin + vector.y * cos;

        return new Vector2(rotatedX, rotatedY);
    }


}
