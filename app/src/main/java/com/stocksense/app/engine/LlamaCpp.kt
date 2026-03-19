package com.stocksense.app.engine

/**
 * JNI bridge to llama.cpp native library.
 *
 * To build:
 *  1. Clone llama.cpp into app/src/main/cpp/llama.cpp
 *  2. Add CMakeLists.txt that builds libllama.so and the JNI wrapper
 *  3. Uncomment the externalNativeBuild block in app/build.gradle
 *
 * The stubs below allow the app to compile and run without the native library
 * (LLMInsightEngine gracefully falls back to templates when the lib is absent).
 */
object LlamaCpp {

    /**
     * Load a GGUF model from [path].
     * @param nGpuLayers number of layers to offload to GPU (0 = CPU only).
     * @return opaque model handle (pointer cast to Long).
     */
    @JvmStatic
    external fun loadModel(path: String, nGpuLayers: Int): Long

    /**
     * Create an inference context for [modelHandle].
     * @param contextSize token context window size.
     * @return opaque context handle.
     */
    @JvmStatic
    external fun createContext(modelHandle: Long, contextSize: Int): Long

    /**
     * Run text generation on [contextHandle] with the given [prompt].
     * @param maxTokens maximum number of tokens to generate.
     * @return generated text.
     */
    @JvmStatic
    external fun runInference(contextHandle: Long, prompt: String, maxTokens: Int): String

    /** Free a context created by [createContext]. */
    @JvmStatic
    external fun freeContext(contextHandle: Long)

    /** Free a model loaded by [loadModel]. */
    @JvmStatic
    external fun freeModel(modelHandle: Long)
}
