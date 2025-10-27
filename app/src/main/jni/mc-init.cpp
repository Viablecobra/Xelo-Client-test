#include <jni.h>
#include <string>
#include <dlfcn.h>

#include <android/native_activity.h>

static void (*android_main_minecraft)(struct android_app *app);
static void (*ANativeActivity_onCreate_minecraft)(ANativeActivity *activity, void *savedState, size_t savedStateSize);

extern "C" void android_main(struct android_app *app) {
    android_main_minecraft(app);
}

extern "C" void ANativeActivity_onCreate(ANativeActivity *activity, void *savedState, size_t savedStateSize) {
    ANativeActivity_onCreate_minecraft(activity, savedState, savedStateSize);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    void *handle = dlopen("libminecraftpe.so", RTLD_LAZY);
    android_main_minecraft = (void (*)(struct android_app *)) (dlsym(handle, "android_main"));
    ANativeActivity_onCreate_minecraft = (void (*)(ANativeActivity *, void *, size_t)) (dlsym(handle, "ANativeActivity_onCreate"));
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL Java_org_levimc_launcher_core_minecraft_pesdk_nativeapi_LibraryLoader_nativeOnLauncherLoaded(JNIEnv*env, jclass thiz, jstring libPath) {
    const char * mNativeLibPath = env->GetStringUTFChars(libPath, 0);
    void* imageMCPE=(void*) dlopen(mNativeLibPath,RTLD_LAZY);
    dlclose(imageMCPE);
    env->ReleaseStringUTFChars(libPath,mNativeLibPath);
}
