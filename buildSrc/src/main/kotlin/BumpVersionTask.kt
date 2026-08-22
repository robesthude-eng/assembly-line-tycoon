import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/** Что именно повышаем в версии. */
enum class VersionPart { MAJOR, MINOR, PATCH, BUILD }

/**
 * Повышает версию в version.properties.
 *
 * Задача живёт в buildSrc, а не в build.gradle.kts, из-за конфигурационного
 * кэша Gradle: лямбда `doLast`, вызывающая функцию скрипта сборки, не
 * сериализуется («cannot serialize Gradle script object references»).
 * Отдельный класс с @Input-свойствами кэшируется без проблем.
 *
 * Файл переписывается регулярками построчно, чтобы сохранить комментарии:
 * Properties.store() их выбрасывает и подставляет свою дату в первую строку.
 */
abstract class BumpVersionTask : DefaultTask() {

    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val part: Property<VersionPart>

    @TaskAction
    fun bump() {
        val file = versionFile.get().asFile
        val text = file.readText()

        fun read(key: String): Int = Regex("(?m)^$key=(\\d+)\\s*$").find(text)
            ?.groupValues?.get(1)?.toInt()
            ?: error("В ${file.name} нет числового значения $key")

        val major = read("VERSION_MAJOR")
        val minor = read("VERSION_MINOR")
        val patch = read("VERSION_PATCH")
        val build = read("VERSION_BUILD")

        // Повышение старшего разряда обнуляет младшие, счётчик сборок
        // начинается с единицы: versionCode при этом всё равно растёт,
        // потому что старший разряд весит больше.
        val (newMajor, newMinor, newPatch, newBuild) = when (part.get()) {
            VersionPart.MAJOR -> Version(major + 1, 0, 0, 1)
            VersionPart.MINOR -> Version(major, minor + 1, 0, 1)
            VersionPart.PATCH -> Version(major, minor, patch + 1, 1)
            VersionPart.BUILD -> Version(major, minor, patch, build + 1)
        }

        file.writeText(
            text.replace(Regex("(?m)^VERSION_MAJOR=.*$"), "VERSION_MAJOR=$newMajor")
                .replace(Regex("(?m)^VERSION_MINOR=.*$"), "VERSION_MINOR=$newMinor")
                .replace(Regex("(?m)^VERSION_PATCH=.*$"), "VERSION_PATCH=$newPatch")
                .replace(Regex("(?m)^VERSION_BUILD=.*$"), "VERSION_BUILD=$newBuild")
        )

        logger.lifecycle("Версия: $newMajor.$newMinor.$newPatch (сборка $newBuild)")
    }

    private data class Version(val major: Int, val minor: Int, val patch: Int, val build: Int)
}
