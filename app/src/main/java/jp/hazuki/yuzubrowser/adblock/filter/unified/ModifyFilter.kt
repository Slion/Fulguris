package jp.hazuki.yuzubrowser.adblock.filter.unified

import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_CSP
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_REDIRECT
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_REMOVEPARAM

// this is NOT a unified filter, it's an optional part of it
abstract class ModifyFilter(val parameter: String?, val inverse: Boolean) {
    abstract val prefix: Char
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        other as ModifyFilter
        if (prefix != other.prefix) return false
        if (parameter != other.parameter) return false
        return inverse == other.inverse
    }

    override fun hashCode(): Int {
        return (parameter + prefix + inverse).hashCode()
    }
}

open class RemoveparamFilter(parameter: String?, inverse: Boolean): ModifyFilter(parameter, inverse) {
    override val prefix = MODIFY_PREFIX_REMOVEPARAM
}
class RemoveparamRegexFilter(parameter: String?, inverse: Boolean): RemoveparamFilter(parameter, inverse)

class RedirectFilter(parameter: String?): ModifyFilter(parameter, false) {
    override val prefix = MODIFY_PREFIX_REDIRECT

}
class CspFilter(parameter: String?): ModifyFilter(parameter, false) { // TODO: what is this?
    override val prefix = MODIFY_PREFIX_CSP
}

fun getRemoveparamFilter(parameter: String, inverse: Boolean) =
    if (parameter.startsWith('/'))
        RemoveparamRegexFilter(parameter, inverse)
    else
        RemoveparamFilter(parameter, inverse)
