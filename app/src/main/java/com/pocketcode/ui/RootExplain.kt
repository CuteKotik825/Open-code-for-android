package com.pocketcode.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Пояснение к root-команде: что делает каждая её часть (про сами файлы пользователь решает сам) и меняет ли она систему. */
class RootNote(val lines: List<Pair<String, String>>, val changesSystem: Boolean)

private class Cmd(val text: String, val changes: Boolean = false, val system: Boolean = false)

private val SAFE_PREFIXES = listOf(
    "/sdcard", "/storage/emulated", "/data/local/tmp", "/work", "/data/data/com.pocketcode", "/data/user/0/com.pocketcode",
)

private val SIMPLE: Map<String, Cmd> = mapOf(
    "id" to Cmd("Показывает, от какого пользователя запущена команда (uid=0 значит root)."),
    "whoami" to Cmd("Показывает имя текущего пользователя."),
    "ls" to Cmd("Показывает список файлов в папке."),
    "cat" to Cmd("Выводит содержимое файла."),
    "head" to Cmd("Показывает начало файла."),
    "tail" to Cmd("Показывает конец файла."),
    "grep" to Cmd("Ищет строки с нужным текстом."),
    "find" to Cmd("Ищет файлы по имени или условию."),
    "df" to Cmd("Показывает занятое и свободное место на разделах."),
    "du" to Cmd("Считает, сколько места занимают папки."),
    "ps" to Cmd("Показывает запущенные процессы."),
    "top" to Cmd("Показывает загрузку процессора и процессы."),
    "pidof" to Cmd("Находит номер процесса по имени."),
    "pgrep" to Cmd("Находит процессы по имени."),
    "getprop" to Cmd("Читает системные свойства Android (модель, версия и т. д.). Ничего не меняет."),
    "dumpsys" to Cmd("Выводит состояние системной службы Android. Только чтение."),
    "logcat" to Cmd("Показывает журнал системы. Только чтение."),
    "dmesg" to Cmd("Показывает журнал ядра. Только чтение."),
    "uname" to Cmd("Показывает версию ядра."),
    "uptime" to Cmd("Показывает, сколько телефон включён."),
    "date" to Cmd("Показывает дату и время."),
    "echo" to Cmd("Печатает текст."),
    "pwd" to Cmd("Показывает текущую папку."),
    "cd" to Cmd("Переходит в другую папку."),
    "stat" to Cmd("Показывает сведения о файле: размер, права, даты."),
    "file" to Cmd("Определяет тип файла."),
    "wc" to Cmd("Считает строки, слова или байты."),
    "sort" to Cmd("Сортирует строки."),
    "uniq" to Cmd("Убирает повторяющиеся строки."),
    "cut" to Cmd("Вырезает столбцы из текста."),
    "awk" to Cmd("Обрабатывает текст по правилам."),
    "ip" to Cmd("Показывает сетевые интерфейсы и маршруты."),
    "ifconfig" to Cmd("Показывает сетевые интерфейсы."),
    "netstat" to Cmd("Показывает сетевые соединения."),
    "ss" to Cmd("Показывает сетевые соединения."),
    "ping" to Cmd("Проверяет доступность адреса в сети."),
    "env" to Cmd("Показывает переменные окружения."),
    "printenv" to Cmd("Показывает переменные окружения."),
    "lsmod" to Cmd("Показывает загруженные модули ядра."),
    "lsof" to Cmd("Показывает, какие файлы открыты процессами."),
    "getenforce" to Cmd("Показывает режим SELinux (Enforcing или Permissive)."),
    "free" to Cmd("Показывает использование памяти."),
    "diff" to Cmd("Сравнивает два файла."),
    "md5sum" to Cmd("Считает контрольную сумму файла."),
    "sha256sum" to Cmd("Считает контрольную сумму файла."),
    "readlink" to Cmd("Показывает, куда ведёт ссылка."),
    "which" to Cmd("Показывает, где лежит программа."),
    "tree" to Cmd("Показывает дерево папок."),
    "strings" to Cmd("Достаёт читаемый текст из бинарного файла."),
    "xxd" to Cmd("Показывает файл в шестнадцатеричном виде."),
    "hexdump" to Cmd("Показывает файл в шестнадцатеричном виде."),
    "sleep" to Cmd("Ждёт указанное число секунд."),
    "true" to Cmd("Ничего не делает."),
    "sync" to Cmd("Сбрасывает несохранённые данные на диск."),

    "rm" to Cmd("Удаляет файлы или папки. Без корзины: вернуть нельзя.", changes = true),
    "rmdir" to Cmd("Удаляет пустые папки.", changes = true),
    "mv" to Cmd("Перемещает или переименовывает файлы.", changes = true),
    "cp" to Cmd("Копирует файлы. Существующие файлы могут быть перезаписаны.", changes = true),
    "mkdir" to Cmd("Создаёт папки.", changes = true),
    "touch" to Cmd("Создаёт пустой файл или обновляет время изменения.", changes = true),
    "chmod" to Cmd("Меняет права доступа к файлам.", changes = true),
    "chown" to Cmd("Меняет владельца файлов.", changes = true),
    "chgrp" to Cmd("Меняет группу файлов.", changes = true),
    "chcon" to Cmd("Меняет метку безопасности SELinux у файлов.", changes = true, system = true),
    "restorecon" to Cmd("Сбрасывает метки SELinux у файлов к стандартным.", changes = true, system = true),
    "ln" to Cmd("Создаёт ссылку на файл.", changes = true),
    "dd" to Cmd("Копирует данные блоками. Если выход направлен на раздел или устройство, затирает его содержимое.", changes = true, system = true),
    "tee" to Cmd("Записывает ввод в файл (и выводит на экран).", changes = true),
    "truncate" to Cmd("Обрезает или обнуляет файл.", changes = true),
    "setprop" to Cmd("Меняет системное свойство Android.", changes = true, system = true),
    "resetprop" to Cmd("Меняет системное свойство Android напрямую (Magisk).", changes = true, system = true),
    "setenforce" to Cmd("Переключает режим SELinux (защита системы).", changes = true, system = true),
    "chattr" to Cmd("Меняет особые атрибуты файла (например, запрещает его менять или удалять).", changes = true, system = true),
    "mkfs" to Cmd("Форматирует раздел: все данные на нём пропадут.", changes = true, system = true),
    "fsck" to Cmd("Проверяет и чинит файловую систему раздела.", changes = true, system = true),
    "umount" to Cmd("Отключает раздел от системы.", changes = true, system = true),
    "reboot" to Cmd("Перезагружает телефон.", changes = true, system = true),
    "kill" to Cmd("Завершает процесс по номеру.", changes = true),
    "killall" to Cmd("Завершает процессы по имени.", changes = true),
    "pkill" to Cmd("Завершает процессы по имени.", changes = true),
    "svc" to Cmd("Включает или выключает Wi-Fi, мобильные данные, экран и т. п.", changes = true),
    "input" to Cmd("Имитирует нажатия, свайпы и ввод текста.", changes = true),
    "wm" to Cmd("Меняет размер и плотность экрана.", changes = true),
    "magisk" to Cmd("Управляет Magisk (root, модули).", changes = true, system = true),
    "iptables" to Cmd("Меняет правила сетевого экрана.", changes = true, system = true),
    "install" to Cmd("Копирует файл и задаёт ему права.", changes = true),
    "unzip" to Cmd("Распаковывает архив. Существующие файлы могут быть перезаписаны.", changes = true),
    "start" to Cmd("Запускает системную службу.", changes = true, system = true),
    "stop" to Cmd("Останавливает системную службу.", changes = true, system = true),
    "swapon" to Cmd("Включает раздел подкачки.", changes = true, system = true),
    "swapoff" to Cmd("Выключает раздел подкачки.", changes = true, system = true),
    "fastboot" to Cmd("Прошивает или меняет разделы телефона.", changes = true, system = true),
    "flash_image" to Cmd("Записывает образ в раздел.", changes = true, system = true),
    "wget" to Cmd("Скачивает файл из сети."),
    "printf" to Cmd("Печатает текст по шаблону (форматированный вывод)."),
    "basename" to Cmd("Оставляет от пути только имя файла."),
    "dirname" to Cmd("Оставляет от пути только папку."),
    "realpath" to Cmd("Показывает полный путь к файлу."),
    "tr" to Cmd("Заменяет или удаляет символы в тексте."),
    "rev" to Cmd("Переворачивает строки задом наперёд."),
    "tac" to Cmd("Выводит файл, начиная с последней строки."),
    "nl" to Cmd("Нумерует строки."),
    "less" to Cmd("Показывает файл постранично."),
    "more" to Cmd("Показывает файл постранично."),
    "seq" to Cmd("Печатает последовательность чисел."),
    "expr" to Cmd("Считает выражение."),
    "bc" to Cmd("Калькулятор."),
    "base64" to Cmd("Кодирует или раскодирует данные в base64."),
    "jq" to Cmd("Разбирает и фильтрует JSON."),
    "gzip" to Cmd("Сжимает файл (оригинал заменяется архивом).", changes = true),
    "gunzip" to Cmd("Распаковывает .gz (архив заменяется файлом).", changes = true),
    "zcat" to Cmd("Показывает содержимое сжатого файла."),
    "zip" to Cmd("Упаковывает файлы в архив.", changes = true),
    "python" to Cmd("Запускает скрипт на Python. Что он делает, зависит от скрипта."),
    "python3" to Cmd("Запускает скрипт на Python. Что он делает, зависит от скрипта."),
    "node" to Cmd("Запускает скрипт на Node.js. Что он делает, зависит от скрипта."),
    "perl" to Cmd("Запускает скрипт на Perl. Что он делает, зависит от скрипта."),
    "nc" to Cmd("Соединяется с адресом в сети или слушает порт (netcat)."),
    "nslookup" to Cmd("Узнаёт IP-адрес по имени сайта."),
    "traceroute" to Cmd("Показывает путь пакетов до адреса."),
    "tcpdump" to Cmd("Перехватывает и показывает сетевой трафик."),
    "ssh" to Cmd("Подключается к другому компьютеру по SSH."),
    "scp" to Cmd("Копирует файлы на другой компьютер или с него.", changes = true),
    "adb" to Cmd("Управляет устройством через ADB."),
    "export" to Cmd("Задаёт переменную окружения для следующих команд."),
    "set" to Cmd("Меняет параметры оболочки или показывает переменные."),
    "unset" to Cmd("Удаляет переменную."),
    "local" to Cmd("Объявляет переменную внутри функции."),
    "read" to Cmd("Читает ввод в переменную."),
    "exit" to Cmd("Завершает скрипт."),
    "return" to Cmd("Выходит из функции."),
    "break" to Cmd("Прерывает цикл."),
    "continue" to Cmd("Переходит к следующему шагу цикла."),
    "eval" to Cmd("Выполняет текст как команду. Проверь, что именно внутри."),
    "source" to Cmd("Выполняет команды из файла в текущей оболочке."),
    "." to Cmd("Выполняет команды из файла в текущей оболочке."),
    "alias" to Cmd("Задаёт короткое имя для команды."),
    "trap" to Cmd("Задаёт, что сделать при сигнале или выходе."),
    "wait" to Cmd("Ждёт завершения фоновых процессов."),
    "xargs" to Cmd("Запускает команду для каждого элемента из ввода. Что она делает, смотри в тексте."),
    "mktemp" to Cmd("Создаёт временный файл или папку.", changes = true),
    "[" to Cmd("Проверяет условие (например, есть ли файл)."),
    "[[" to Cmd("Проверяет условие (например, есть ли файл)."),
    "test" to Cmd("Проверяет условие (например, есть ли файл)."),
    "lsblk" to Cmd("Показывает диски и разделы."),
    "blkid" to Cmd("Показывает идентификаторы и типы разделов."),
    "fdisk" to Cmd("Работает с таблицей разделов диска. Неверное действие может стереть данные.", changes = true, system = true),
    "parted" to Cmd("Работает с таблицей разделов диска. Неверное действие может стереть данные.", changes = true, system = true),
    "losetup" to Cmd("Подключает файл как блочное устройство.", changes = true, system = true),
    "insmod" to Cmd("Загружает модуль ядра.", changes = true, system = true),
    "modprobe" to Cmd("Загружает модуль ядра.", changes = true, system = true),
    "rmmod" to Cmd("Выгружает модуль ядра.", changes = true, system = true),
    "sysctl" to Cmd("Показывает или меняет параметры ядра."),
    "setsebool" to Cmd("Меняет переключатель SELinux.", changes = true, system = true),
    "ndc" to Cmd("Управляет сетевыми службами Android (netd).", changes = true, system = true),
    "vdc" to Cmd("Управляет службой томов Android (vold).", changes = true, system = true),
    "device_config" to Cmd("Показывает или меняет серверные флаги Android.", changes = true, system = true),
    "getevent" to Cmd("Показывает события нажатий и датчиков."),
    "sendevent" to Cmd("Отправляет системное событие ввода.", changes = true),
    "screencap" to Cmd("Делает снимок экрана и сохраняет в файл.", changes = true),
    "screenrecord" to Cmd("Записывает видео с экрана в файл.", changes = true),
    "monkey" to Cmd("Генерирует случайные нажатия в приложении.", changes = true),
    "dpm" to Cmd("Управляет политиками устройства (администраторы).", changes = true, system = true),
    "appops" to Cmd("Показывает или меняет разрешения приложений."),
    "content" to Cmd("Читает или меняет данные приложений Android (content provider)."),
    "service" to Cmd("Вызывает системную службу Android напрямую."),
    "bmgr" to Cmd("Управляет резервным копированием Android."),
    "tune2fs" to Cmd("Меняет параметры файловой системы раздела.", changes = true, system = true),
    "e2fsck" to Cmd("Проверяет и чинит файловую систему раздела.", changes = true, system = true),
    "blockdev" to Cmd("Меняет параметры блочного устройства.", changes = true, system = true),
    "sha1sum" to Cmd("Считает контрольную сумму файла."),
    "cksum" to Cmd("Считает контрольную сумму файла."),
    "od" to Cmd("Показывает файл в числовом виде."),
    "nproc" to Cmd("Показывает число ядер процессора."),
    "hostname" to Cmd("Показывает имя устройства."),
    "w" to Cmd("Показывает, кто вошёл в систему."),
    "last" to Cmd("Показывает историю входов."),
    "history" to Cmd("Показывает историю введённых команд."),
    "logger" to Cmd("Пишет сообщение в системный журнал."),
    "vmstat" to Cmd("Показывает статистику памяти и процессора."),
    "iostat" to Cmd("Показывает статистику ввода-вывода дисков."),
    "lsattr" to Cmd("Показывает особые атрибуты файлов."),
    "getfattr" to Cmd("Показывает расширенные атрибуты файлов."),
    "curl" to Cmd("Обращается к адресу в сети или скачивает файл."),
)

