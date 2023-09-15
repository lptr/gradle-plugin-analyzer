import com.google.common.collect.ImmutableSortedSet
import com.google.common.collect.MultimapBuilder
import com.ibm.wala.classLoader.IClass
import com.ibm.wala.classLoader.IField
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.types.TypeName
import com.ibm.wala.types.TypeReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.gradle.internal.Actions
import org.gradlex.plugins.analyzer.Analysis
import org.gradlex.plugins.analyzer.Analyzer
import org.gradlex.plugins.analyzer.DefaultAnalyzer
import org.gradlex.plugins.analyzer.Reporter
import org.gradlex.plugins.analyzer.TypeRepository
import org.gradlex.plugins.analyzer.TypeRepository.TypeSet
import org.gradlex.plugins.analyzer.TypeRepository.TypeSet.ALL_EXTERNAL_REFERENCED_TYPES
import org.gradlex.plugins.analyzer.TypeRepository.TypeSet.EXTERNAL_TASK_TYPES
import org.gradlex.plugins.analyzer.WalaUtil.toFQCN
import org.gradlex.plugins.analyzer.analysis.FindTypeReferences
import org.gradlex.plugins.analyzer.analysis.ShouldNotReferenceInternalApi
import org.gradlex.plugins.analyzer.analysis.TypeShouldExtendType
import org.gradlex.plugins.analyzer.analysis.TypeShouldNotOverrideGetter
import org.gradlex.plugins.analyzer.analysis.TypeShouldNotOverrideSetter
import org.jsoup.Jsoup
import org.slf4j.event.Level
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    // Required since we are resolving JVM artifacts
    `jvm-ecosystem`
}

val gradleRuntime = configurations.create("gradleRuntime")

dependencies {
    gradleRuntime(gradleApi())
}

abstract class AnalyzedPlugin(val pluginId: String) : Comparable<AnalyzedPlugin>, Named {

    abstract val coordinates: Property<String>

    override fun getName() = pluginId

    override fun compareTo(other: AnalyzedPlugin) = pluginId.compareTo(other.pluginId)
}

abstract class AnalysisRunner : java.io.Serializable {
    abstract fun executeAnalysis(context: Context)

    protected fun executeAnalysis(context: Context, title: String, set: TypeSet, analysis: Analysis) {
        val builder = ImmutableSortedSet.naturalOrder<Message>()
        context.analyzer.analyze(set, analysis) { level, message, args ->
            if (level.toInt() >= context.level.toInt()) {
                builder.add(Message(level.name, message.format(*args)))
            }
        }
        val messages = builder.build()
        if (!messages.isEmpty()) {
            context.messageGroups.add(MessageGroup(title, messages.asList()))
        }
    }

    data class Context(val analyzer: Analyzer, val level: Level, val messageGroups: MutableList<MessageGroup>)
}

class TypeShouldExtendTypeRunner(@Input val title: String, @Input val set: TypeSet, @Input val extendedSuperType: String) : AnalysisRunner() {
    override fun executeAnalysis(context: Context) {
        executeAnalysis(context, title, set, TypeShouldExtendType(extendedSuperType))
    }
}

class TypeShouldNotOverrideGetterRunner(@Input val set: TypeSet) : AnalysisRunner() {
    override fun executeAnalysis(context: Context) {
        executeAnalysis(context, "Should not override getter", set, TypeShouldNotOverrideGetter())
    }
}

class TypeShouldNotOverrideSetterRunner(@Input val set: TypeSet) : AnalysisRunner() {
    override fun executeAnalysis(context: Context) {
        executeAnalysis(context, "Should not override setter", set, TypeShouldNotOverrideSetter())
    }
}

class ShouldNotReferenceInternalApiRunner(@Input val set: TypeSet) : AnalysisRunner() {
    override fun executeAnalysis(context: Context) {
        executeAnalysis(context, "Should not reference internal Gradle API", set, ShouldNotReferenceInternalApi())
    }
}

class FindTypeReferencesRunner(@Input val set: TypeSet, @Input val typeName: String) : AnalysisRunner() {
    override fun executeAnalysis(context: Context) {
        executeAnalysis(context, "References ${typeName}", set, FindTypeReferences(typeName))
    }
}

open class PluginAnalyzerExtension(objects: ObjectFactory) {
    val runners = objects.domainObjectSet(AnalysisRunner::class.java)

    fun analyze(runner: AnalysisRunner) {
        runners.add(runner)
    }

    val analyzedPlugins = objects.domainObjectContainer(AnalyzedPlugin::class.java)

