# Standalone entity material readback

This original diagnostic reads the `_s.png` associated with each entity's actual albedo SimpleTexture. The integration harness spawns an original test-world cow and supplies authored material PNGs through an original resource pack. Only entity material reads produce magenta; the terrain stays green. Missing maps give the defined zero material and cannot pass the signal predicate.

The predicate also samples the albedo and requires it to differ from the authored material color, checking that lazy material upload preserves the original texture binding.
