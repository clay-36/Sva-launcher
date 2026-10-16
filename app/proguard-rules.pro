# JNI resolves these class and method names through Java_com_sva_launcher_* symbols.
-keep,includedescriptorclasses class com.sva.launcher.NestopiaSession {
    native <methods>;
}
