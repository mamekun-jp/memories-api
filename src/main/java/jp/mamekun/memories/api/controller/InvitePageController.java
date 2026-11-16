package jp.mamekun.memories.api.controller;

import jp.mamekun.memories.api.config.SpaceSettings;
import jp.mamekun.memories.api.model.Invite;
import jp.mamekun.memories.api.model.api.InviteInfoResponse;
import jp.mamekun.memories.api.repository.InviteRepository;
import jp.mamekun.memories.api.util.TimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Public (no login) invite endpoints under /api/auth:
 * <ul>
 *   <li>{@code /api/auth/join/{token}}: the page an invite link opens. It opens the app straight
 *   into this space, or sends people to the App Store first and copies the invite so the app
 *   can pick it up on first launch.</li>
 *   <li>{@code /api/auth/invite/{token}}: lets the app check an invite and show the space name.</li>
 * </ul>
 */
@RestController
public class InvitePageController {
    private final InviteRepository inviteRepository;
    private final SpaceSettings spaceSettings;

    public InvitePageController(InviteRepository inviteRepository, SpaceSettings spaceSettings) {
        this.inviteRepository = inviteRepository;
        this.spaceSettings = spaceSettings;
    }

    @GetMapping("/api/auth/invite/{token}")
    public ResponseEntity<InviteInfoResponse> inviteInfo(@PathVariable String token) {
        Optional<Invite> invite = usableInvite(token);
        return ResponseEntity.ok(new InviteInfoResponse(invite.isPresent(), spaceSettings.getSpaceName(),
                invite.map(i -> TimeFormat.iso(i.getExpiresAt())).orElse(null)));
    }

    @GetMapping(value = "/api/auth/join/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> joinPage(@PathVariable String token) {
        boolean valid = usableInvite(token).isPresent();
        String space = escapeHtml(spaceSettings.getSpaceName());
        String html = PAGE
                .replace("{{SPACE}}", space)
                .replace("{{TOKEN}}", valid ? token : "")
                .replace("{{APP_STORE}}", escapeHtml(spaceSettings.getAppStoreUrl()))
                .replace("{{VALID_DISPLAY}}", valid ? "block" : "none")
                .replace("{{INVALID_DISPLAY}}", valid ? "none" : "block");
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(html);
    }

    private Optional<Invite> usableInvite(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,64}")) {
            return Optional.empty();
        }
        return inviteRepository.findByToken(token).filter(invite -> invite.isUsable(ZonedDateTime.now()));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta name="robots" content="noindex">
              <title>Join {{SPACE}}</title>
              <style>
                :root { color-scheme: light dark; --bg:#f2f2f7; --card:#fff; --text:#111; --muted:#6b6b70; --btn:#111; --btn-text:#fff; }
                @media (prefers-color-scheme: dark) { :root { --bg:#000; --card:#1c1c1e; --text:#f2f2f7; --muted:#a1a1a6; --btn:#f2f2f7; --btn-text:#111; } }
                body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
                       background:var(--bg); color:var(--text); font-family:-apple-system,system-ui,sans-serif; }
                .card { width:min(420px, calc(100% - 32px)); background:var(--card); border-radius:20px; padding:28px 24px; text-align:center; }
                h1 { font-size:22px; margin:0 0 8px; }
                p { color:var(--muted); line-height:1.45; margin:0 0 20px; }
                a.btn { display:block; padding:14px; border-radius:12px; font-weight:600; text-decoration:none; margin-top:12px; }
                .primary { background:var(--btn); color:var(--btn-text); }
                .secondary { border:1px solid var(--muted); color:var(--text); }
                .step { font-size:13px; color:var(--muted); margin-top:18px; }
              </style>
            </head>
            <body>
              <div class="card">
                <div style="display:{{VALID_DISPLAY}}">
                  <h1>You're invited to {{SPACE}}</h1>
                  <p>Share photos, stories and private messages with your family on Memories.</p>
                  <a class="btn primary" id="open" href="#">Open in Memories</a>
                  <a class="btn secondary" id="get" href="{{APP_STORE}}">Get the app</a>
                  <div class="step">Don't have Memories yet? Tap “Get the app”, install it, then open it:
                    it will find this invite for you. You can also come back here and tap “Open in Memories”.</div>
                </div>
                <div style="display:{{INVALID_DISPLAY}}">
                  <h1>This invite can't be used</h1>
                  <p>It has expired or was already used. Ask the person who invited you for a new link.</p>
                </div>
              </div>
              <script>
                (function () {
                  var token = "{{TOKEN}}";
                  var base = location.href.split('/api/auth/join/')[0];
                  var open = document.getElementById('open');
                  open.href = 'memories-app://join?address=' + encodeURIComponent(base)
                    + '&invite=' + encodeURIComponent(token)
                    + '&name=' + encodeURIComponent(document.title.replace(/^Join /, ''));
                  // Copy the invite before going to the App Store, so the app can pick it up after install.
                  document.getElementById('get').addEventListener('click', function (e) {
                    var link = base + '/api/auth/join/' + token;
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                      e.preventDefault();
                      var target = this.href;
                      navigator.clipboard.writeText(link).finally(function () { location.href = target; });
                    }
                  });
                })();
              </script>
            </body>
            </html>
            """;
}
