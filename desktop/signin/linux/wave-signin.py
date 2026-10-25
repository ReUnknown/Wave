#!/usr/bin/env python3
# Wave's Google sign-in window on Linux, on WebKitGTK (part of the GNOME runtime; nothing downloaded).
# Google's sign-in page runs here; once it lands on music.youtube.com signed in, this prints the
# session (cookies and page config) as one line of JSON to Wave and quits.
#   wave-signin [url]        sign in (or open a channel-switch URL)
#   wave-signin --sign-out   forget the web session
import json, os, shutil, sys
import gi
gi.require_version("Gtk", "3.0")
gi.require_version("WebKit2", "4.1")
from gi.repository import GLib, Gtk, WebKit2

SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
DATA = os.path.join(GLib.get_user_data_dir(), "wave", "signin")

if len(sys.argv) > 1 and sys.argv[1] == "--sign-out":
    shutil.rmtree(DATA, ignore_errors=True)
    sys.exit(0)

manager = WebKit2.WebsiteDataManager(base_data_directory=DATA, base_cache_directory=os.path.join(DATA, "cache"))
context = WebKit2.WebContext.new_with_website_data_manager(manager)
manager.get_cookie_manager().set_persistent_storage(os.path.join(DATA, "cookies.sqlite"), WebKit2.CookiePersistentStorage.SQLITE)
web = WebKit2.WebView.new_with_context(context)
window = Gtk.Window(title="Sign in to Wave")
window.set_default_size(480, 680)
window.add(web)
state = {"done": False, "attempt": 0}
window.connect("destroy", lambda *_: sys.exit(0 if state["done"] else 1))

PAGE_JS = "(function(){var c=(window.yt&&window.yt.config_)||{};return JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',a:String(c.SESSION_INDEX||0)});})()"

def read():
    def got_js(view, result):
        try:
            page = json.loads(view.evaluate_javascript_finish(result).to_string())
        except Exception:
            page = {}
        def got_cookies(cm, res):
            cookies = cm.get_cookies_finish(res)
            names = [c.get_name() for c in cookies]
            if "SAPISID" in names and page.get("v"):
                state["done"] = True
                print(json.dumps({
                    "cookie": "; ".join(f"{c.get_name()}={c.get_value()}" for c in cookies),
                    "visitorData": page.get("v", ""), "dataSyncId": page.get("d", ""), "authUser": page.get("a", "0"),
                }), flush=True)
                Gtk.main_quit()
                sys.exit(0)
            state["attempt"] += 1
            if state["attempt"] < 20:
                GLib.timeout_add(500, lambda: (read(), False)[1])
            else:
                window.show_all()
        manager.get_cookie_manager().get_cookies("https://music.youtube.com", None, got_cookies)
    web.evaluate_javascript(PAGE_JS, -1, None, None, None, got_js)

def changed(view, event):
    if event == WebKit2.LoadEvent.FINISHED and not state["done"]:
        uri = view.get_uri() or ""
        if uri.startswith("https://music.youtube.com"):
            window.hide()
            read()

web.connect("load-changed", changed)
web.load_uri(sys.argv[1] if len(sys.argv) > 1 else SIGN_IN_URL)
window.show_all()
Gtk.main()
