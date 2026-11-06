package com.pocketcode.tools

import java.io.File

/**
 * Оболочка с «корзиной»: команда rm в командах агента не удаляет файлы, а переносит их в ~/.pocketcode-trash.
 * Записи старше 7 дней чистятся сами. Работает только для rm в shell агента: скрипты, Python и root-команды она не касается.
 */
class TrashShell(private val inner: Shell, private val enabled: () -> Boolean) : Shell {
    override val info: String
        get() = inner.info + if (enabled()) {
            " Команда rm не удаляет, а переносит файлы в корзину ~/.pocketcode-trash (хранится 7 дней); вернуть можно командой mv."
        } else {
            ""
        }

    override suspend fun run(command: String, cwd: File, timeoutSec: Long): String =
        inner.run(wrap(command, enabled()), cwd, timeoutSec)

    override fun builder(command: String, cwd: File): ProcessBuilder = inner.builder(command, cwd)

    companion object {
        private val USES_RM = Regex("""(^|[\s;&|(`])rm(\s|$)""")

        // Знак § здесь стоит вместо доллара, чтобы Kotlin не принял его за шаблон строки
        private const val FUNCTION_TEXT =
            "find \"§HOME/.pocketcode-trash\" -mindepth 1 -maxdepth 1 -mtime +7 -exec rm -rf {} \\; >/dev/null 2>&1\n" +
                "rm() { __t=\"§HOME/.pocketcode-trash/§(date +%Y%m%d-%H%M%S)\"; __f=0; __r=0; __n=0; " +
                "for __a in \"§@\"; do case \"§__a\" in " +
                "--) ;; " +
                "-*) case \"§__a\" in *f*) __f=1;; esac;; " +
                "*) if [ -e \"§__a\" ] || [ -L \"§__a\" ]; then __n=§((__n+1)); " +
                "mkdir -p \"§__t\" && mv -- \"§__a\" \"§__t/§__n-§(basename \"§__a\")\" || __r=1; " +
                "elif [ \"§__f\" = 0 ]; then echo \"rm: нет такого файла: §__a\" >&2; __r=1; fi;; " +
                "esac; done; return §__r; }\n"

        private val PREFIX = FUNCTION_TEXT.replace('§', '$')

        /** Команда, готовая к запуску: с корзиной, если она включена и в команде есть rm. */
        fun wrap(command: String, on: Boolean): String =
            if (on && USES_RM.containsMatchIn(command)) PREFIX + command else command
    }
}
