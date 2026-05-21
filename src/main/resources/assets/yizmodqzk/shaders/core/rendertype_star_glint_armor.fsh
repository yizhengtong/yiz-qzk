#version 150

in vec2 texCoord;
in vec4 vertexColor;
in vec3 fPos;

uniform float GameTime;

out vec4 fragColor;

void main() {
    // Armor: model geometry IS the mask — no texture alpha discard needed

    vec3 dir = normalize(fPos);
    float time = GameTime * 0.4;

    vec3 col = vec3(0.05, 0.0, 0.08);

    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        float s = 0.4 + fi * 0.08;
        vec3 ray = dir * s;

        float u = 0.5 + atan(ray.z, ray.x) / 6.28318;
        float v = 0.5 + asin(clamp(ray.y / length(ray.xyz), -1.0, 1.0)) / 3.14159;

        u = u * (12.0 + fi * 2.0);
        v = (v + time * 0.003 * fi) * (8.0 + fi);

        float seed = fract(sin(dot(floor(vec2(u, v)), vec2(12.9898, 78.233))) * 43758.5453);

        if (seed > 0.92 - fi * 0.005) {
            float bright = (seed - 0.92 + fi * 0.005) / (1.0 - 0.92 + fi * 0.005);
            float twinkle = sin(time * 3.0 + seed * 100.0) * 0.4 + 0.6;
            float a = bright * twinkle * (1.0 / (1.0 + fi * 0.3));
            col += vec3(a * 0.6, a * 0.75, a) * a;
        }
    }

    col.rgb *= vertexColor.rgb;
    fragColor = vec4(col.rgb, 0.5);
}
