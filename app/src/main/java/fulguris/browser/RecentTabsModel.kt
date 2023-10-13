package fulguris.browser

import fulguris.extensions.popIfNotEmpty
import android.os.Bundle
import java.util.*

/**
 * A model that saves [Bundle] and returns the last returned one.
 */
class RecentTabsModel {

    public val bundleStack: Stack<Bundle> = Stack()

    /**
     * Return the last closed tab as a [Bundle] or null if there is no previously opened tab.
     * Removes the [Bundle] from the queue after returning it.
     */
    fun popLast(): Bundle? = bundleStack.popIfNotEmpty()

    /**
     * Add the [savedBundle] to the queue. The next call to [popLast] will return this [Bundle].
     */
    fun add(savedBundle: Bundle) = bundleStack.add(savedBundle)

}
