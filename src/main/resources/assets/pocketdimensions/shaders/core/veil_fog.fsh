#version 330

#moj_import <minecraft:fog.glsl>

// The Veil fog: falling glowing smoke along the realm's four edges, drawn over the finished frame. Ported from the
// approved study (Veil Mist Studies, "Veil Fog, game version"). Per pixel and per wall: up to Shape.z steps through the
// smoke within 10 blocks of the wall, the rest in closed form (keep in step with VeilFogMath). Writes
// vec4(light, transmittance); the pipeline blends ONE, SRC_ALPHA, so the result is scene * T + L.

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;

layout(std140) uniform VeilFog {
    mat4 InvViewProj;   // screen to camera-relative position
    vec4 Camera;        // x, z relative to the plot's min corner; y in world blocks
    vec4 Plot;          // x, y: plot size along x and z; z: time in seconds; w: night 0..1
    vec4 Shape;         // x, y: top fade start and end y; z: steps
};

in vec2 texCoord;
out vec4 fragColor;

const float H = 2.4;          // the smoke halves about every 1.7 blocks out from its face
const float SIGMA = 0.8;      // density at the face
const float XMAX = 24.0;      // nothing worth drawing further out
const float NEAR = 10.0;      // stepped within this distance of the wall, closed form beyond
const float NEAR_PATH = 48.0; // at most this much of a view is stepped
const float SKY = 450.0;      // how far a sky pixel counts as
const float SPEED = 0.4;      // all of the smoke's motion runs at this share of the study's (falls about 1.3 blocks a second)
const float TILE = 34.0, ATLAS_W = 1088.0, SLICES = 32.0;

// ---- the noise atlas: 32 slices side by side, each pixel holding its slice and the next ----
vec4 atlas(vec3 c, out float f) {
    float s = fract(c.z) * SLICES;
    float k = min(floor(s), SLICES - 1.0);
    f = s - k;
    float u = (k * TILE + 1.5 + fract(c.x) * SLICES) / ATLAS_W;
    return textureLod(NoiseSampler, vec2(u, c.y + 0.5 / SLICES), 0.0);
}
float fieldA(vec3 c) { float f; vec4 n = atlas(c, f); return mix(n.r, n.g, f); }
float fieldB(vec3 c) { float f; vec4 n = atlas(c, f); return mix(n.b, n.a, f); }

// ---- the falling smoke at a point: along the wall, height, distance from the wall ----
// Two fields falling at 3.2 and 2 blocks a second, multiplied, so it changes as it falls; a sideways curl; tile sizes
// that never line up and slow sine bends travelling with each field, so the small atlas does not show as a pattern.
float smoke(float along, float y, float d, float t) {
    float qz = along + 1.2 * sin(y / 7.0 + t * 0.9 + d * 0.6);
    float ya = y + t * 3.2, yb = y + t * 2.0;
    vec2 wa = vec2(sin(qz / 41.0 + ya / 67.0), sin(qz / 29.3 - ya / 53.0 + 1.7));
    vec2 wb = vec2(sin(qz / 37.0 - yb / 59.0 + 4.1), sin(qz / 23.7 + yb / 71.0 + 0.6));
    float a = 0.25 + 0.5 * fieldA(vec3((qz + 5.0 * wa.x) / 19.4, (ya + 4.0 * wa.y) / 28.0, d / 12.0 + t * 0.0625));
    float b = 0.25 + 0.5 * fieldB(vec3((qz + 4.0 * wb.x) / 12.0 + 0.6, (yb + 3.0 * wb.y) / 19.1, d / 10.0 - t * 0.075));
    float broad = 0.07 * sin(qz / 97.0 + 0.4) + 0.05 * sin(qz / 61.0 - ya / 140.0);
    return clamp((a * b * 2.2 - 0.5) * 1.6 + 0.52 + broad, 0.0, 1.0);
}

// ---- closed forms (VeilFogMath) ----
float amount(float dc, float rx, float ta, float tb) {
    if (tb <= ta) return 0.0;
    float da = dc + rx * ta, db = dc + rx * tb;
    if (abs(rx) < 1e-3) return exp(-0.5 * (da + db) / H) * (tb - ta);   // nearly along the wall: midpoint
    return (-H / rx) * (exp(-db / H) - exp(-da / H));
}
float middle(float dc, float rx, float ta, float tb) {
    if (abs(rx) < 1e-3 || tb <= ta) return 0.5 * (ta + tb);
    float da = dc + rx * ta, db = dc + rx * tb;
    float dm = -H * log(0.5 * (exp(-da / H) + exp(-db / H)));
    return clamp((dm - dc) / rx, ta, tb);
}
vec2 clipD(float lo, float hi, float dc, float rx, float ta, float tb) {
    if (abs(rx) < 1e-4) return (dc >= lo && dc <= hi) ? vec2(ta, tb) : vec2(0.0, -1.0);
    float a = (lo - dc) / rx, b = (hi - dc) / rx;
    return vec2(max(ta, min(a, b)), min(tb, max(a, b)));
}
float top(float y) { return 1.0 - smoothstep(Shape.x, Shape.y, y); }

