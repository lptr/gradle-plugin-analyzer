package org.gradlex.plugins.analyzer.analysis

import com.google.common.collect.ImmutableList
import org.codehaus.groovy.control.CompilerConfiguration
import org.gradlex.plugins.analyzer.Analyzer
import org.gradlex.plugins.analyzer.DefaultAnalyzer
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import spock.lang.Specification

import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import java.nio.file.Path
import java.nio.file.Paths

class AbstractAnalysisSpec extends Specification {
    String gradleApi
    String localGroovy
    String localKotlin
    List<String> reports
    List<Path> files
    Analyzer analyzer
    File sourceDirectory = new File("build/test-classes/${getClass().simpleName}/sources")
    File targetDirectory = new File("build/test-classes/${getClass().simpleName}/classes")

    def setup() {
        assert sourceDirectory.deleteDir()
        assert sourceDirectory.mkdirs()
        assert targetDirectory.deleteDir()
        assert targetDirectory.mkdirs()

        gradleApi = System.getProperty("gradle-api")
        localGroovy = System.getProperty("local-groovy")
        localKotlin = System.getProperty("local-kotlin")
        files = [Paths.get(gradleApi), targetDirectory.toPath()]
        reports = []
    }

    protected Analyzer getAnalyzer() {
        analyzer = new DefaultAnalyzer(files, { level, message ->
            reports += "$level: $message" as String
        })
    }

    protected List<String> getReports() {
        ImmutableList.sortedCopyOf(reports)
    }

    protected void compileJava(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()

        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///HelloWorld.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source
            }
        }

        assert compiler.getTask(
            null,
            null,
            null,
            ImmutableList.of(
                "-d", targetDirectory.absolutePath,
                "-classpath", localGroovy + File.pathSeparator + gradleApi
            ),
            null,
            ImmutableList.of(file))
            .call()
    }

    protected void compileKotlin(String source) {
        def sourceFile = new File(sourceDirectory, "source.kt")
        sourceFile.text = source

        def args = new K2JVMCompilerArguments()
        args.with {
            freeArgs = [sourceFile.absolutePath]
            destination = targetDirectory.absolutePath
            classpath = [localGroovy, localKotlin, gradleApi, *files].join(File.pathSeparator)
            // We are passing it in via the classpath
            noStdlib = true
        }

        def exitCode = new K2JVMCompiler()
            .execImpl(
                new PrintingMessageCollector(System.err, MessageRenderer.GRADLE_STYLE, false),
                Services.EMPTY,
                args)
        if (exitCode != ExitCode.OK) {
            throw new RuntimeException("Compilation failure: $exitCode")
        }
    }

    protected void compileGroovy(String source) {
        CompilerConfiguration config = new CompilerConfiguration()
        config.setTargetDirectory(targetDirectory)
        GroovyClassLoader classLoader
        classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, config)
        classLoader.addClasspath(gradleApi)

        classLoader.parseClass(source)
    }
}
