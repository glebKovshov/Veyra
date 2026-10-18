#version 120

uniform sampler2D texture;
uniform vec3 cameraPosition;
uniform vec3 cameraPositionFract;
uniform ivec3 cameraPositionInt;
uniform vec3 previousCameraPosition;
uniform vec3 previousCameraPositionFract;
uniform ivec3 previousCameraPositionInt;
uniform mat4 gbufferModelViewInverse;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    vec3 worldPosition = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz
        + vec3(cameraPositionInt) + cameraPositionFract;
    vec3 positionColor = fract(abs(worldPosition) * 0.0625);
    vec3 cameraCell = mix(fract(abs(cameraPosition) * 0.0625), cameraPositionFract, 0.25);
    vec3 previousCameraCell = mix(fract(abs(previousCameraPosition) * 0.0625), previousCameraPositionFract, 0.25);
    vec3 cameraDelta = vec3(cameraPositionInt - previousCameraPositionInt)
        + cameraPositionFract - previousCameraPositionFract;
    float cameraMotion = clamp(length(cameraDelta + cameraCell - previousCameraCell) * 0.25, 0.0, 1.0);
    gl_FragColor = vec4(mix(positionColor, cameraCell, 0.2) + vec3(cameraMotion * 0.2), albedo.a);
}
