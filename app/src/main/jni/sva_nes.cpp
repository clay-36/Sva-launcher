#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <aaudio/AAudio.h>
#include <dlfcn.h>
#include <libretro.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {
struct Core {
    void* library{};
    decltype(&retro_init) init{}; decltype(&retro_deinit) deinit{};
    decltype(&retro_set_environment) set_environment{}; decltype(&retro_set_video_refresh) set_video{};
    decltype(&retro_set_audio_sample) set_audio{}; decltype(&retro_set_audio_sample_batch) set_audio_batch{};
    decltype(&retro_set_input_poll) set_input_poll{}; decltype(&retro_set_input_state) set_input_state{};
    decltype(&retro_get_system_info) get_system_info{}; decltype(&retro_get_system_av_info) get_av_info{};
    decltype(&retro_load_game) load_game{}; decltype(&retro_unload_game) unload_game{};
    decltype(&retro_run) run{}; decltype(&retro_reset) reset{};
    decltype(&retro_get_memory_data) get_memory_data{}; decltype(&retro_get_memory_size) get_memory_size{};
};

struct Session;
Session* active = nullptr;

struct Session {
    Core core;
    std::string rom_path, save_path, system_path;
    std::vector<uint8_t> rom;
    std::thread worker;
    std::mutex mutex;
    std::condition_variable wake;
    std::atomic<bool> stopping{false}, paused{true}, reset_requested{false};
    std::atomic<uint32_t> input[2]{};
    ANativeWindow* window{};
    unsigned buffer_width{}, buffer_height{};
    AAudioStream* audio{};
    bool initialized = false, loaded = false;
    retro_pixel_format pixel_format = RETRO_PIXEL_FORMAT_RGB565;
    double fps = 60.0;

    ~Session() { stop(); }

    template<typename T> bool symbol(T& value, const char* name) {
        value = reinterpret_cast<T>(dlsym(core.library, name)); return value != nullptr;
    }

    bool openCore() {
        core.library = dlopen("libretro.so", RTLD_NOW | RTLD_LOCAL);
        bool ok = core.library && symbol(core.init,"retro_init") && symbol(core.deinit,"retro_deinit")
            && symbol(core.set_environment,"retro_set_environment") && symbol(core.set_video,"retro_set_video_refresh")
            && symbol(core.set_audio,"retro_set_audio_sample") && symbol(core.set_audio_batch,"retro_set_audio_sample_batch")
            && symbol(core.set_input_poll,"retro_set_input_poll") && symbol(core.set_input_state,"retro_set_input_state")
            && symbol(core.get_system_info,"retro_get_system_info") && symbol(core.get_av_info,"retro_get_system_av_info")
            && symbol(core.load_game,"retro_load_game") && symbol(core.unload_game,"retro_unload_game")
            && symbol(core.run,"retro_run") && symbol(core.reset,"retro_reset")
            && symbol(core.get_memory_data,"retro_get_memory_data") && symbol(core.get_memory_size,"retro_get_memory_size");
        if (!ok && core.library) { dlclose(core.library); core.library = nullptr; }
        return ok;
    }