// vanilla's distance fog over a point t blocks along the view
vec3 vanilla(vec3 c, float t, vec3 rd) {
    vec3 p = rd * t;
    float v = total_fog_value(t, max(length(p.xz), abs(p.y)), FogEnvironmentalStart, FogEnvironmentalEnd,
                              FogRenderDistanceStart, FogRenderDistanceEnd);
    return mix(c, FogColor.rgb, v * FogColor.a);
}

// ---- one wall: dc, rx as in VeilFogMath; ac, ar the same for the coordinate along the wall ----
// Returns vec4(light, transmittance); tin is where the view enters the smoke (1e9 if never).
vec4 wall(float dc, float rx, float ac, float ar, vec3 rd, float tEnd, float j, vec3 deep, vec3 lit, out float tin) {
    tin = 1e9;
    vec2 r = clipD(0.0, XMAX, dc, rx, 0.0, tEnd);
    if (r.y <= r.x) return vec4(0.0, 0.0, 0.0, 1.0);
    tin = r.x;
    float time = Plot.z * SPEED;
    vec2 n = clipD(0.0, NEAR, dc, rx, r.x, r.y), f = clipD(NEAR, XMAX, dc, rx, r.x, r.y);
    float nEnd = min(n.y, n.x + NEAR_PATH);

    // the near stretch, stepped
    float Tn = 1.0; vec3 Ln = vec3(0.0);
    if (nEnd > n.x) {
        int count = int(Shape.z);
        float dt = (nEnd - n.x) / Shape.z;
        for (int i = 0; i < 16; i++) {
            if (i >= count) break;
            float t = n.x + (float(i) + j) * dt;
            float y = Camera.y + rd.y * t, d = dc + rx * t, al = ac + ar * t;
            float b = smoke(al, y, d, time);
            float out_ = d - 1.2 - 0.7 * sin(al / 8.0 + (y + time * 3.0) / 9.0) - (b - 0.4) * 5.0;  // thick smoke stands further out
            float sigma = SIGMA * exp(-max(out_, -3.0) / H) * smoothstep(0.25, 0.6, b) * top(y)
                        * mix(0.55, 1.0, smoothstep(0.0, 3.0, t));                                // thinner right around you
            float a = 1.0 - exp(-sigma * dt);
            vec3 c = mix(deep, lit, smoothstep(0.35, 0.8, b)) * mix(1.0, 0.75, smoothstep(0.0, 200.0, y));
            Ln += Tn * a * vanilla(c, t, rd);
            Tn *= 1.0 - a;
        }
    }
    // the rest of a long stretch along the wall, and the thin smoke further out: closed form
    float k = SIGMA * exp(1.2 / H) * 0.5;
    vec3 avg = mix(deep, lit, 0.3);
    float tr = middle(dc, rx, nEnd, n.y), tf = middle(dc, rx, f.x, f.y);
    float Tr = exp(-k * amount(dc, rx, nEnd, n.y) * top(Camera.y + rd.y * tr));
    float Tf = exp(-k * amount(dc, rx, f.x, f.y) * top(Camera.y + rd.y * tf));
    vec3 Lr = vanilla(avg, tr, rd) * (1.0 - Tr);
    vec3 Lf = vanilla(avg, tf, rd) * (1.0 - Tf);
    vec3 L = Ln + Tn * Lr; float T = Tn * Tr;                  // near, then its rest
    if (f.x < n.x) { L = Lf + Tf * L; } else { L = L + T * Lf; } // the outer smoke in front or behind
    T *= Tf;
    return vec4(L, T);
}

void main() {
    float z = texture(DepthSampler, texCoord).r;
    vec4 v = InvViewProj * vec4(texCoord * 2.0 - 1.0, z * 2.0 - 1.0, 1.0);
    vec3 rel = v.xyz / v.w;
    float tEnd = z >= 1.0 ? SKY : length(rel);
    vec3 rd = normalize(rel);

    float night = Plot.w;
    vec3 deep = mix(vec3(0.62, 0.62, 0.84), vec3(0.20, 0.17, 0.42), night);
    vec3 lit  = mix(vec3(0.96, 0.97, 1.00), vec3(0.62, 0.66, 1.00), night);
    float j = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));   // per-pixel jitter

    // the four walls: west (x = 0), east (x = size), north (z = 0), south (z = size)
    vec4 w[4]; float tin[4];
    w[0] = wall(Camera.x,          rd.x, Camera.z, rd.z, rd, tEnd, j, deep, lit, tin[0]);
    w[1] = wall(Plot.x - Camera.x, -rd.x, Camera.z, rd.z, rd, tEnd, j, deep, lit, tin[1]);
    w[2] = wall(Camera.z,          rd.z, Camera.x, rd.x, rd, tEnd, j, deep, lit, tin[2]);
    w[3] = wall(Plot.y - Camera.z, -rd.z, Camera.x, rd.x, rd, tEnd, j, deep, lit, tin[3]);

    // put them together front to back (at most two are ever non-empty: a corner)
    vec3 L = vec3(0.0); float T = 1.0;
    bool used[4] = bool[](false, false, false, false);
    for (int round = 0; round < 4; round++) {
        int best = -1; float bt = 1e9;
        for (int i = 0; i < 4; i++) if (!used[i] && tin[i] < bt) { bt = tin[i]; best = i; }
        if (best < 0) break;
        used[best] = true;
        L += T * w[best].rgb;
        T *= w[best].a;
    }
    fragColor = vec4(L, T);
}
