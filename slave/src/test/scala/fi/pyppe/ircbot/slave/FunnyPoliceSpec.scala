package fi.pyppe.ircbot.slave

import org.specs2.mutable._

class FunnyPoliceSpec extends Specification {

  "FunnyPolice.isFunnyText" should {
    testFunny("Diipa daapa Faufiu", false)
    testFunny(":Df", false)
    testFunny("( not funny )", false)
    testFunny(":\\", false)

    testFunny(":DD", true)
    testFunny(":)", true)
    testFunny("haha", true)
    testFunny("very funny man! :P", true)
    testFunny(":D:D", true)
    testFunny("very :----D funny", true)
  }

  private def testFunny(text: String, expected: Boolean): Unit =
    s"give $expected for [$text]" in {
      FunnyPolice.isFunnyText(text) === expected
    }

}
