package tv.cleanyour.core.ops

// Bloatless: `pm enable` for every Android user on the TV (each Google TV profile is one). The user
// ids come from `pm list users` ("UserInfo{10:Name:..}"); a user without the package answers an
// error that is ignored, and the line always ends exit 0, so the step's own check (user 0) decides.
// The package is checked by the renderer and quoted here.
internal object EveryUserLines {
    fun enable(pkg: String): String =
        "for u in \$(pm list users | sed -n 's/.*UserInfo{\\([0-9][0-9]*\\):.*/\\1/p'); " +
            "do pm enable --user \"\$u\" ${ShellTokens.quote(pkg)} >/dev/null 2>&1; done; true"
}
