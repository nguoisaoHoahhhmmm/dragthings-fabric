package dragthings.config;

/**
 * physics.json: how dragging moves, in real units (not percentages).
 *
 * Ranges (anything outside is clamped when the file is loaded):
 *   lerpSpeed 0.05-0.5, dragForce 0.1-2.0, friction 0.7-0.99, maxVelocity 0.5-3.0,
 *   throwMultiplier 0.1-2.0, dragDistance 1-10, pickupRange 1-10, maxDragCount 1-8
 */
public final class PhysicsConfig {

    // ── Movement ──────────────────────────────────────────────────────────
    public float lerpSpeed = 0.25f;
    public float dragForce = 0.5f;
    public float friction = 0.90f;
    public float maxVelocity = 1.2f;
    public float throwMultiplier = 0.8f;

    // ── Distance and reach ────────────────────────────────────────────────
    public int dragDistance = 4;
    public int pickupRange = 5;
    public boolean scrollToAdjustDistance = true;
    public int maxDragCount = 3;

    // ── Behaviour ─────────────────────────────────────────────────────────
    public boolean gravityOnRelease = true;
    /** Releasing a block item slowly while aiming at a block face places it instead of throwing. */
    public boolean enableBlockPlacement = true;
    /** Placing needs Shift held, so a normal release never turns into an accidental placement. */
    public boolean placeRequiresShift = true;

    // ── Feel of the movement ──────────────────────────────────────────────
    /** Ramp the spring up after grabbing instead of full force on the first tick. */
    public boolean enableStartupEase = true;
    /** 0-1000 ms. */
    public int startupEaseMs = 250;
    /** 0.01-1.0. How fast scrolling the drag distance eases to the new value. */
    public float distanceEaseSpeed = 0.18f;
    /** 0.1-0.9. How fast a follower that fell behind catches up. */
    public float followerCatchupSpeed = 0.40f;

    void sanitize() {
        lerpSpeed = ConfigFiles.clamp(lerpSpeed, 0.05f, 0.5f);
        dragForce = ConfigFiles.clamp(dragForce, 0.1f, 2.0f);
        friction = ConfigFiles.clamp(friction, 0.7f, 0.99f);
        maxVelocity = ConfigFiles.clamp(maxVelocity, 0.5f, 3.0f);
        throwMultiplier = ConfigFiles.clamp(throwMultiplier, 0.1f, 2.0f);
        dragDistance = ConfigFiles.clamp(dragDistance, 1, 10);
        pickupRange = ConfigFiles.clamp(pickupRange, 1, 10);
        maxDragCount = ConfigFiles.clamp(maxDragCount, 1, 8);
        startupEaseMs = ConfigFiles.clamp(startupEaseMs, 0, 1000);
        distanceEaseSpeed = ConfigFiles.clamp(distanceEaseSpeed, 0.01f, 1.0f);
        followerCatchupSpeed = ConfigFiles.clamp(followerCatchupSpeed, 0.1f, 0.9f);
    }

    // ── Getters (same names the old DragThingsConfig had) ─────────────────
    public float getLerpSpeed()            { return lerpSpeed; }
    public float getDragForce()            { return dragForce; }
    public float getFriction()             { return friction; }
    public float getMaxVelocity()          { return maxVelocity; }
    public float getThrowMultiplier()      { return throwMultiplier; }
    public double getDragDistance()        { return dragDistance; }
    public double getPickupRange()         { return pickupRange; }
    public float getStartupEaseSeconds()   { return enableStartupEase ? startupEaseMs / 1000f : 0f; }
    public float getDistanceEaseSpeed()    { return distanceEaseSpeed; }
    public float getFollowerCatchupSpeed() { return followerCatchupSpeed; }
}