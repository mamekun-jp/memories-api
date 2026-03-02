package jp.mamekun.memories.api.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordResetPageController {

    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPasswordPage(
            @RequestParam(name = "token", required = false) String token
    ) {
        String safeToken = token == null ? "" : escapeHtml(token);

        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width,initial-scale=1" />
                  <title>Reset Password</title>
                  <style>
                    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; margin: 40px; }
                    .card { max-width: 520px; padding: 20px; border: 1px solid #ddd; border-radius: 10px; }
                    label { display:block; margin-top: 12px; font-weight: 600; }
                    input { width: 100%%; padding: 10px; margin-top: 6px; box-sizing: border-box; }
                    button { margin-top: 16px; padding: 10px 14px; }
                    .msg { margin-top: 12px; white-space: pre-wrap; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>Reset Password</h2>
                    <p>Enter your new password. This will use the token from the email link.</p>

                    <label for="token">Token</label>
                    <input id="token" name="token" value="%s" placeholder="Paste token from email" />

                    <label for="newPassword">New password</label>
                    <input id="newPassword" name="newPassword" type="password" placeholder="At least 8 characters" />

                    <button id="submitBtn" type="button">Reset</button>

                    <div id="msg" class="msg"></div>
                  </div>

                  <script>
                    const msg = document.getElementById('msg');

                    function setMsg(text) {
                      msg.textContent = text;
                    }

                    document.getElementById('submitBtn').addEventListener('click', async () => {
                      const token = document.getElementById('token').value.trim();
                      const newPassword = document.getElementById('newPassword').value;

                      if (!token) { setMsg('Token is required.'); return; }
                      if (!newPassword || newPassword.length < 8) { setMsg('Password must be at least 8 characters.'); return; }

                      setMsg('Submitting...');

                      try {
                        const res = await fetch('/api/auth/reset-password', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ token, newPassword })
                        });

                        const text = await res.text();
                        if (res.ok) {
                          setMsg('Password reset successful. You can now log in.');
                        } else {
                          setMsg(text || 'Password reset failed.');
                        }
                      } catch (e) {
                        setMsg('Network error: ' + (e && e.message ? e.message : 'unknown'));
                      }
                    });
                  </script>
                </body>
                </html>
                """.formatted(safeToken);

        return ResponseEntity.ok(html);
    }

    private static String escapeHtml(String s) {
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
