package com.sva.launcher;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs an upstream NTSC diagnostic fixture, never a commercial game. */
public final class NativeRuntimeTest extends InstrumentationTestCase {
    public void testFramePacingPauseAndSessionOwnership() throws Exception {
        File rom = File.createTempFile("sva-native-", ".nes", getInstrumentation().getTargetContext().getCacheDir());
        File save = new File(rom.getPath()+".sav");
        NativeBridge first = new NativeBridge(), second = new NativeBridge();
        ImageReader frames = ImageReader.newInstance(256,240,PixelFormat.RGBX_8888,3);
        AtomicInteger count = new AtomicInteger();
        frames.setOnImageAvailableListener(reader -> {
            try(Image image = reader.acquireLatestImage()) { if(image != null)count.incrementAndGet(); }
        },new Handler(Looper.getMainLooper()));
        try {
            try(InputStream input = getInstrumentation().getContext().getAssets().open("1.line_phase.nes");
                OutputStream output = new FileOutputStream(rom)) {
                byte[] buffer = new byte[8192]; int read;
                while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
            }
            assertTrue("Diagnostic fixture did not load",first.start(rom.getPath(),save.getPath(),rom.getParent()));
            assertFalse("Concurrent libretro session must be rejected",second.start(rom.getPath(),save.getPath(),rom.getParent()));
            first.surface(frames.getSurface()); first.pause(false);
            SystemClock.sleep(700);
            int rendered = count.get();
            assertTrue("No frames received",rendered > 0);
            assertTrue("Emulation ran faster than its configured frame rate: "+rendered,rendered <= 85);
            first.pause(true); SystemClock.sleep(100);
            int paused = count.get(); SystemClock.sleep(200);
            assertTrue("Frames continued while paused",count.get()-paused <= 1);
            first.reset(); first.pause(false); SystemClock.sleep(100);
            first.surface(null); first.stop(); first.stop();
            assertTrue("Core could not restart after cleanup",second.start(rom.getPath(),save.getPath(),rom.getParent()));
        } finally {
            first.stop(); second.stop(); frames.close(); rom.delete(); save.delete(); new File(save.getPath()+".tmp").delete();
        }
    }
    /** Use only the stable JNI entrypoints so this test also exercises an R8 release APK. */
    private final class NativeBridge {
        private final Class<?> bridge;
        private long handle;
        NativeBridge() throws Exception {
            bridge = Class.forName("com.sva.launcher.NestopiaSession", true,
                    getInstrumentation().getTargetContext().getClassLoader());
        }
        private Object call(String name, Class<?>[] types, Object... values) throws Exception {
            java.lang.reflect.Method method = bridge.getDeclaredMethod(name,types);
            method.setAccessible(true);
            return method.invoke(null,values);
        }
        boolean start(String rom,String save,String system) throws Exception {
            handle=(Long)call("nativeStart",new Class<?>[]{String.class,String.class,String.class},rom,save,system);
            return handle!=0;
        }
        void surface(android.view.Surface surface) throws Exception {
            if(handle!=0)call("nativeSurface",new Class<?>[]{long.class,android.view.Surface.class},handle,surface);
        }
        void pause(boolean paused) throws Exception {
            if(handle!=0)call("nativePause",new Class<?>[]{long.class,boolean.class},handle,paused);
        }
        void reset() throws Exception { if(handle!=0)call("nativeReset",new Class<?>[]{long.class},handle); }
        void stop() throws Exception {
            if(handle!=0) { call("nativeStop",new Class<?>[]{long.class},handle); handle=0; }
        }
    }
}
