package fulguris.utils

import timber.log.Timber
import java.io.IOException


/**
 * We used that to transition after our major refactoring moved all classes to fulguris package.
 *
 *  See: https://analyzejava.wordpress.com/2014/09/25/java-classloader-write-your-own-classloader/
 */
class ClassLoaderCompat(parent: ClassLoader) :
    ClassLoader(parent) {

    /**
     * Every request for a class passes through this method.
     * If the requested class is in "javablogging" package,
     * it will load it using the
     * [CustomClassLoader.getClass] method.
     * If not, it will use the super.loadClass() method
     * which in turn will pass the request to the parent.
     *
     * @param name
     * Full class name
     */
    /*
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*>? {
        Timber.i("loadClass: $name")
        //println("loading class '$name'")
        return if (name == "acr.browser.lightning.browser.sessions.Session") {
            Timber.i("loadClass: Use new Session class")
            fulguris.browser.sessions.Session::class.java
        } else {
            super.loadClass(name)
        }
    }
    */

    /**
     *
     */
    /*
    override fun findClass(name: String?): Class<*> {
        Timber.i("findClass: $name")
        //println("loading class '$name'")
        return if (name == "acr.browser.lightning.browser.sessions.Session") {
            Timber.i("findClass: Use new Session class")
            fulguris.browser.sessions.Session.javaClass
        } else {
            super.findClass(name)
        }
    }
    */

}