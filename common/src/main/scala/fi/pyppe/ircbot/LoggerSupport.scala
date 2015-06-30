package fi.pyppe.ircbot

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger

trait LoggerSupport { self =>

  implicit lazy val logger: Logger =
    Logger(LoggerFactory.getLogger(self.getClass.getName.replaceAll("\\$$", "")))

}