    fun plugin(artifact: String, configuration: Action<in AnalyzedPlugin> = Actions.doNothing()) {
        analyzedPlugins.create(artifact, configuration)
    }
}

@Serializable
data class Message(val level: String, val message: String) : Comparable<Message> {
    override fun compareTo(other: Message) = message.compareTo(other.message)
}

@Serializable
data class MessageGroup(val title: String, val messages: List<Message>)

@Serializable
data class PluginReport(val messageGroups: List<MessageGroup>)

@CacheableTask
abstract class PluginAnalyzerTask : DefaultTask() {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Classpath
    abstract val runtime: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Input
    abstract val level: Property<Level>

    // TODO This should be @Nested, but then we get "not serializable" errors...
    @get:Input
    abstract val runners: ListProperty<AnalysisRunner>

    init {
        level.convention(Level.INFO)
    }

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    abstract class Work : WorkAction<Work.Params> {
        interface Params : WorkParameters {
            val classpath: ConfigurableFileCollection

            val runtime: ConfigurableFileCollection

            val reportFile: RegularFileProperty

            val level: Property<Level>

            val runners: ListProperty<AnalysisRunner>
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun execute() {
            val files = parameters.runtime.files.map(File::toPath) +
                parameters.classpath.files.map(File::toPath)
            val messageGroups = mutableListOf<MessageGroup>()
            val analyzer = DefaultAnalyzer(TypeRepository(files)) { arg ->
                when (arg) {
                    is IClass -> formatType(arg.name)
                    is TypeReference -> formatType(arg.name)
                    is TypeName -> formatType(arg)
                    is IField -> formatField(arg)
                    is IMethod -> formatMethod(arg)
                    else -> arg.toString()
                }
            }

            parameters.runners.get().forEach { runner ->
                runner.executeAnalysis(AnalysisRunner.Context(analyzer, parameters.level.get(), messageGroups))
            }

            val report = parameters.reportFile.get().asFile
            FileOutputStream(report).use { stream ->
                Json.encodeToStream(PluginReport(messageGroups), stream)
            }
        }

        private fun formatType(type: TypeName) = "type `${toFQCN(type.toString())}`"
        private fun formatField(field: IField) = "field `${toFQCN(field.declaringClass)}.${field.name}`"
        private fun formatMethod(method: IMethod): String {
            val paramTypes = mutableListOf<String>()
            // Ignore 'this` for instance methods
            val firstParam = if (method.isStatic) 0 else 1
            for (iParam in firstParam until method.numberOfParameters) {
                // Let's use the simple name of parameter types
                paramTypes += method.getParameterType(iParam).name.className.toString()
            }
            return "method `${toFQCN(method.declaringClass)}.${method.name}(${paramTypes.joinToString(",")})`"
        }
    }

    @TaskAction
    fun execute() {
        val task = this
        workerExecutor.processIsolation {
            forkOptions {
                // Seems to help analyze Kotlin plugins quite a bit
                maxHeapSize = "1g"
            }
        }
            .submit(Work::class) {
                classpath = task.classpath
                runtime = task.runtime
                reportFile = task.reportFile
                level = task.level
                runners = task.runners
            }

        workerExecutor.await()
    }
}

@CacheableTask
abstract class PluginAnalysisCollectorTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputReports: ConfigurableFileCollection

    @get:OutputFile
    abstract val aggregateReportFile: RegularFileProperty

    @OptIn(ExperimentalSerializationApi::class)
    @TaskAction
    fun execute() {
        val allPluginReports = MultimapBuilder
            .linkedHashKeys()
            .treeSetValues()
            .build<PluginReport, String>()

        // Merge plugin reports with the same contents
        inputReports.forEach { inputFile ->
            val pluginReport = FileInputStream(inputFile).use { stream ->
                Json.decodeFromStream<PluginReport>(stream)
            }
            var pluginId = inputFile.nameWithoutExtension
            allPluginReports.put(pluginReport, pluginId)
        }

        val report = aggregateReportFile.get().asFile
        PrintWriter(report).use { writer ->
            writer.println("# Plugin validation report")
            writer.println()
            writer.println("Produced at **${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}**")
            writer.println()

            allPluginReports.asMap().forEach { pluginReport, pluginIds ->
                writeReport(writer, pluginReport, pluginIds)
            }
        }

        println("Plugin analysis report: " + report.absolutePath)
    }

