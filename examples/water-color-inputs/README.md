# Water color inputs

An original diagnostic pack for water above an opaque floor. Terrain writes red to colortex4 and green to colortex5; deferred writes blue to colortex5's next ping-pong side. Water samples gaux1, gaux2 and the modern colortex5 alias, plus a half-resolution read-only colortex7 initialized to its declared clear color.

Magenta means all four reads matched their expected contents. Green indicates a failed read or stale buffer side. The final pass shows the water signal through colortex1. The software Vulkan integration matrix compares it against shaders disabled.

Only water geometry currently has these auxiliary inputs. Reading a color image also attached to that draw's framebuffer is rejected; no image-feedback semantics are claimed.
