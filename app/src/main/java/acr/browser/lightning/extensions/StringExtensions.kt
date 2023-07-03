package acr.browser.lightning.extensions

val String.reverseDomainName get() = split('.').reversed().joinToString(".")