    fun writeReport(writer: PrintWriter, pluginReport: PluginReport, pluginIds: Collection<String>) {
        writer.println("## Plugin ${pluginIds.joinToString { formatPluginId(it) }} (${pluginReport.messageGroups.map { it.messages.size }.sum()})")
        writer.println()

        if (pluginReport.messageGroups.isNotEmpty()) {
            pluginReport.messageGroups.forEach { messageGroup ->
                writer.println("### ${messageGroup.title} (${messageGroup.messages.size})")
                writer.println()
                messageGroup.messages.forEach { message ->
                    val levelSymbol = when (message.level) {
                        "INFO" -> "\uD83D\uDCAC"
                        "WARN" -> "⚠\uFE0F"
                        "ERROR" -> "❌"
                        else -> "❓"
                    }
                    writer.println("- ${levelSymbol} ${message.message}")
                }
                writer.println()
            }
        } else {
            writer.println("No messages generated. Good plugin! ❤\uFE0F")
        }
        writer.println()
    }

    private fun formatPluginId(pluginId: String) = "[`${pluginId}`](https://plugins.gradle.org/plugin/${pluginId})"
}

open class PluginInfo(val artifact: String, val sourceUrl: String?) : Comparable<PluginInfo> {
    override fun compareTo(other: PluginInfo) = artifact.compareTo(other.artifact)
}

companion object Util {
    fun lookupPlugin(pluginId: String): PluginInfo {
        val client = HttpClient.newHttpClient()

        // Create an HttpRequest using the source ID
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://plugins.gradle.org/plugin/$pluginId"))
            .build()

        // Send the HTTP request
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        // Parse the HTML into a DOM
        val document = Jsoup.parse(response.body())

        // Find the <pre> tag
        val preElement = document.select("#kotlin-usage > pre:nth-child(4)").first()

        val artifact: String
        if (preElement != null) {
            val kotlinCode = preElement.text()

            val gavRegex = """classpath\("(.+?)"\)""".toRegex()
            val gavMatch = gavRegex.find(kotlinCode)

            if (gavMatch == null) {
                throw RuntimeException("No target ID found for source ID $pluginId")
            }
            artifact = gavMatch.groupValues[1]
            println("Analyzing plugin ${pluginId} with artifact '${artifact}'")
        } else {
            throw RuntimeException("No <pre> tag found for source ID $pluginId")
        }

        val sourceUrlElement = document.select("#content > div > div.row.plugin-detail.box > div.detail > p > a").first()
        val sourceUrl = sourceUrlElement?.attr("href")

        return PluginInfo(artifact, sourceUrl)
    }
}

val analyzePluginsTask = tasks.register<PluginAnalysisCollectorTask>("analyzePlugins") {
    aggregateReportFile = project.layout.buildDirectory.file("plugin-analysis/aggregate-report.md")
}

val pluginAnalyzer = extensions.create<PluginAnalyzerExtension>("pluginAnalyzer")

pluginAnalyzer.analyzedPlugins.all {
    val simplifiedName = pluginId.replace(':', '_').replace('.', '_')

    val config = configurations.create("conf_$simplifiedName")
    configureRequestAttributes(config)

    val defaultCoordinates = "$pluginId:$pluginId.gradle.plugin:latest.release"
    config.dependencies.addLater(
        coordinates.orElse(defaultCoordinates).map { dependencyFactory.create(it) }
    )

    val analyzeTask = tasks.register<PluginAnalyzerTask>("analyze_$simplifiedName") {
        classpath = config
        runtime.from(gradleRuntime)
        runners = pluginAnalyzer.runners
        reportFile = project.layout.buildDirectory.file("plugin-analysis/plugins/${pluginId}.json")
    }

    analyzePluginsTask.configure {
        inputReports.from(analyzeTask.flatMap { it.reportFile })
    }
}

/**
 * Use the same request attribute that Gradle will use to resolve the plugin classpath, as defined by:
 * [Link](https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/api/internal/initialization/DefaultScriptClassPathResolver.java#L52-L67)
 *
 * Note, that Gradle also sets the `TargetJvmVersion` attribute to the version of the JVM that this build is
 * running on. However, this would restrict the analyzer plugin to only resolve plugins that have a target Java
 * version less than or equal to the current JVM version.
 * Since the analyzer logic can seemingly run on a lower JVM version than the plugins that it analyzes,
 * we deviate from the conventional attributes by not setting the target JVM attribute.
 */
fun configureRequestAttributes(config: Configuration) {
    config.attributes {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
        attributes.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(GradlePluginApiVersion::class.java, GradleVersion.current().version))
    }
}
