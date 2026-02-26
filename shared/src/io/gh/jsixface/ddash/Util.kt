package io.gh.jsixface.ddash

expect fun getEnv(key: EnvVars): String?

fun String.removeAnsiCodes(): String {
    val ansiRegex = Regex("\u001B\\[[;\\d]*[A-Za-z]")
    return this.replace(ansiRegex, "")
}

expect fun setupShutdownHook(block: () -> Unit)
