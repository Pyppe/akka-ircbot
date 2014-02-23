package fi.pyppe.ircbot.slave

import org.specs2.mutable._

class CopyCatSpec extends Specification {

  "CopyCat.tokenize" should {
    def test(message: String, expectedOutput: String) =
      s"convert [$message] into [$expectedOutput]" in {
        CopyCat.tokenize(message) === expectedOutput
      }
    test("This is VERY interesting message, yo", "interesting is message this very yo")
    test("Pyppe: I believe you, sir, are correct.", "are believe correct i pyppe sir you")
  }

}
