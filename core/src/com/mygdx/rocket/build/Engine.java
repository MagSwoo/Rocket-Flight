package com.mygdx.rocket.build;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Engine extends Part {
    private float thrust; // in newtons
    private boolean isOn = false;
    private float fuelConsumptionRate; // in liters per second
    private Vector2 thrustDirection = new Vector2(0, -1);
    private float gimbal = 0; // in degrees

    public Engine(String name, float mass, Vector2 size, Array<AttachmentPoint> attachmentPoints, float thrust, float fuelConsumptionRate) {
        super(name, mass, size, attachmentPoints);
        this.thrust = thrust;
        this.fuelConsumptionRate = fuelConsumptionRate;
        super.setType("engine");
    }

    public Engine(Engine part) {
        super(part);
        this.thrust = 0 + part.getThrust();
        this.fuelConsumptionRate = 0 + part.getFuelConsumptionRate();
        super.setType("engine");
    }

    public float getThrust() {
        return thrust;
    }

    public void setThrust(float thrust) {
        this.thrust = thrust;
    }

    public float getFuelConsumptionRate() {
        return fuelConsumptionRate;
    }

    public void setFuelConsumptionRate(float fuelConsumptionRate) {
        if(fuelConsumptionRate >= 0) {
            this.fuelConsumptionRate = fuelConsumptionRate;
        } else {
            throw new IllegalArgumentException("Fuel consumption rate cannot be negative");
        }
    }

    public void setState(boolean state) {
        isOn = state;
    }
    public void toggleState() {
        isOn = !isOn;
    }
    public boolean getState() {
        return isOn;
    }

    public Vector2 getDirection() {
        return thrustDirection;
    }

    public void adjustGimbal(float deltaAngle) {
        gimbal += deltaAngle;
        gimbal = MathUtils.clamp(gimbal, -3f, 3f);
    }

    public float getGimbal() {
        return gimbal;
    }

    public void setGimbal(float gimbalnew) {
        gimbal = gimbalnew;
        gimbal = MathUtils.clamp(gimbal, -3f, 3f);
    }

}