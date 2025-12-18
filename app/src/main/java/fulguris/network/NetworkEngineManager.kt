/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 */

package fulguris.network

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager that discovers and manages available network engine implementations.
 */
@Singleton
class NetworkEngineManager @Inject constructor() {

    private val availableEngines = mutableMapOf<String, NetworkEngine>()
    private var currentEngine: NetworkEngine? = null

    init {
        discoverEngines()
    }

    /**
     * Discover all NetworkEngine implementations.
     * Manually registers known implementations.
     * In the future, could use reflection or annotation processing for automatic discovery.
     */
    private fun discoverEngines() {
        registerEngine(NetworkEngineWebView())
        registerEngine(NetworkEngineOkHttp())
        registerEngine(NetworkEngineHttpUrlConnection())

        Timber.i("Discovered ${availableEngines.size} network engines: ${availableEngines.keys}")
    }

    /**
     * Register a network engine implementation.
     */
    private fun registerEngine(engine: NetworkEngine) {
        availableEngines[engine.id] = engine
        Timber.d("Registered network engine: ${engine.displayName} (${engine.id})")
    }

    /**
     * Get all available network engine implementations.
     * @return List of pairs (id, displayName)
     */
    fun getAvailableEngines(): List<Pair<String, String>> {
        return availableEngines.map { (id, engine) ->
            id to engine.displayName
        }.sortedBy { it.second }
    }

    /**
     * Get the currently selected network engine.
     */
    fun getCurrentEngine(): NetworkEngine? = currentEngine

    /**
     * Select a network engine by its ID.
     * @param engineId The ID of the engine to select
     * @return true if the engine was found and selected, false otherwise
     */
    fun selectEngine(engineId: String): Boolean {
        val newEngine = availableEngines[engineId]

        if (newEngine == null) {
            Timber.w("Network engine not found: $engineId")
            return false
        }

        // Deselect current engine
        currentEngine?.onDeselected()

        // Select new engine
        currentEngine = newEngine
        currentEngine?.onSelected()

        Timber.i("Selected network engine: ${newEngine.displayName} (${newEngine.id})")
        return true
    }

    /**
     * Get a network engine by its ID.
     */
    fun getEngine(engineId: String): NetworkEngine? {
        return availableEngines[engineId]
    }

    companion object {
        const val DEFAULT_ENGINE_ID = "webview"
    }
}

