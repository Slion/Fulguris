package fulguris.database.adblock

import fulguris.adblock.UnifiedFilterResponse
import fulguris.database.adblock.UserRulesRepository.Companion.RESPONSE_BLOCK
import fulguris.database.adblock.UserRulesRepository.Companion.RESPONSE_EXCLUSION
import fulguris.database.adblock.UserRulesRepository.Companion.RESPONSE_NOOP
import fulguris.database.databaseDelegate
import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class UserRulesDatabase @Inject constructor(
    application: Application
) : SQLiteOpenHelper(application, DATABASE_NAME, null, DATABASE_VERSION),
    UserRulesRepository {

    private val database: SQLiteDatabase by databaseDelegate()


    // TODO: simplify? actually tag is always the same as pattern in the user rules...

    // Creating Tables
    override fun onCreate(db: SQLiteDatabase) {
        // create only one table, not necessary to have many for different lists, or exclusions
        val createRulesTable = "CREATE TABLE ${DatabaseUtils.sqlEscapeString(TABLE_RULES)}(" +
                "${DatabaseUtils.sqlEscapeString(KEY_ID)} INTEGER PRIMARY KEY autoincrement," +
                // filter info: list/entity, tag, exclusion
                //"${DatabaseUtils.sqlEscapeString(KEY_LIST)} INTEGER," +
                //"${DatabaseUtils.sqlEscapeString(KEY_TAG)} TEXT not null," +
                "${DatabaseUtils.sqlEscapeString(KEY_RESPONSE)} INTEGER," + // block: 1, allow: -1, noop: 0
                // stuff for UnifiedFilter: pattern, filterType, contentType, ignoreCase, isRegex, thirdParty, domainMap
                // using blob like the filter writer/reader is horribly slow for some reason
                "${DatabaseUtils.sqlEscapeString(KEY_PATTERN)} TEXT not null," +
                "${DatabaseUtils.sqlEscapeString(KEY_FILTER_TYPE)} INTEGER," +
                "${DatabaseUtils.sqlEscapeString(KEY_CONTENT_TYPE)} INTEGER," +
                //"${DatabaseUtils.sqlEscapeString(KEY_IGNORE_CASE)} INTEGER," + it's always false for user filters anyway
                "${DatabaseUtils.sqlEscapeString(KEY_THIRD_PARTY)} INTEGER," +
                "${DatabaseUtils.sqlEscapeString(KEY_DOMAIN_MAP)} TEXT not null" +
                ')'
        db.execSQL(createRulesTable)
    }

    // Upgrading database
    // if this is ever necessary, at least user rules should be preserved!
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop older table if it exists
        db.execSQL("DROP TABLE IF EXISTS ${DatabaseUtils.sqlEscapeString(TABLE_RULES)}")
        // Create tables again
        onCreate(db)
    }

    override fun addRules(rules: List<UnifiedFilterResponse>){
        database.apply {
            beginTransaction()

            for (item in rules) {
                // check filter type and complain if it's not a type that can be read?
                insert(TABLE_RULES, null, item.toContentValues())
            }

            setTransactionSuccessful()
            endTransaction()
        }
    }

    private fun UnifiedFilterResponse.toContentValues() = ContentValues(10).apply {
        put(fulguris.database.adblock.UserRulesDatabase.KEY_RESPONSE, response.toInt())
        put(fulguris.database.adblock.UserRulesDatabase.KEY_PATTERN, filter.pattern)
        put(fulguris.database.adblock.UserRulesDatabase.KEY_FILTER_TYPE, filter.filterType)
        put(fulguris.database.adblock.UserRulesDatabase.KEY_CONTENT_TYPE, filter.contentType)
        put(fulguris.database.adblock.UserRulesDatabase.KEY_THIRD_PARTY, filter.thirdParty)
        put(fulguris.database.adblock.UserRulesDatabase.KEY_DOMAIN_MAP, filter.domains?.toDBString() ?: "")
    }

    fun Boolean?.toInt(): Int {
        return when {
            this == false -> -1
            this == true -> 1
            else -> 0
        }
    }

    override fun removeAllRules() {
        database.run {
            delete(TABLE_RULES, null, null)
            close()
        }
    }

    override fun removeRule(rule: UnifiedFilterResponse) {
        database.run {
            delete(
                TABLE_RULES,
                "$KEY_RESPONSE = ? AND $KEY_PATTERN = ? AND $KEY_DOMAIN_MAP = ? AND $KEY_FILTER_TYPE = ? AND $KEY_CONTENT_TYPE = ? AND $KEY_THIRD_PARTY = ?",
                arrayOf(rule.response.toInt().toString(), rule.filter.pattern, rule.filter.domains?.toDBString() ?: "", rule.filter.filterType.toString(), rule.filter.contentType.toString(), rule.filter.thirdParty.toString()))
        }
    }

    // page in this context: what is in blocker as pageUrl.host
    // to be used for uBo style page settings, allows users to block/allow/noop requests to specific domains when on this page
    //  (actually could be more powerful than that, could be used for something to create something like uMatrix)
    // TODO: is this actually necessary? userRules need to be in UserFilterContainer anyway, so this should actually never be called
