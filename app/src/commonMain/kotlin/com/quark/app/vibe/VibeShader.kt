package com.quark.app.vibe

/**
 * `shaders/vibe.frag`, translated to SkSL.
 *
 * The maths is untouched — simplex noise driving three rotating blobs of
 * colour. What changed is the dialect Skia speaks:
 *
 *  - `#include <flutter/runtime_effect.glsl>` and `FlutterFragCoord()` are
 *    Flutter's; SkSL passes the coordinate into `main` instead.
 *  - `vec2/3/4` are not SkSL types; they are `float2/3/4`.
 *  - `out vec4 fragColor` becomes a `half4` return.
 *  - The noise loop counted with a float. SkSL needs a loop it can unroll, so it
 *    counts with an int; the variable was never used in the body anyway.
 *
 * Uniforms keep their names, so [VibeRenderer] can set them by name through
 * `RuntimeShaderBuilder` instead of packing a buffer by hand and getting the
 * alignment wrong in a way that only shows up as the wrong colours.
 */
internal val VIBE_SKSL = """
uniform float2 uScreenSize;
uniform float  uTime;
uniform float  uScale;
uniform float  uBgBrightness;

uniform float3 uColor0;
uniform float3 uColor1;
uniform float3 uColor2;
uniform float3 uColor3;
uniform float3 uColor4;
uniform float3 uColor5;

uniform float3 uRotation0;
uniform float3 uRotation1;
uniform float3 uRotation2;

float4 permute(float4 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
float4 taylorInvSqrt(float4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise3(float3 v) {
  const float2 C = float2(0.1666667, 0.3333333);
  const float4 D = float4(0.0, 0.5, 1.0, 2.0);

  float3 i  = floor(v + dot(v, C.yyy));
  float3 x0 = v - i + dot(i, C.xxx);

  float3 g  = step(x0.yzx, x0.xyz);
  float3 l  = 1.0 - g;
  float3 i1 = min(g.xyz, l.zxy);
  float3 i2 = max(g.xyz, l.zxy);

  float3 x1 = x0 - i1 + C.xxx;
  float3 x2 = x0 - i2 + 2.0 * C.xxx;
  float3 x3 = x0 - 1.0 + 3.0 * C.xxx;

  i = mod(i, 289.0);
  float4 p = permute(permute(permute(
               i.z + float4(0.0, i1.z, i2.z, 1.0))
             + i.y + float4(0.0, i1.y, i2.y, 1.0))
             + i.x + float4(0.0, i1.x, i2.x, 1.0));

  float n_ = 0.142857142857;
  float3 ns = n_ * D.wyz - D.xzx;

  float4 j = p - 49.0 * floor(p * ns.z * ns.z);
  float4 x_ = floor(j * ns.z);
  float4 y_ = floor(j - 7.0 * x_);

  float4 x = x_ * ns.x + ns.yyyy;
  float4 y = y_ * ns.x + ns.yyyy;
  float4 h = 1.0 - abs(x) - abs(y);

  float4 b0 = float4(x.xy, y.xy);
  float4 b1 = float4(x.zw, y.zw);

  float4 s0 = floor(b0) * 2.0 + 1.0;
  float4 s1 = floor(b1) * 2.0 + 1.0;
  float4 sh = -step(h, float4(0.0));

  float4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
  float4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

  float3 p0 = float3(a0.xy, h.x);
  float3 p1 = float3(a0.zw, h.y);
  float3 p2 = float3(a1.xy, h.z);
  float3 p3 = float3(a1.zw, h.w);

  float4 norm = taylorInvSqrt(float4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
  p0 *= norm.x; p1 *= norm.y; p2 *= norm.z; p3 *= norm.w;

  float4 m = max(0.6 - float4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
  m = m * m;
  return 42.0 * dot(m * m, float4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}

float tri(float x) { return abs(fract(x) - 0.5); }

float3 tri3(float3 p) {
  return float3(tri(p.z + tri(p.y * 20.0)),
                tri(p.z + tri(p.x * 1.0)),
                tri(p.y + tri(p.x * 1.0)));
}

float triNoise3D(float3 p, float spd) {
  float z  = 0.4;
  float rz = 0.1;
  float3 bp = p;
  for (int i = 0; i <= 4; i++) {
    float3 dg = tri3(bp * 0.01);
    p += (dg + uTime * 0.1 * spd);
    bp *= 4.0;
    z  *= 0.9;
    p  *= 1.6;
    rz += (tri(p.z + tri(0.6 * p.x + 0.1 * tri(p.y)))) / z;
  }
  return smoothstep(0.0, 8.0, rz + sin(rz + sin(z) * 2.8) * 2.2);
}

float2 rotate(float2 p, float a) {
  float s = sin(a), c = cos(a);
  return float2(p.x * c - p.y * s, p.x * s + p.y * c);
}

float light(float intensity, float attenuation, float dist) {
  return intensity / (1.0 + dist + dist * attenuation);
}

float4 makeNoiseBlob2(float2 uv, float3 color1, float3 color2, float strength, float offset) {
  float len = length(uv);
  float n0  = snoise3(float3(uv * 1.2 + offset, uTime * 0.5 + offset)) * 0.5 + 0.5;
  float r0  = mix(0.0, 1.0, n0);
  float d0  = distance(uv, r0 / max(len, 0.0001) * uv);
  float v0  = smoothstep(r0 + 0.1 + (sin(uTime + offset) + 1.0), r0, len);
  float v1  = light(0.15 * (1.0 + 1.5 * (-sin(uTime * 2.0 + offset * 0.5) * 0.5)) + 0.3 * strength, 10.0, d0);

  float3 col = mix(color1, color2, uv.y * 2.0);
  col = clamp(col + v1, 0.0, 1.0);
  return float4(col, v0);
}

float4 makeBlob(float2 uv,
                float blob,
                float3 color1,
                float3 color2,
                float width,
                float baseReaction,
                float offset,
                float2 noiseOffset) {
  float len         = length(uv);
  float outerRadius = blob + width * 0.5 + baseReaction;
  float4 noise      = makeNoiseBlob2(uv + noiseOffset, color1, color2, 0.0, offset);
  noise.a           = mix(0.0, noise.a, smoothstep(outerRadius, 0.5, len));
  return noise;
}

half4 main(float2 fragCoord) {
  float2 uv = fragCoord / uScreenSize.xy;
  uv = uv * 2.0 - 1.0;
  uv.y *= uScreenSize.y / min(uScreenSize.x, uScreenSize.y) / uScale;
  uv.x *= uScreenSize.x / min(uScreenSize.x, uScreenSize.y) / uScale;

  float2 ruv = uv * 2.0;
  float  pa  = atan(ruv.y, ruv.x);
  float  idx = (pa / 3.1415) / 2.0;

  float2 ruv1 = rotate(uv * 2.0, 3.1415);
  float  pa1  = atan(ruv1.y, ruv1.x);
  float  idx1 = (pa1 / 3.1415) / 2.0;
  float  idx21= (pa1 / 3.1415 + 1.0) / 2.0 * 3.1415;

  float spark = triNoise3D(float3(idx, 0.0, 0.0), 0.1);
  spark = mix(spark, triNoise3D(float3(idx1, 0.0, idx1), 0.1), smoothstep(0.9, 1.0, sin(idx21)));
  spark = spark * 0.2 + pow(spark, 10.0);
  spark = smoothstep(0.0, spark, 0.3) * spark;

  float3 bg    = float3(uBgBrightness);
  float3 color = float3(0.0);

  float n0 = snoise3(float3(uv * 1.2, uTime * 0.5));

  float3 colors[6];
  colors[0] = uColor0; colors[1] = uColor1; colors[2] = uColor2;
  colors[3] = uColor3; colors[4] = uColor4; colors[5] = uColor5;

  float3 rots[3];
  rots[0] = uRotation0; rots[1] = uRotation1; rots[2] = uRotation2;

  float CIRCLE_WIDTH_BASE   = 0.8;
  float CIRCLE_WIDTH_STEP   = 0.2;
  float SPARK_STRENGTH_BASE = 1.0;
  float SPARK_STRENGTH_STEP = 0.3;
  float CIRCLE_RADIUS_BASE  = 0.95;
  float CIRCLE_RADIUS_STEP  = 0.15;
  float CIRCLE_OFFSET_STEP  = 1.57;

  float totalAlpha = 0.0;

  for (int i = 0; i < 3; i++) {
    float fi     = float(i);
    float radius = CIRCLE_RADIUS_BASE - CIRCLE_RADIUS_STEP * fi;
    float4 blob  = makeBlob(
      uv,
      mix(radius, radius + 0.3, n0),
      colors[i],
      colors[i + 3],
      CIRCLE_WIDTH_BASE - CIRCLE_WIDTH_STEP * fi,
      (SPARK_STRENGTH_BASE - SPARK_STRENGTH_STEP * fi) * spark,
      CIRCLE_OFFSET_STEP * fi,
      rotate(rots[i].xy, uTime * rots[i].z)
    );
    color = mix(color, blob.rgb, blob.a);
    totalAlpha = totalAlpha + blob.a * (1.0 - totalAlpha);
  }

  float bgAlpha = uBgBrightness;
  float finalAlpha = clamp(totalAlpha + bgAlpha, 0.0, 1.0);
  color = mix(color, bg, bgAlpha * (1.0 - totalAlpha));

  float2 uvNorm = fragCoord / uScreenSize.xy;
  float2 edge = smoothstep(0.0, 0.15, uvNorm) * smoothstep(0.0, 0.15, 1.0 - uvNorm);
  float edgeFade = edge.x * edge.y;

  return half4(float4(color, finalAlpha * edgeFade));
}
""".trimIndent()
