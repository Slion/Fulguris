package fulguris.adblock

abstract class BlockerResponse

data class BlockResponse(val blockList: String, val pattern: String): BlockerResponse()

data class ModifyResponse(val url: String, val requestMethod: String, val requestHeaders: Map<String, String>, val addResponseHeaders: Map<String,String>?, val removeResponseHeaders: Collection<String>?): BlockerResponse()

class BlockResourceResponse(resource: String): BlockerResponse() {
    val filename = when (resource) {
        RES_EMPTY -> RES_EMPTY // first because used for normal block and thus frequent
        RES_1X1, "1x1-transparent.gif" -> RES_1X1
        RES_2X2, "2x2-transparent.png" -> RES_2X2
        RES_3X2, "3x2-transparent.png" -> RES_3X2
        RES_32X32, "32x32-transparent.png" -> RES_32X32
        RES_ADDTHIS, "addthis.com/addthis_widget.js" -> RES_ADDTHIS
        RES_AMAZON_ADS, "amazon-adsystem.com/aax2/amzn_ads.js" -> RES_AMAZON_ADS
        RES_CHARTBEAT, "static.chartbeat.com/chartbeat.js" -> RES_CHARTBEAT
        RES_CLICK2LOAD, "aliasURL", "url" -> RES_CLICK2LOAD
        RES_DOUBLECLICK, "doubleclick.net/instream/ad_status.js" -> RES_DOUBLECLICK
        RES_ANALYTICS, "google-analytics.com/analytics.js", "googletagmanager_gtm.js", "googletagmanager.com/gtm.js" -> RES_ANALYTICS
        RES_ANALYTICS_CX, "google-analytics.com/cx/api.js" -> RES_ANALYTICS_CX
        RES_ANALYTICS_GA, "google-analytics.com/ga.js" -> RES_ANALYTICS_GA
        RES_ANALYTICS_INPAGE, "google-analytics.com/inpage_linkid.js" -> RES_ANALYTICS_INPAGE
        RES_ADSBYGOOGLE, "googlesyndication.com/adsbygoogle.js" -> RES_ADSBYGOOGLE
        RES_GOOGLETAGSERVICES, "googletagservices.com/gpt.js" -> RES_GOOGLETAGSERVICES
        RES_LIGATUS, "ligatus.com/*/angular-tag.js" -> RES_LIGATUS
        RES_MONEYBROKER, "d3pkae9owd2lcf.cloudfront.net/mb105.js" -> RES_MONEYBROKER
        RES_NOEVAL_SILENT, "silent-noeval.js'" -> RES_NOEVAL_SILENT
        RES_NOFAB, "fuckadblock.js-3.2.0" -> RES_NOFAB
        RES_NOOP_MP3, "noopmp3-0.1s", "abp-resource:blank-mp3" -> RES_NOOP_MP3
        RES_NOOP_MP4, "noopmp4-1s" -> RES_NOOP_MP4
        RES_NOOP_HTML, "noopframe" -> RES_NOOP_HTML
        RES_NOOP_JS, "noopjs", "abp-resource:blank-js" -> RES_NOOP_JS
        RES_NOOP_TXT, "nooptext" -> RES_NOOP_TXT
        RES_NOOP_VMAP, "noopvmap-1." -> RES_NOOP_VMAP
        RES_OUTBRAIN, "widgets.outbrain.com/outbrain.js" -> RES_OUTBRAIN
        RES_POPADS, "popads.net.js" -> RES_POPADS
        RES_SCORECARD, "scorecardresearch.com/beacon.js" -> RES_SCORECARD
        RES_WINDOW_OPEN_DEFUSER, "nowoif.js" -> RES_WINDOW_OPEN_DEFUSER
        RES_HD_MAIN, RES_MXPNL, RES_NOEVAL, RES_NOBAB_2, RES_POPADS_DUMMY, RES_FINGERPRINT_2, RES_AMAZON_APSTAG, RES_AMPPROJECT -> resource // no alias -> keep name

        else -> RES_EMPTY // might happen if new block resources are added to uBo, or if there is a type in the list
    }

}

const val RES_EMPTY = "empty"
const val RES_1X1 = "1x1.gif"
const val RES_2X2 = "2x2.png"
const val RES_3X2 = "3x2.png"
const val RES_32X32 = "32x32.png"
const val RES_ADDTHIS = "addthis_widget.js"
const val RES_AMAZON_ADS = "amazon_ads.js"
const val RES_AMAZON_APSTAG = "amazon_apstag.js"
const val RES_AMPPROJECT = "ampproject_v0.js"
const val RES_CHARTBEAT = "chartbeat.js"
const val RES_CLICK2LOAD = "click2load.html"
const val RES_DOUBLECLICK = "doubleclick_instream_ad_status.js"
const val RES_FINGERPRINT_2 = "fingerprint2.js"
const val RES_ANALYTICS = "google-analytics_analytics.js"
const val RES_ANALYTICS_CX = "google-analytics_cx_api.js"
const val RES_ANALYTICS_GA = "google-analytics_ga.js"
const val RES_ANALYTICS_INPAGE = "google-analytics_inpage_linkid.js"
const val RES_ADSBYGOOGLE = "googlesyndication_adsbygoogle.js"
const val RES_GOOGLETAGSERVICES = "googletagservices_gpt.js"
const val RES_HD_MAIN ="hd-main.js"
const val RES_LIGATUS = "ligatus_angular-tag.js"
const val RES_MXPNL = "mxpnl_mixpanel.js"
const val RES_MONEYBROKER = "monkeybroker.js"
const val RES_NOEVAL = "noeval.js"
const val RES_NOEVAL_SILENT = "noeval-silent.js"
const val RES_NOBAB_2 = "nobab2.js"
const val RES_NOFAB = "nofab.js"
const val RES_NOOP_MP3 = "noop-0.1s.mp3"
const val RES_NOOP_MP4 = "noop-1s.mp4"
const val RES_NOOP_HTML = "noop.html"
const val RES_NOOP_JS = "noop.js"
const val RES_NOOP_TXT = "noop.txt"
const val RES_NOOP_VMAP = "noop-vmap1.0.xml"
const val RES_OUTBRAIN = "outbrain-widget.js"
const val RES_POPADS = "popads.js"
const val RES_POPADS_DUMMY = "popads-dummy.js"
const val RES_SCORECARD = "scorecardresearch_beacon.js"
const val RES_WINDOW_OPEN_DEFUSER = "window.open-defuser.js"
