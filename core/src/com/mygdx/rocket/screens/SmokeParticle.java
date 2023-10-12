package com.mygdx.rocket.screens;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Vector2;

class SmokeParticle {
    Circle circle;
    Vector2 direction;
    float age = 0;  // A timer to keep track of how long this particle has been alive.
    final float maxAge = 4f;  // The maximum time a particle can exist (2 seconds for instance).

    public SmokeParticle(float x, float y, float radius, Vector2 direction) {
        this.circle = new Circle(x, y, radius);
        this.direction = direction;
    }

    // Update the particle's position and age
    public void update(float delta) {
        circle.x += direction.x * delta;
        circle.y += direction.y * delta;
        age += delta;
    }

    // Check if the particle is old enough to be removed
    public boolean isOld() {
        return age >= maxAge;
    }
}