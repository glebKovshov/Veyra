# Shadow color MRT readback

This diagnostic writes separate values to `shadowcolor0` and `shadowcolor1`, then samples the second attachment from terrain. The blue signal is present only when the custom shadow vertex stage emits the expected terrain-stage value to attachment 1 and the shadow MRT pass preserves it for the later terrain pass.
