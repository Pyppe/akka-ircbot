package fi.pyppe.ircbot.slave

import org.specs2.mutable._

class SlaveWorkerSpec extends Specification {

  val hsLink = "http://www.hs.fi/ulkomaat/Ensimmäiset+Venäjän+avustusrekat+pääsivät+Ukrainan+maaperälle/a1408672884734?ref=hs-breaking-news"

  "SlaveWorker.parseUrls" should {
    def test(input: String, urls : List[String]) =
      s"find [$urls] from [$input]" in {
        SlaveWorker.parseUrls(input) === urls
    }

    test("", List())
    test("http://", List())
    test("http://google.com", List("http://google.com"))
    test("https://google.com", List("https://google.com"))
    test("Some Text here http://www.google.com, cool", List("http://www.google.com"))
    test("http://www.google.com testing", List("http://www.google.com"))
    test("HTTP://WWW.GOOGLE.COM", List("HTTP://WWW.GOOGLE.COM"))

    test(s"diipa $hsLink! $hsLink, $hsLink?", List(hsLink, hsLink, hsLink))

    // some urls with entities, upper & lowercase
    test("http://www.hs.fi/ulkomaat/Valko-Ven%C3%A4j%C3%A4n+Luka%C5%A1enka+suunnittelee+maaorjuutta/a1401330071381",
      List("http://www.hs.fi/ulkomaat/Valko-Ven%C3%A4j%C3%A4n+Luka%C5%A1enka+suunnittelee+maaorjuutta/a1401330071381"))

    test("http://www.hs.fi/ulkomaat/valko-ven%c3%a4j%c3%a4n+luka%c5%a1enka+suunnittelee+maaorjuutta/a1401330071381",
      List("http://www.hs.fi/ulkomaat/valko-ven%c3%a4j%c3%a4n+luka%c5%a1enka+suunnittelee+maaorjuutta/a1401330071381"))
  }
}