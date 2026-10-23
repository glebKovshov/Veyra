# Begin fragment stage fixture

This pack writes a full-screen magenta value to `colortex1` before shadow rendering. The final pass reads that buffer and only remains magenta when Iris `begin.fsh` ran at the correct frame boundary.

In the F6 smoke menu, enable this pack to see the full-screen magenta signal; disable it to verify the signal disappears.
