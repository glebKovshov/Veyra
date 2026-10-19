# Post shadow inputs across four phases

Original diagnostic: prepare, deferred, composite and final each require separate shadow depths (0.25 / 0.75), authored shadow color and the matching legacy color alias. Prepare/deferred/composite write a sentinel through colortex4/5/6; final requires the full chain before producing magenta. Deferred uses modern float comparison helpers; composite uses legacy vec4 shadow2D helpers. Default/aliased images, skipped phases and zero fallback cannot pass. The world includes water and enables shadowTranslucent explicitly.