    bool start() {
        std::ifstream file(rom_path, std::ios::binary);
        if (!file) return false;
        rom.assign(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
        if (rom.size() < 16 || std::memcmp(rom.data(), "NES\x1a", 4) != 0 || !openCore()) return false;
        active = this;
        core.set_environment(environment); core.set_video(video); core.set_audio(audioSample);
        core.set_audio_batch(audioBatch); core.set_input_poll(inputPoll); core.set_input_state(inputState);
        core.init(); initialized = true;
        retro_game_info info{rom_path.c_str(), rom.data(), rom.size(), nullptr};
        if (!core.load_game(&info)) { core.deinit(); initialized = false; active = nullptr; dlclose(core.library); core.library = nullptr; return false; }
        loaded = true;
        retro_system_av_info av{}; core.get_av_info(&av); if (av.timing.fps > 1.0) fps = av.timing.fps;
        loadSave(); openAudio(static_cast<int32_t>(av.timing.sample_rate > 1 ? av.timing.sample_rate : 48000));
        worker = std::thread([this]{ loop(); }); return true;
    }

    void loop() {
        auto next_save = std::chrono::steady_clock::now() + std::chrono::seconds(30);
        while (!stopping) {
            if (paused) {
                std::unique_lock<std::mutex> lock(mutex); wake.wait_for(lock, std::chrono::milliseconds(100), [this]{ return stopping || !paused; }); continue;
            }
            if (reset_requested.exchange(false)) core.reset();
            core.run();
            if (std::chrono::steady_clock::now() >= next_save) { save(); next_save = std::chrono::steady_clock::now() + std::chrono::seconds(30); }
        }
        save();
    }

    void openAudio(int32_t rate) {
        AAudioStreamBuilder* builder = nullptr; if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return;
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setChannelCount(builder, 2); AAudioStreamBuilder_setSampleRate(builder, rate);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_openStream(builder, &audio); AAudioStreamBuilder_delete(builder);
        if (audio) AAudioStream_requestStart(audio);
    }

    void loadSave() {
        size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM); void* memory = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
        if (!size || !memory) return; std::ifstream file(save_path, std::ios::binary); if (file) file.read(static_cast<char*>(memory), size);
    }
    void save() {
        size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM); void* memory = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
        if (!size || !memory) return; std::string temporary = save_path + ".tmp";
        { std::ofstream file(temporary, std::ios::binary | std::ios::trunc); if (!file) return; file.write(static_cast<char*>(memory), size); file.flush(); }
        std::rename(temporary.c_str(), save_path.c_str());
    }
    void setWindow(JNIEnv* env, jobject surface) {
        std::lock_guard<std::mutex> lock(mutex); if (window) ANativeWindow_release(window);
        window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
        buffer_width = buffer_height = 0;
    }
    void stop() {
        if (!core.library) return; stopping = true; paused = false; wake.notify_all(); if (worker.joinable()) worker.join();
        if (audio) { AAudioStream_requestStop(audio); AAudioStream_close(audio); audio = nullptr; }
        { std::lock_guard<std::mutex> lock(mutex); if (window) { ANativeWindow_release(window); window = nullptr; } }
        if (loaded) { core.unload_game(); loaded = false; }
        if (initialized) { core.deinit(); initialized = false; }
        if (active == this) active = nullptr; dlclose(core.library); core.library = nullptr;
    }

    static bool environment(unsigned command, void* data) {
        if (!active) return false;
        switch (command) {
            case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: active->pixel_format = *static_cast<retro_pixel_format*>(data); return true;
            case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: *static_cast<const char**>(data) = active->system_path.c_str(); return true;
            case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: *static_cast<const char**>(data) = active->system_path.c_str(); return true;
            case RETRO_ENVIRONMENT_GET_CAN_DUPE: *static_cast<bool*>(data) = true; return true;
            case RETRO_ENVIRONMENT_GET_LANGUAGE: *static_cast<unsigned*>(data) = RETRO_LANGUAGE_ENGLISH; return true;
            case RETRO_ENVIRONMENT_SET_VARIABLES: case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
            case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS: return true;
            case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: *static_cast<bool*>(data) = false; return true;
            // Returning true promises the core that value points to a valid
            // option string. Sva currently uses every Nestopia default, so an
            // unknown option must be reported as unavailable instead. Nestopia
            // legitimately calls strcmp() when this returns true.
            case RETRO_ENVIRONMENT_GET_VARIABLE: static_cast<retro_variable*>(data)->value = nullptr; return false;
            default: return false;
        }
    }
    static void video(const void* data, unsigned width, unsigned height, size_t pitch) {
        if (!active || !data) return; std::lock_guard<std::mutex> lock(active->mutex); if (!active->window) return;
        // Keep the producer buffer at the core's native resolution. SurfaceView
        // and SurfaceFlinger scale this buffer to the centered 4:3 view using
        // the device's graphics hardware. Scaling to the full TV resolution
        // here consumed millions of CPU operations on every emulated frame.
        if (active->buffer_width != width || active->buffer_height != height) {
            if (ANativeWindow_setBuffersGeometry(active->window, static_cast<int32_t>(width),
                    static_cast<int32_t>(height), WINDOW_FORMAT_RGBX_8888) != 0) return;
            active->buffer_width = width; active->buffer_height = height;
        }
        ANativeWindow_Buffer target{}; if (ANativeWindow_lock(active->window, &target, nullptr) != 0) return;
        auto* out = static_cast<uint32_t*>(target.bits);
        unsigned copy_width = std::min(width, static_cast<unsigned>(target.width));
        unsigned copy_height = std::min(height, static_cast<unsigned>(target.height));
        for (unsigned y = 0; y < copy_height; ++y) {
            const uint8_t* source_row = static_cast<const uint8_t*>(data) + y * pitch;
            uint32_t* target_row = out + y * target.stride;
            if (active->pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
                std::memcpy(target_row, source_row, copy_width * sizeof(uint32_t));
            } else {
                const uint16_t* source = reinterpret_cast<const uint16_t*>(source_row);
                for (unsigned x = 0; x < copy_width; ++x) {
                    uint16_t p = source[x]; uint32_t color;
                    if (active->pixel_format == RETRO_PIXEL_FORMAT_RGB565) color = 0xff000000u | ((p & 0xf800u) << 8) | ((p & 0x07e0u) << 5) | ((p & 0x001fu) << 3);
                    else color = 0xff000000u | ((p & 0x7c00u) << 9) | ((p & 0x03e0u) << 6) | ((p & 0x001fu) << 3);
                    target_row[x] = color;
                }
            }
        }
        ANativeWindow_unlockAndPost(active->window);
    }
    static void audioSample(int16_t left, int16_t right) { int16_t pair[]{left,right}; audioBatch(pair,1); }
    static size_t audioBatch(const int16_t* data, size_t frames) {
        if (active && active->audio && frames) { aaudio_result_t written = AAudioStream_write(active->audio, data, static_cast<int32_t>(frames), 100000000); return written > 0 ? static_cast<size_t>(written) : 0; }
        return frames;
    }
    static void inputPoll() {}
    static int16_t inputState(unsigned port, unsigned device, unsigned, unsigned id) {
        if (!active || device != RETRO_DEVICE_JOYPAD || port > 1 || id > 31) return 0;
        return (active->input[port].load() & (1u << id)) ? 1 : 0;
    }
};

