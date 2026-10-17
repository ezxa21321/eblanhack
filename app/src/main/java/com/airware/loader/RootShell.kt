package com.airware.loader

import java.util.concurrent.TimeUnit

object RootShell {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    fun exec(command: String, timeoutMs: Long = 4000): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(-1, stdout, "timeout")
            }

            Result(process.exitValue(), stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Result(-1, "", e.message ?: "su exec failed")
        }
    }

    private fun shq(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    fun hasRoot(): Boolean {
        val result = exec("id")
        return result.exitCode == 0 && result.stdout.contains("uid=0")
    }

    fun pidOf(packageName: String): List<Int> {
        val result = exec("pidof ${shq(packageName)}")
        if (result.exitCode != 0 || result.stdout.isBlank()) return emptyList()
        return result.stdout.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }

    /**
     * Ищет файлы в директории, которые содержат ключевое слово ИЛИ заканчиваются на .cfg / .so
     */
    fun listConfigsAndBuilds(dir: String): List<String> {
        val result = exec("ls -1 ${shq(dir)} 2>/dev/null")
        if (result.exitCode != 0) return emptyList()
        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { name ->
                name.isNotEmpty() && (
                    name.endsWith(".cfg", ignoreCase = true) ||
                    name.endsWith(".so", ignoreCase = true) ||
                    name.contains("airware", ignoreCase = true)
                )
            }
            .toList()
    }

    /**
     * Копирует импортированный файл из временного кэша приложения в целевую папку с правами 644
     */
    fun copyFileToDir(sourcePath: String, targetDir: String, fileName: String): Boolean {
        val dest = "$targetDir/$fileName"
        val cmd = "cp ${shq(sourcePath)} ${shq(dest)} && chmod 644 ${shq(dest)}"
        return exec(cmd).exitCode == 0
    }
}