/** Пояснения к частым ключам: ключ -> описание. Показываются под командой. */
private val FLAGS: Map<String, Map<String, String>> = mapOf(
    "grep" to mapOf("-c" to "считает найденные строки, а не выводит их", "-x" to "строка должна совпасть целиком", "-i" to "без учёта регистра", "-r" to "ищет во всех файлах папки и вложенных", "-v" to "наоборот: выводит строки без этого текста", "-l" to "выводит только имена файлов", "-n" to "добавляет номера строк", "-o" to "выводит только совпавшую часть", "-E" to "шаблон как регулярное выражение", "-q" to "ничего не выводит, только результат проверки", "-w" to "совпадение только целым словом", "-F" to "ищет текст как есть, без шаблонов", "-a" to "обрабатывает бинарный файл как текст"),
    "ls" to mapOf("-l" to "подробный список (права, владелец, размер, дата)", "-a" to "показывает и скрытые файлы", "-R" to "включая вложенные папки", "-h" to "размеры в удобном виде (КБ, МБ)", "-d" to "сама папка, а не её содержимое", "-Z" to "метки SELinux"),
    "rm" to mapOf("-r" to "рекурсивно: вместе со всем содержимым папок", "-R" to "рекурсивно: вместе со всем содержимым папок", "-f" to "без вопросов, игнорирует ошибки"),
    "cp" to mapOf("-r" to "папки целиком, со вложенными", "-R" to "папки целиком, со вложенными", "-a" to "сохраняя права, владельца и даты", "-f" to "перезаписывая без вопросов", "-p" to "сохраняя права и даты"),
    "mv" to mapOf("-f" to "перезаписывая без вопросов"),
    "mkdir" to mapOf("-p" to "создаёт и все недостающие папки по пути"),
    "chmod" to mapOf("-R" to "для всех вложенных файлов и папок"),
    "chown" to mapOf("-R" to "для всех вложенных файлов и папок"),
    "tail" to mapOf("-f" to "следит за файлом и показывает новые строки", "-n" to "сколько последних строк показать"),
    "head" to mapOf("-n" to "сколько первых строк показать"),
    "du" to mapOf("-s" to "только итог по каждой папке", "-h" to "размеры в удобном виде", "-a" to "включая файлы"),
    "df" to mapOf("-h" to "размеры в удобном виде"),
    "ps" to mapOf("-A" to "все процессы", "-e" to "все процессы", "-f" to "подробно", "-ef" to "все процессы, подробно", "-aux" to "все процессы всех пользователей"),
    "sort" to mapOf("-u" to "убирает повторы", "-n" to "сортирует как числа", "-r" to "в обратном порядке"),
    "wc" to mapOf("-l" to "считает строки", "-w" to "считает слова", "-c" to "считает байты"),
    "cat" to mapOf("-n" to "с номерами строк"),
    "find" to mapOf("-name" to "по имени", "-type" to "по типу (f файл, d папка)", "-mtime" to "по дате изменения", "-size" to "по размеру", "-delete" to "удаляет найденное", "-exec" to "выполняет команду для каждого найденного", "-maxdepth" to "ограничивает глубину поиска"),
    "logcat" to mapOf("-c" to "очищает журнал", "-d" to "выводит журнал и выходит", "-b" to "выбирает буфер журнала"),
    "dd" to mapOf("if=" to "откуда читать", "of=" to "куда записывать (затирает содержимое)"),
    "ping" to mapOf("-c" to "сколько раз проверить"),
    "tar" to mapOf("-x" to "распаковать", "-c" to "создать архив", "-z" to "со сжатием gzip", "-f" to "имя файла архива", "-v" to "показывать процесс"),
    "curl" to mapOf("-s" to "без вывода прогресса", "-o" to "сохранить в файл", "-O" to "сохранить под исходным именем", "-L" to "идти по перенаправлениям", "-X" to "метод запроса", "-d" to "данные запроса"),
    "mount" to mapOf("-o" to "параметры (например, remount,rw — разрешить запись)", "-t" to "тип файловой системы"),
    "unzip" to mapOf("-o" to "перезаписывать без вопросов", "-d" to "в какую папку распаковать"),
    "sed" to mapOf("-n" to "выводить только отмеченные строки", "-i" to "правка прямо в файле", "-e" to "скрипт замены"),
    "xargs" to mapOf("-n" to "сколько элементов передавать за раз"),
    "kill" to mapOf("-9" to "жёсткое завершение, процесс не успеет сохраниться", "-KILL" to "жёсткое завершение", "-TERM" to "вежливая просьба завершиться"),
    "pkill" to mapOf("-9" to "жёсткое завершение", "-f" to "искать по всей командной строке"),
    "killall" to mapOf("-9" to "жёсткое завершение"),
)

