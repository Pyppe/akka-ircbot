package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.DateTime
import org.json4s._
import org.joda.time.format.DateTimeFormat
import java.util.Locale

object OpenWeatherMap extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._

  private case class Weather(time: String, temp: String, text: String, rain: Option[String])
  private val DefaultCountry = "FI"
  private val ShortCities = Map(
    "h" -> "Helsinki",
    "e" -> "Espoo",
    "j" -> "Järvenpää",
    "v" -> "Vantaa"
  )
  private val Count = 7
  private val WeekDay = DateTimeFormat.forPattern("E").withLocale(Locale.forLanguageTag("fi"))

  // http://api.openweathermap.org/data/2.5/forecast?q=helsinki,FI&units=metric&lang=fi
  def weather(city: String, country: String, daily: Boolean): Future[Option[String]] =
    Http(url(s"http://api.openweathermap.org/data/2.5/forecast${if (daily) "/daily" else ""}").
      addQueryParameter("q", s"$city,$country").
      addQueryParameter("units", "metric").
      addQueryParameter("cnt", s"$Count").
      addQueryParameter("lang", "fi").GET).
      map {
        case r if r.getStatusCode == 200 =>
          val json = parseJSON(r.getResponseBody)
          val city = (json \ "city" \ "name").extract[String]
          val country = (json \ "city" \ "country").extract[String]
          val id = (json \ "city" \ "id").extract[String]
          val link = s"http://openweathermap.org/city/$id"
          val list = (json \ "list") match {
            case JArray(items) =>
              items.map { obj =>
                if (daily) parseDailyItem(obj)
                else parseNonDailyItem(obj)
              }
          }
          val data = list.take(Count).map { w =>
            val rain = w.rain match {
              case Some(v) => s" ($v)"
              case _ => ""
            }
            s"${w.time} ${w.temp}, ${w.text}$rain"
          }.mkString(" | ")
          val smartCountry = if (country == DefaultCountry || country == "Finland") "" else s", $country"
          Some(s"$city$smartCountry: $data | $link")
        case _ => None
      } recover {
        case e: Exception =>
          logger.error("Error fetching weather data", e)
          None
      }

  private def parseNonDailyItem(obj: JValue) = {
    val dt = (obj \ "dt").extract[Long]
    val time = new DateTime(dt*1000)
    val temp = (obj \ "main" \ "temp").extract[Double].toInt + "°C"
    val text = (obj \ "weather" \ "description").extract[String]
    val rain = (obj \ "rain" \ "3h") match {
      case JNothing => None
      case x =>
        val rain = x.extract[String].toDouble
        if (rain > 0) Some(s"$rain mm/3h")
        else None
    }
    Weather(time.toString("HH:mm"), temp, text, rain)
  }

  private def parseDailyItem(obj: JValue) = {
    val dt = (obj \ "dt").extract[Long]
    val time = new DateTime(dt*1000)
    val tempDay = (obj \ "temp" \ "day").extract[Double].toInt + "°C"
    val text = (obj \\ "description").extract[String]
    val rain = (obj \ "rain") match {
      case JNothing => None
      case x =>
        val rain = x.extract[String].toDouble
        if (rain > 0) Some(s"$rain mm")
        else None
    }
    Weather(time.toString(WeekDay), tempDay, text, rain)
  }

  def queryWeather(q: String, daily: Boolean) = {
    val (city, country) = q.split(',').map(_.trim).filter(_.nonEmpty) match {
      case Array(city, country) => (city, country)
      case Array(city) => (city, DefaultCountry)
      case _ => ("Helsinki", DefaultCountry)
    }
    weather(ShortCities.getOrElse(city, city), country, daily)
  }

}
