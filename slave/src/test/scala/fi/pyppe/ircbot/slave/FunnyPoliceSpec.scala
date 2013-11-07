package fi.pyppe.ircbot.slave

import org.specs2.mutable._

class FunnyPoliceSpec extends Specification {

  "FunnyPolice.isFunnyText" should {
    testFunny("Diipa daapa Faufiu", false)
    testFunny(":Df", false)
    testFunny(":Df", false)

    testFunny(":DD", true)
    testFunny(":)", true)
    testFunny("haha", true)
  }

  private def testFunny(text: String, expected: Boolean): Unit =
    s"give $expected for [$text]" in {
      FunnyPolice.isFunnyText(text) === expected
    }

}