private fun flagLines(name: String, args: List<String>): List<Pair<String, String>> {
    val map = FLAGS[name] ?: return emptyList()
    val out = ArrayList<Pair<String, String>>()
    for (a in args) {
        if (!a.startsWith("-") && !(a.contains("=") && map.containsKey(a.substringBefore("=") + "="))) continue
        val key = if (a.contains("=")) a.substringBefore("=") + "=" else a
        val whole = map[key]
        if (whole != null) { out += ("  " + key) to whole; continue }
        if (a.startsWith("--") || a.length < 2) continue
        // склеенные короткие ключи: -rf, -la
        for (ch in a.drop(1)) {
            val d = map["-$ch"] ?: continue
            if (d.isNotEmpty()) out += ("  -$ch") to d
        }
    }
    return out.distinct().take(7)
}

/** Подстановки $(...) и `...`: содержимое вложенных команд. */
private fun substitutions(cmd: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < cmd.length - 1) {
        if (cmd[i] == '$' && cmd[i + 1] == '(' && !(i + 2 < cmd.length && cmd[i + 2] == '(')) {
            var d = 1
            var j = i + 2
            while (j < cmd.length && d > 0) { if (cmd[j] == '(') d++ else if (cmd[j] == ')') d--; j++ }
            if (d == 0) { out += cmd.substring(i + 2, j - 1); i = j; continue }
        }
        i++
    }
    Regex("`([^`]+)`").findAll(cmd).forEach { out += it.groupValues[1] }
    return out
}

