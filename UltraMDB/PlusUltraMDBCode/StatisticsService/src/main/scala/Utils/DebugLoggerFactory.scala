// ===== src/main/scala/Utils/DebugLoggerFactory.scala (FIXED) =====

package Utils

import java.io.{BufferedWriter, File, FileWriter}
import org.slf4j.{Logger, LoggerFactory}
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.FileAppender
import scala.util.Try

/**
 * 一个用于调试的自定义LoggerFactory。
 * 它可以为特定的类创建一个重定向到文件的专用Logger，
 * 而为所有其他类返回标准的Logger。
 */
object DebugLoggerFactory {

  // 在这里列出需要特殊调试的类的简单名称
  private val classesToDebug: Set[String] = Set(
    "GetUserPortraitUtils"
  )

  /**
   * 获取一个Logger实例。
   * 如果传入的类名在 `classesToDebug` 列表中，则返回一个专用的文件Logger。
   * 否则，返回一个由SLF4J管理的标准Logger。
   *
   * @param clazz 需要日志记录的类
   * @return 一个Logger实例
   */
  // [FIXED] 将 Class[_] 修改为 Class[?] 以符合Scala 3的规范
  def getLogger(clazz: Class[?]): Logger = {
    val className = clazz.getSimpleName
    if (classesToDebug.contains(className)) {
      // 如果是需要调试的类，创建并返回专用文件Logger
      createDedicatedFileLogger(className)
    } else {
      // 否则，返回普通Logger
      LoggerFactory.getLogger(clazz)
    }
  }

  /**
   * 动态创建一个将日志写入独立文件的Logger。
   */
  private def createDedicatedFileLogger(className: String): Logger = {
    val logFilePath = s"${className.toLowerCase}_debug.log"
    // 在JVM启动时，清空一次旧的日志文件
    Try(new File(logFilePath).delete())

    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    val fileAppender = new FileAppender[ch.qos.logback.classic.spi.ILoggingEvent]()
    fileAppender.setContext(context)
    fileAppender.setName(s"debug-appender-for-$className")
    fileAppender.setFile(logFilePath)
    fileAppender.setAppend(true) // 使用追加模式

    val encoder = new PatternLayoutEncoder()
    encoder.setContext(context)
    encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level - %msg%n")
    encoder.start()

    fileAppender.setEncoder(encoder)
    fileAppender.start()

    // 获取特定类的Logger，并为其配置Appender
    val logger = context.getLogger(s"debug.$className")
    logger.detachAndStopAllAppenders() // 移除任何可能从配置继承的Appender
    logger.addAppender(fileAppender)
    logger.setLevel(Level.INFO)
    logger.setAdditive(false) // 关键：阻止日志冒泡到root logger

    logger
  }
}