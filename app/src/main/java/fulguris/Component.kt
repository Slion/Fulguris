package fulguris


import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * Fulguris component
 */
abstract class Component : DefaultLifecycleObserver  /*: androidx.lifecycle.ViewModel()*/ {

    // Setup an async scope on the main/UI thread dispatcher.
    // This one as opposed to viwModelScope will not be cancelled therefore all operation will complete before the process quits.
    // Use this if you want to manipulate views and other object bound to the UI thread.
    val iScopeMainThread = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Only use this for operations that do not need the UI thread as they will run on another thread.
    // Typically used for file write or read operations.
    val iScopeThreadPool = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
class CloseableCoroutineScope(context: CoroutineContext) : Closeable, CoroutineScope {
    override val coroutineContext: CoroutineContext = context

    override fun close() {
        coroutineContext.cancel()
    }
}*/

    /*
    override fun onCleared() {
        super.onCleared()
        // This was not working here cause that callback comes after viewModelScope and all its jobs are cancelled
//        runBlocking {
//            viewModelScope.join()
//        }

        // Make sure all async operations from our ViewModels are completed before we quit.
        // This is needed as the ViewModel default behaviour is to cancel all outstanding operations.
        // That should make sure sessions are always saved properly.
        //runBlocking {
            //ioScope.job()?.join()
        //}


        //ioScope.cancel()
    }

 */

}

