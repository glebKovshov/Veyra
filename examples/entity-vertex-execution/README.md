# Entity vertex midpoint execution

The original smoke fixture makes `gbuffers_entities.vsh` consume `mc_midTexCoord` and checks that its nonzero per-quad UV midpoint produces the magenta entity signal. Veyra appends the midpoint attribute only to this pipeline's vertex format and averages UV0 over each four-vertex quad. Minecraft's shared `NEW_ENTITY` format stays unchanged.