std::string stringFrom(JNIEnv* env, jstring value) { const char* chars = env->GetStringUTFChars(value, nullptr); std::string result(chars ? chars : ""); if (chars) env->ReleaseStringUTFChars(value, chars); return result; }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_deo_launcher_NestopiaSession_nativeStart(JNIEnv* env, jclass, jstring rom, jstring save, jstring system) {
    auto* session = new Session(); session->rom_path = stringFrom(env, rom); session->save_path = stringFrom(env, save); session->system_path = stringFrom(env, system);
    if (!session->start()) { delete session; return 0; } return reinterpret_cast<jlong>(session);
}
extern "C" JNIEXPORT void JNICALL Java_com_deo_launcher_NestopiaSession_nativeSurface(JNIEnv* env, jclass, jlong handle, jobject surface) { reinterpret_cast<Session*>(handle)->setWindow(env, surface); }
extern "C" JNIEXPORT void JNICALL Java_com_deo_launcher_NestopiaSession_nativeInput(JNIEnv*, jclass, jlong handle, jint port, jint mask) { if (port >= 0 && port < 2) reinterpret_cast<Session*>(handle)->input[port] = static_cast<uint32_t>(mask); }
extern "C" JNIEXPORT void JNICALL Java_com_deo_launcher_NestopiaSession_nativePause(JNIEnv*, jclass, jlong handle, jboolean paused) { auto* s = reinterpret_cast<Session*>(handle); s->paused = paused; if (!paused) s->wake.notify_all(); else s->save(); }
extern "C" JNIEXPORT void JNICALL Java_com_deo_launcher_NestopiaSession_nativeReset(JNIEnv*, jclass, jlong handle) { reinterpret_cast<Session*>(handle)->reset_requested = true; }
extern "C" JNIEXPORT void JNICALL Java_com_deo_launcher_NestopiaSession_nativeStop(JNIEnv*, jclass, jlong handle) { auto* session = reinterpret_cast<Session*>(handle); session->stop(); delete session; }
