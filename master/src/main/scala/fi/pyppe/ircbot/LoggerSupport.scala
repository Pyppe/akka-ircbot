package fi.pyppe.ircbot

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.slf4j.Logger

trait LoggerSupport { self =>

  implicit lazy val logger: Logger =
    Logger(LoggerFactory.getLogger(self.getClass.getName.replaceAll("\\$$", "")))

}
