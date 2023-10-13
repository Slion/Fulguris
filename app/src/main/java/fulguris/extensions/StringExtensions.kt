package fulguris.extensions

val String.reverseDomainName get() = split('.').reversed().joinToString(".")