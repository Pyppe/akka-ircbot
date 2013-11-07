package fi.pyppe.ircbot.slave

import fi.pyppe.ircbot.LoggerSupport
import org.joda.time.DateTime
import org.json4s._

object OpenWeatherMap extends LoggerSupport with JsonSupport {
  import dispatch._, Defaults._

  private case class Weather(time: String, temp: String, text: String, rain: Option[Double])
  private val DefaultCountry = "FI"
  private val ShortCities = Map(
    "h" -> "Helsinki",
    "e" -> "Espoo",
    "j" -> "Järvenpää",
    "v" -> "Vantaa"
  )

  // http://api.openweathermap.org/data/2.5/forecast?q=helsinki,FI&units=metric&lang=fi
  def weather(city: String, country: String): Future[Option[String]] =
    Http(url("http://api.openweathermap.org/data/2.5/forecast").
      addQueryParameter("q", s"$city,$country").
      addQueryParameter("units", "metric").
      addQueryParameter("lang", "fi").GET).
      map {
        case r if r.getStatusCode == 200 =>
          val json = parseJSON(r.getResponseBody)
          val city = (json \ "city" \ "name").extract[String]
          val country = (json \ "city" \ "country").extract[String]
          val id = (json \ "city" \ "id").extract[Long]
          val link = s"http://openweathermap.org/city/$id"
          val list = (json \ "list") match {
            case JArray(items) =>
              items.map { obj =>
                val dt = (obj \ "dt").extract[Long]
                val time = new DateTime(dt*1000)
                val temp = (obj \ "main" \ "temp").extract[Double].toInt + "°C"
                val text = (obj \ "weather" \ "description").extract[String]
                val rain = (obj \ "rain" \ "3h") match {
                  case JNothing => None
                  case x => Some(x.extract[String].toDouble)
                }
                Weather(time.toString("HH:mm"), temp, text, rain)
              }
          }
          val data = list.take(3).map { w =>
            val rain = w.rain match {
              case Some(v) if v > 0 => s" ($v mm/3h)"
              case _ => ""
            }
            s"${w.time} ${w.temp}, ${w.text}$rain"
          }.mkString(" | ")
          val smartCountry = if (country == DefaultCountry || country == "Finland") "" else s", $country"
          Some(s"$city$smartCountry: $data | $link")
        case _ => None
      } recover {
        case e: Exception =>
          logger.error("Error fetching weather data", e)
          None
      }

  def queryWeather(q: String) = {
    val (city, country) = q.split(',').map(_.trim).filter(_.nonEmpty) match {
      case Array(city, country) => (city, country)
      case Array(city) => (city, DefaultCountry)
      case _ => ("Helsinki", DefaultCountry)
    }
    weather(ShortCities.getOrElse(city, city), country)
  }

}
