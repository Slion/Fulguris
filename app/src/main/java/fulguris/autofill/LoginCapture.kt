package fulguris.autofill

import android.webkit.WebView

/**
 * Notices when the user submits a login form so the browser can offer to save the password.
 *
 * The script reports (username, password) to [fulguris.view.WebViewEx.LoginCaptureBridge], which only
 * ever receives data, nothing saved is ever handed back to a page. The host is not taken from the page
 * script either: the browser uses the address of the tab itself.
 */
object LoginCapture {

    const val JS_INTERFACE = "_fulgurisLogin"

    private const val JS = """
        (function() {
            if (window.__fcLoginCapture) return;
            window.__fcLoginCapture = true;
            var SKIP = /show|hide|toggle|eye|visib|reveal|показ|скры/i;
            function visible(el) {
                return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
            }
            function grab(root) {
                var scope = root && root.querySelectorAll ? root : document;
                var passes = scope.querySelectorAll('input[type=password]');
                for (var i = 0; i < passes.length; i++) {
                    var pass = passes[i];
                    if (!pass.value || !visible(pass)) continue;
                    var form = pass.form || scope;
                    var cands = form.querySelectorAll('input[type=email], input[type=text], input[type=tel], input:not([type])');
                    var user = '';
                    for (var j = 0; j < cands.length; j++) {
                        // Closest field before the password field wins
                        if (cands[j].value && (cands[j].compareDocumentPosition(pass) & Node.DOCUMENT_POSITION_FOLLOWING)) {
                            user = cands[j].value;
                        }
                    }
                    if (user) return [user, pass.value];
                }
                return null;
            }
            function report(root) {
                try {
                    var r = grab(root);
                    if (r) window.$JS_INTERFACE.onSubmit(r[0], r[1]);
                } catch (e) {}
            }
            document.addEventListener('submit', function(e) { report(e.target); }, true);
            document.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' && e.target && e.target.type === 'password') report(e.target.form || document);
            }, true);
            document.addEventListener('click', function(e) {
                var b = e.target && e.target.closest ? e.target.closest('button, input[type=submit], input[type=button], [role=button]') : null;
                if (!b) return;
                var label = (b.getAttribute('aria-label') || '') + ' ' + (b.className || '') + ' ' + (b.id || '') + ' ' + (b.title || '');
                // Skip the "show password" eye and similar
                if (SKIP.test(label)) return;
                report(b.form || document);
            }, true);
        })();
    """

    fun inject(view: WebView) {
        view.evaluateJavascript(JS, null)
    }
}
