package com.deo.launcher;

import android.view.Surface;

final class NestopiaSession {
    static { System.loadLibrary("duo_nes"); }
    private long handle;

    synchronized boolean start(String romPath, String savePath, String systemPath) {
        if (handle != 0) return true;
        handle = nativeStart(romPath, savePath, systemPath);
        return handle != 0;
    }
    synchronized void surface(Surface surface) { if (handle != 0) nativeSurface(handle, surface); }
    synchronized void input(int port, int mask) { if (handle != 0) nativeInput(handle, port, mask); }
    synchronized void pause(boolean value) { if (handle != 0) nativePause(handle, value); }
    synchronized void reset() { if (handle != 0) nativeReset(handle); }
    synchronized void stop() { if (handle != 0) { nativeStop(handle); handle = 0; } }

    private static native long nativeStart(String romPath, String savePath, String systemPath);
    private static native void nativeSurface(long handle, Surface surface);
    private static native void nativeInput(long handle, int port, int mask);
    private static native void nativePause(long handle, boolean paused);
    private static native void nativeReset(long handle);
    private static native void nativeStop(long handle);
}
