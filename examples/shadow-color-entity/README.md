# Shadow color readback

Original diagnostic reading the completed shadow color attachment through both shadowcolor0 and its legacy shadowcolor alias. Terrain writes a blue marker and the entity-specific shadow_entities program writes green. final.fsh scans the full shadow map across the current frame and turns matching samples magenta; terrain writes cannot pass the entity fixture. The entity fragment output is constant so the color-write check does not depend on custom varyings. General shadow formats, additional color attachments and mipmap properties are not implemented by this fixture.
