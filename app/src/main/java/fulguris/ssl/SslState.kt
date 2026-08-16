package fulguris.ssl

/**
 * Representing the SSL state of the browser.
 */
sealed class SslState {

    /**
     * No SSL.
     */
    object None : SslState()

    /**
     * Valid SSL connection.
     */
    object Valid : SslState()

    /**
     * Plain HTTP (no encryption).
     */
    object Insecure : SslState()

    /**
     * Broken SSL connection.
     *
     * @param sslError The error that is causing the invalid SSL state.
     */
    object Invalid : SslState()

}