private val KEYWORD_LINES = mapOf(
    "for" to "Цикл: повторяет команды для каждого значения из списка.",
    "while" to "Цикл: повторяет команды, пока выполняется условие.",
    "until" to "Цикл: повторяет команды, пока условие не выполнится.",
    "if" to "Условие: следующие команды выполнятся, только если проверка пройдена.",
    "elif" to "Иначе-если: ещё одна проверка условия.",
    "case" to "Выбор варианта по значению.",
)

private val MODIFY_WORDS = setOf(
    "install", "uninstall", "disable", "disable-user", "enable", "clear", "hide", "unhide", "suspend", "unsuspend", "grant", "revoke", "set-installer",
)

private fun segments(cmd: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var q = ' '
    var i = 0
    while (i < cmd.length) {
        val c = cmd[i]
        if (c == '$' && i + 1 < cmd.length && cmd[i + 1] == '(' && (q == ' ' || q == '"')) {
            // $( ... ) целиком, со всем, что внутри
            var d = 0
            var j = i + 1
            while (j < cmd.length) { if (cmd[j] == '(') d++ else if (cmd[j] == ')') { d--; if (d == 0) break }; j++ }
            val end = if (j < cmd.length) j else cmd.length - 1
            sb.append(cmd, i, end + 1)
            i = end + 1
            continue
        }
        if (q != ' ') {
            sb.append(c)
            if (c == q) q = ' '
        } else if (c == '\'' || c == '"') { q = c; sb.append(c) }
        else if (c == '\n' || c == ';') { out += sb.toString(); sb.setLength(0) }
        else if ((c == '&' || c == '|') && i + 1 < cmd.length && cmd[i + 1] == c) { out += sb.toString(); sb.setLength(0); i++ }
        else if (c == '|') { out += sb.toString(); sb.setLength(0) }
        else sb.append(c)
        i++
    }
    out += sb.toString()
    return out.map { it.trim() }.filter { it.isNotEmpty() }
}

