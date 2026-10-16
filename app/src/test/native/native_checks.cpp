#include "../../main/jni/nes_runtime.h"
#include <cassert>
#include <limits>
int main() {
    assert(sva::frameDuration(60).count() == 16666666);
    assert(sva::frameDuration(50).count() == 20000000);
    assert(sva::frameDuration(0) == sva::frameDuration(60));
    assert(sva::frameDuration(std::numeric_limits<double>::quiet_NaN()) == sva::frameDuration(60));
    assert(sva::frameDuration(1000) == sva::frameDuration(60));
    assert(sva::rgbaFromXrgb(0x00ff0000) == 0xff0000ff); // Red: R,G,B,X bytes.
    assert(sva::rgbaFromXrgb(0x000000ff) == 0xffff0000); // Blue.
    assert(sva::rgbaFrom16(0xf800,true) == 0xff0000ff);
    assert(sva::rgbaFrom16(0x07e0,true) == 0xff00ff00);
    assert(sva::rgbaFrom16(0x001f,true) == 0xffff0000);
    assert(sva::rgbaFrom16(0x7c00,false) == 0xff0000ff);
    assert(sva::rgbaFrom16(0xffff,true) == 0xffffffff);
}
