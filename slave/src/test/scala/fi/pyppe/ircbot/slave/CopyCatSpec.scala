package fi.pyppe.ircbot.slave

import org.specs2.mutable._

class CopyCatSpec extends Specification {

  "CopyCat.tokenize" should {
    def testEquals(message: String, expectedOutput: String) =
      s"convert [$message] into [$expectedOutput]" in {
        CopyCat.tokenize(message) === expectedOutput
      }

    def testNotEquals(a: String, b: String) =
      s"not covert [$a] and [$b] as same values" in {
        CopyCat.tokenize(a) !== CopyCat.tokenize(b)
      }

    testEquals("This is VERY interesting message, yo", "interesting is message this very yo")
    testEquals("Pyppe: I believe you, sir, are correct.", "are believe correct i pyppe sir you")
    testEquals("Huomenta!", "huomenta")
    testNotEquals("http://www.hs.fi/kaupunki/a1421641285419", "http://www.hs.fi/kaupunki/a1421390537338")
  }

}