private fun tokens(s: String): List<String> = Regex("""'[^']*'|"[^"]*"|\S+""").findAll(s).map { it.value.trim('\'', '"') }.toList()

private fun hasRedirect(seg: String): Pair<Boolean, Boolean> {
    var q = ' '
    var i = 0
    while (i < seg.length) {
        val c = seg[i]
        if (q != ' ') { if (c == q) q = ' ' }
        else if (c == '\'' || c == '"') q = c
        else if (c == '>') {
            val prev = if (i > 0) seg[i - 1] else ' '
            if (prev == '2' || prev == '&') { i++; continue } // 2>/dev/null, >&2
            if (seg.drop(i).trimStart('>').trimStart().startsWith("/dev/null")) { i++; continue }
            return true to (i + 1 < seg.length && seg[i + 1] == '>')
        }
        i++
    }
    return false to false
}

/** Разбирает команду: что делает каждая часть и меняет ли она систему (удаление, создание, перезапись, свойства, разделы). */
fun rootNote(cmd: String): RootNote = rootNoteAt(cmd, 0)

private fun rootNoteAt(cmd: String, depth: Int): RootNote {
    val lines = ArrayList<Pair<String, String>>()
    var systemChange = false
    for (seg in segments(cmd)) {
        var t = tokens(seg).toMutableList()
        // обёртки, которые сами ничего не делают
        while (t.isNotEmpty()) {
            val w = t[0].substringAfterLast('/')
            if (Regex("^[A-Za-z_][A-Za-z0-9_]*=.*").matches(t[0]) || w in setOf("sudo", "nohup", "exec", "busybox", "toybox", "command", "time")) t.removeAt(0)
            else if (w == "su" && t.size > 2 && t[1] == "-c") { t = tokens(t.drop(2).joinToString(" ")).toMutableList() }
            else if (w == "timeout" && t.size > 2) t = t.drop(2).toMutableList()
            else break
        }
        // ключевые слова оболочки: сами ничего не делают, но поясняем циклы и условия
        while (t.isNotEmpty() && t[0] in setOf("do", "then", "else", "{", "(", "!")) t.removeAt(0)
        if (t.isEmpty() || t[0] in setOf("done", "fi", "esac", "}", ")", ";;")) continue
        val kw = KEYWORD_LINES[t[0]]
        if (kw != null) {
            lines += t[0] to kw
            if (t[0] == "for" || t[0] == "case") continue
            t.removeAt(0)
            if (t.isEmpty()) continue
        }
        val name = t[0].substringAfterLast('/')
        val args = t.drop(1)
        val sub = args.firstOrNull { !it.startsWith("-") } ?: ""
        val (redir, append) = hasRedirect(seg)
        var text: String? = null
        var changes = false
        var inherently = false
        when {
            name == "find" && args.contains("-delete") -> { text = "Ищет файлы и сразу удаляет найденные."; changes = true }
            name == "find" && args.contains("-exec") -> { text = "Ищет файлы и выполняет команду для каждого найденного. Смотри, какую именно."; changes = true }
            name == "find" -> text = "Ищет файлы по имени или условию."
            name == "logcat" && args.contains("-c") -> { text = "Очищает журнал системы."; changes = true }
            name == "sysctl" && (args.any { it.contains("=") } || args.contains("-w")) -> { text = "Меняет параметр ядра."; changes = true; inherently = true }
            name == "appops" && sub == "set" -> { text = "Меняет разрешение приложения."; changes = true; inherently = true }
            name == "content" && sub in setOf("insert", "update", "delete") -> { text = "Меняет данные приложения Android."; changes = true; inherently = true }
            name == "mount" && args.any { it.contains("remount") } -> {
                text = "Перемонтирует раздел: меняет режим доступа (например, разрешает запись в защищённый раздел)."; changes = true; inherently = true
            }
            name == "mount" && args.isEmpty() -> text = "Показывает подключённые разделы."
            name == "mount" -> { text = "Подключает раздел или папку к системе."; changes = true; inherently = true }
            name == "sed" && args.any { it == "-i" || it.startsWith("-i") && !it.startsWith("--") && it.length <= 3 } -> { text = "Правит текст прямо в файле (меняет файл на месте)."; changes = true }
            name == "sed" -> text = "Заменяет текст в потоке. Файл не меняет."
            name == "settings" && sub in setOf("put", "delete") -> { text = "Меняет настройку Android (${args.firstOrNull() ?: ""})."; changes = true; inherently = true }
            name == "settings" -> text = "Читает настройку Android."
            name == "pm" && sub in setOf("list", "path", "dump", "get-install-location", "resolve-activity") -> text = "Показывает сведения о приложениях. Ничего не меняет."
            name == "pm" && sub in MODIFY_WORDS -> {
                text = when (sub) {
                    "uninstall" -> "Удаляет приложение вместе с данными."
                    "install" -> "Устанавливает приложение."
                    "clear" -> "Стирает данные приложения."
                    "disable", "disable-user", "hide", "suspend" -> "Отключает приложение."
                    "enable", "unhide", "unsuspend" -> "Включает приложение."
                    else -> "Меняет разрешения или параметры приложения."
                }; changes = true; inherently = true
            }
            name == "pm" -> text = "Работает с приложениями (pm)."
            name == "am" && sub in setOf("start", "startservice", "start-activity", "broadcast") -> { text = "Запускает экран, службу или рассылку Android."; changes = true }
            name == "am" && sub in setOf("force-stop", "kill", "kill-all") -> { text = "Принудительно останавливает приложение."; changes = true }
            name == "am" -> text = "Управляет компонентами Android (am)."
            name == "cmd" -> text = "Вызывает системную службу Android (cmd)."
            name == "tar" && args.any { it.startsWith("-") && !it.startsWith("--") && it.contains("x") || it == "--extract" } -> { text = "Распаковывает архив. Существующие файлы могут быть перезаписаны."; changes = true }
            name == "tar" -> text = "Работает с архивом tar."
            name == "ip" && sub in setOf("link", "addr", "route", "rule") && args.any { it in setOf("add", "del", "set", "flush") } -> { text = "Меняет сетевые настройки."; changes = true; inherently = true }
            name == "curl" && args.any { it == "-o" || it == "-O" || it.startsWith("--output") } -> { text = "Скачивает файл из сети и сохраняет на телефон."; changes = true }
            name == "wget" -> { text = "Скачивает файл из сети и сохраняет на телефон."; changes = true }
            name.startsWith("mkfs") -> { text = SIMPLE["mkfs"]!!.text; changes = true; inherently = true }
            name == "sh" || name == "bash" || name == "su" -> text = "Запускает вложенную команду или скрипт. Что внутри, смотри в тексте."
            else -> SIMPLE[name]?.let { text = it.text; changes = it.changes; inherently = it.system }
        }
        if (text == null && (t[0].contains('/') || t[0].startsWith("."))) text = "Запускает программу или скрипт. Что он делает, зависит от самого файла: прочитай его или проверь у нейросети."
        val flagInfo = flagLines(name, args)
        if (redir) {
            lines += (if (text == null) name else ">") to (if (append) "Дописывает вывод в файл (>>)." else "Перезаписывает файл выводом команды (>): старое содержимое пропадёт.")
            changes = true
        }
        if (text != null || !redir) lines += name to (text ?: "Незнакомая команда. Проверь её у нейросети перед выполнением.")
        lines += flagInfo
        if (changes) {
            val paths = args.filter { it.startsWith("/") } + Regex(""">>?\s*(/\S+)""").findAll(seg).map { it.groupValues[1] }.toList()
            val unsafe = paths.isEmpty() || paths.any { p -> SAFE_PREFIXES.none { p == it || p.startsWith("$it/") } }
            if (inherently || unsafe) systemChange = true
        }
    }
    if (depth < 2) {
        val inner = substitutions(cmd)
        if (inner.isNotEmpty()) {
            lines += "$(…)" to "Вложенная команда: её результат подставляется в текст."
            for (c in inner) {
                val n = rootNoteAt(c, depth + 1)
                lines += n.lines
                if (n.changesSystem) systemChange = true
            }
        }
    }
    return RootNote(lines.distinct(), systemChange)
}

/** Сколько секунд нужно ждать перед подтверждением: выбранная в настройках задержка, но для изменения системы не меньше 5 секунд. */
fun rootDelaySeconds(chosen: Int, note: RootNote): Int = if (note.changesSystem) maxOf(chosen, 5) else chosen

@Composable
fun rememberRootCountdown(key: Any, seconds: Int): Int {
    var left by remember(key, seconds) { mutableIntStateOf(seconds) }
    LaunchedEffect(key, seconds) { while (left > 0) { delay(1000); left-- } }
    return left
}

/** Блок под командой: что она делает, и предупреждение об обязательной задержке. */
@Composable
fun RootNoteView(note: RootNote, chosenDelay: Int) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("Что делает команда:", style = MaterialTheme.typography.labelLarge)
        for ((n, d) in note.lines) {
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 2.dp)) {
                androidx.compose.material3.Text("• $n: ", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(d, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            }
        }
        if (note.changesSystem) {
            Text(
                "⚠ Команда меняет систему (удаляет, создаёт или перезаписывает). Подтверждение с задержкой обязательно.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
        } else if (chosenDelay > 0) {
            Text("Подтверждение откроется через $chosenDelay с.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
