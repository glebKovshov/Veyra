# Compute color target probe

Writes 0.25 to colortex4 through a deferred fragment pass, then reads it as a sampler and writes 0.5 through colorimg4. A second compute stage reads that storage value and writes 0.75; the composite pass requires that exact result and emits the harness magenta marker.

This checks the active ping-pong side, shared sampled/storage backing, compute ordering and graphics visibility. The graphics harness also checks disable/re-enable, failed replacement rollback and cross-pack restoration when those probes are enabled.
