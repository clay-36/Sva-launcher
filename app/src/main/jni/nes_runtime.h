#pragma once
#include <chrono>
#include <cmath>
#include <cstdint>

namespace sva {
inline std::chrono::nanoseconds frameDuration(double fps) {
    if (!std::isfinite(fps) || fps < 1.0 || fps > 240.0) fps = 60.0;
    return std::chrono::nanoseconds(static_cast<int64_t>(1000000000.0 / fps));
}
// ANativeWindow's RGBX_8888 uses R,G,B,X bytes in memory (little-endian on our ABIs).
inline uint32_t rgbaFromXrgb(uint32_t pixel) {
    return 0xff000000u | ((pixel & 0xffu) << 16) | (pixel & 0xff00u) | ((pixel >> 16) & 0xffu);
}
inline uint32_t rgbaFrom16(uint16_t pixel, bool rgb565) {
    uint32_t r = (pixel >> (rgb565 ? 11 : 10)) & 31;
    uint32_t g = (pixel >> 5) & (rgb565 ? 63 : 31);
    uint32_t b = pixel & 31;
    r = (r << 3) | (r >> 2); b = (b << 3) | (b >> 2);
    g = rgb565 ? ((g << 2) | (g >> 4)) : ((g << 3) | (g >> 2));
    return 0xff000000u | r | (g << 8) | (b << 16);
}
}
