package fulguris.utils

import timber.log.Timber


/**
 *
 */
class Observers<T> {

    fun interface Broadcaster<T> {
        fun broadcast(aObserver: T)
    }

    /**
     * Our collection of observers
     */
    private var observers = ArrayList<T>()

    /**
     * Register a property observer.
     */
    fun add(aObserver: T) {
        observers.add(aObserver)
        Timber.d("broadcast add count: ${observers.size}")
    }

    /**
     * Unregister a property observer
     */
    fun remove(aObserver: T) {
        observers.remove(aObserver)
        Timber.d("broadcast remove count: ${observers.size}")
    }

    /**
     *
     */
    fun broadcast(aAction: Broadcaster<T>) {
        Timber.d("broadcast observers count: ${observers.size}")
        observers.forEach {
            Timber.d("broadcast $it")
            aAction.broadcast(it)
        }
    }

}