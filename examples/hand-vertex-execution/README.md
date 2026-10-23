# First-person hand vertex midpoint execution

The original smoke fixture makes `gbuffers_hand.vsh` consume `mc_midTexCoord` and checks that the item's nonzero per-quad UV midpoint produces the magenta hand signal. Veyra appends the midpoint attribute only to this hand pipeline's format and averages UV0 over each four-vertex quad. Minecraft's shared `NEW_ENTITY` format stays unchanged.
