# Animated PBR atlas readback

This original diagnostic supplies synchronized two-frame albedo, `_n.png`, and `_s.png` sprites for the visible grass block top. It samples both material maps at mip level 2. Magenta and green denote the two animation states only when both companion maps follow the base atlas sprite; an unchanged frame or a single animated map produces a different signal. Graphics smoke captures both states, then checks that their signal disappears when shader rendering is disabled.

The fixture covers animated terrain normal and specular atlas regions, frame-state synchronization, interpolation-free frame selection, mip-level uploads, resource-pack loading, and disable cleanup. It is an isolated diagnostic and does not certify broad shaderpack PBR compatibility.
