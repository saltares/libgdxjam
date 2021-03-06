package com.siondream.libgdxjam.ecs.components.agents;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.box2d.Fixture;

public class PlayerComponent implements Component {
	public Fixture feetSensor;
	public Fixture fixture;
	public float groundFriction = 50.0f;
	public float maxVelocityX = 5.0f;
	public float maxVelocityJumpX = 5.0f;
	public float maxVelocityCrouchX = 0.4f;
	public float horizontalImpulse = 4.0f;
	public float verticalImpulse = 5.5f;
	public float currMaxVelX = 0.0f;
	public boolean jump;
	public boolean grounded;
	public boolean crouching;
	public boolean exposed;
	public boolean wantsToMove;
	public int feetContacts = 0;
	public int direction = 1;
}
