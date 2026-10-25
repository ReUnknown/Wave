// Wave's Google sign-in window on macOS, on the system's own WebKit (nothing downloaded).
// Google's sign-in page runs here; once it lands on music.youtube.com signed in, this prints the
// session (cookies and page config) as one line of JSON to Wave and quits.
//   WaveSignIn [url]        sign in (or open a channel-switch URL)
//   WaveSignIn --sign-out   forget the web session
import Cocoa
import WebKit

let signInURL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

final class Delegate: NSObject, NSApplicationDelegate, WKNavigationDelegate, NSWindowDelegate {
    var window: NSWindow!
    var web: WKWebView!
    var done = false
    let start: String

    init(start: String) { self.start = start }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        web = WKWebView(frame: NSRect(x: 0, y: 0, width: 480, height: 680), configuration: config)
        web.navigationDelegate = self
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 480, height: 680),
                          styleMask: [.titled, .closable, .resizable], backing: .buffered, defer: false)
        window.title = "Sign in to Wave"
        window.contentView = web
        window.delegate = self
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        web.load(URLRequest(url: URL(string: start)!))
    }

    func windowWillClose(_ notification: Notification) {
        if !done { exit(1) }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard !done, webView.url?.host == "music.youtube.com" else { return }
        // Don't show the YouTube Music website itself.
        window.orderOut(nil)
        read(attempt: 0)
    }

    func read(attempt: Int) {
        let js = "(function(){var c=(window.yt&&window.yt.config_)||{};return JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',a:String(c.SESSION_INDEX||0)});})()"
        web.evaluateJavaScript(js) { result, _ in
            self.web.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
                let yt = cookies.filter { $0.domain.hasSuffix("youtube.com") }
                let cookie = yt.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                let page = (result as? String).flatMap { try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: String] } ?? [:]
                let visitor = page["v"] ?? ""
                if yt.contains(where: { $0.name == "SAPISID" }) && !visitor.isEmpty {
                    self.done = true
                    let out: [String: String] = ["cookie": cookie, "visitorData": visitor, "dataSyncId": page["d"] ?? "", "authUser": page["a"] ?? "0"]
                    if let data = try? JSONSerialization.data(withJSONObject: out), let line = String(data: data, encoding: .utf8) {
                        print(line)
                        fflush(stdout)
                    }
                    exit(0)
                } else if attempt < 20 {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.read(attempt: attempt + 1) }
                } else {
                    // Not signed in after all: show the page again so it can be finished.
                    self.window.makeKeyAndOrderFront(nil)
                }
            }
        }
    }
}

let args = CommandLine.arguments.dropFirst()
if args.first == "--sign-out" {
    let store = WKWebsiteDataStore.default()
    let group = DispatchGroup()
    group.enter()
    store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) { group.leave() }
    while group.wait(timeout: .now()) == .timedOut { RunLoop.main.run(until: Date().addingTimeInterval(0.05)) }
    exit(0)
}

let app = NSApplication.shared
// A window of its own, without a second icon in the Dock.
app.setActivationPolicy(.accessory)
let delegate = Delegate(start: args.first ?? signInURL)
app.delegate = delegate
app.run()