/*    override fun getRulesForPage(page: String): List<UnifiedFilterResponse> {
        val cursor = database.query(
            TABLE_RULES,
            arrayOf(KEY_PATTERN, KEY_FILTER_TYPE, KEY_CONTENT_TYPE, KEY_THIRD_PARTY, KEY_DOMAIN_MAP, KEY_RESPONSE),
            "$KEY_DOMAIN_MAP = ?",
            arrayOf(page),
            null,
            null,
            null,
            null
        )
        val rules = mutableListOf<UnifiedFilterResponse>()
        while (cursor.moveToNext()) {
            getFilterResponse(cursor)?.let { rules.add(it) }
        }
        cursor.close()
        return rules
    }
*/

    // TODO: as sequence would probably be better
    //  tested: not faster -> any reason to switch
    override fun getAllRules(): List<UnifiedFilterResponse> {
        val cursor = database.query(
            TABLE_RULES,
            arrayOf(KEY_PATTERN, KEY_FILTER_TYPE, KEY_CONTENT_TYPE, KEY_THIRD_PARTY, KEY_DOMAIN_MAP, KEY_RESPONSE),
            null,
            null,
            null,
            null,
            null,
            null
        )
        val rules = mutableListOf<UnifiedFilterResponse>()
        while (cursor.moveToNext()) {
            getFilterResponse(cursor)?.let { rules.add(it) }
        }
        cursor.close()
        return rules
    }

    private fun getFilterResponse(cursor: Cursor): UnifiedFilterResponse? {
        val response = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_RESPONSE)).toResponse()
        val pattern = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATTERN))
        val filterType = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_FILTER_TYPE))
        val contentType = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CONTENT_TYPE))
        val ignoreCase = true
        val thirdParty = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_THIRD_PARTY))
        val domains = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DOMAIN_MAP)).toDomainMap()


        val filter = when (filterType) { // only recognize filter types that are used in user rules
            FILTER_TYPE_CONTAINS -> ContainsFilter(pattern, contentType, ignoreCase, domains, thirdParty)
            FILTER_TYPE_HOST -> HostFilter(pattern, contentType, domains, thirdParty)
            else -> return null // should not happen -> error message?
        }
        return UnifiedFilterResponse(filter, response)
    }

    private fun Int.toResponse() = when {
        this == RESPONSE_BLOCK -> true
        this == RESPONSE_EXCLUSION -> false
        this == RESPONSE_NOOP -> null
        else -> null // should not happen
    }

    private fun DomainMap.toDBString(): String {
        // disable full domainMap support, user rules only have null or SingleDomainMap with include = true
/*        var string = getKey(0) + "/" + (if (getValue(0)) "1" else "0")
        for (i in 1 until size) {
            string += "//" + getKey(i) + "/" + (if (getValue(i)) "1" else "0")
        }
        return string*/

        // TODO: actually there should be an error thrown, also if getValue(0) != 1
        return if (size == 1) getKey(0) else ""
    }

    private fun String.toDomainMap(): DomainMap? {
        if (isEmpty()) return null
        // disable full domainMap support, user rules only have null or SingleDomainMap with include = true
        return SingleDomainMap(true, this)
/*        val mapEntries = split("//")
        when {
            mapEntries.size == 1 -> {
                val pair = mapEntries.first().split("/")
                if (pair.size != 2) return null
                return (SingleDomainMap(pair[1] == "1", pair[0]))
            }
            mapEntries.size > 1 -> {
                val domainMap = ArrayDomainMap(mapEntries.size)
                for (entry in mapEntries) {
                    val pair = entry.split("/")
                    if (pair.size != 2) continue
                    domainMap[pair[0]] = pair[1] == "1"
                    if (pair[1] == "1")
                        domainMap.include = true
                }
                return domainMap
            }
            else -> return null
        }*/
    }

    companion object {

        // Database version
        private const val DATABASE_VERSION = 1

        // Database name
        private const val DATABASE_NAME = "rulesDatabase"

        // Host table name
        private const val TABLE_RULES = "rules"

        // Host table columns names
        // ignoreCase not necessary for user rules because user rules are hostFilter or containsFilter, none use ignoreCase
        // tag not necessary because user rules because user rules use pattern (pageDomain or empty) like a tag
        private const val KEY_ID = "id"
        private const val KEY_RESPONSE = "response"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_CONTENT_TYPE = "content_type"
        private const val KEY_THIRD_PARTY = "third_party"
        private const val KEY_DOMAIN_MAP = "domain_map"

    }

}
