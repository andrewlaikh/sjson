package io.github.shopee.idata.sjson

import scala.collection.mutable.ListBuffer

case class JSONToken(tokenType: Int, text: String, startIndex: Int = 0)

object JSONToken {
  val STRING = 0
  val NUMBER = 1

  val TRUE  = 2
  val FALSE = 3
  val NULL  = 4

  val LEFT_PARAN  = 5 // {
  val RIGHT_PARAN = 6 // }

  val LEFT_BRACKET  = 7 // [
  val RIGHT_BRACKET = 8 // ]

  val COMMA = 9  // ,
  val COLON = 10 // :
}

object TokenParser {
  def toTokens(jsonTxt: String): List[JSONToken] = {
    val tokensBuilder = ListBuffer[JSONToken]()
    val jsonTxtLen    = jsonTxt.length;

    var i = 0
    while (i < jsonTxtLen) {
      val token = getToken(jsonTxt, jsonTxtLen, i)
      if (token != null) {
        tokensBuilder.append(token)
        i += token.text.length // move i
      } else { // ignore
        i += 1
      }
    }

    tokensBuilder.toList
  }

  private def getToken(jsonTxt: String, jsonTxtLen: Int, i: Int): JSONToken = {
    val ch = jsonTxt(i)
    ch match {
      case '{'                                          => JSONToken(JSONToken.LEFT_BRACKET, ch + "", i)
      case '}'                                          => JSONToken(JSONToken.RIGHT_BRACKET, ch + "", i)
      case '['                                          => JSONToken(JSONToken.LEFT_PARAN, ch + "", i)
      case ']'                                          => JSONToken(JSONToken.RIGHT_PARAN, ch + "", i)
      case ','                                          => JSONToken(JSONToken.COMMA, ch + "", i)
      case ':'                                          => JSONToken(JSONToken.COLON, ch + "", i)
      case ' '                                          => null // white space
      case '\t'                                         => null // white space
      case '\r'                                         => null // white space
      case '\n'                                         => null // white space
      case '\f'                                         => null // white space
      case '\b'                                         => null // white space
      case '"'                                          => getStringToken(jsonTxt, jsonTxtLen, i)
      case _ if (ch == '-' || (ch >= '0' && ch <= '9')) => getNumberToken(jsonTxt, jsonTxtLen, i)
      case _ if (i + 3 < jsonTxtLen && jsonTxt.substring(i, i + 4) == "true") =>
        JSONToken(JSONToken.TRUE, "true", i)
      case _ if (i + 4 < jsonTxtLen && jsonTxt.substring(i, i + 5) == "false") =>
        JSONToken(JSONToken.FALSE, "false", i)
      case _ if (i + 3 < jsonTxtLen && jsonTxt.substring(i, i + 4) == "null") =>
        JSONToken(JSONToken.NULL, "null", i)

      case _ =>
        throw new Exception(
          tokenParseError(
            jsonTxt,
            i,
            s"unrecorgnized symbol '$ch', the int value of char is ${ch.toInt}."
          )
        )
    }
  }

  private def getNumberToken(jsonTxt: String, jsonTxtLen: Int, i: Int): JSONToken = {
    val ch = jsonTxt(i)
    var j  = i

    val numberBuilder = new StringBuilder
    // negative symbol, -
    if (ch == '-') {
      numberBuilder.append(ch)
      j += 1
    }

    // integer part, 01234
    while (j < jsonTxtLen && jsonTxt(j) >= '0' && jsonTxt(j) <= '9') {
      numberBuilder.append(jsonTxt(j))
      j += 1
    }

    // fragment part, .01234
    if (j < jsonTxt.length && jsonTxt(j) == '.') {
      numberBuilder.append(jsonTxt(j))
      j += 1

      while (j < jsonTxtLen && jsonTxt(j) >= '0' && jsonTxt(j) <= '9') {
        numberBuilder.append(jsonTxt(j))
        j += 1
      }
    }

    // science part
    if (j < jsonTxtLen && (jsonTxt(j) == 'e' || jsonTxt(j) == 'E')) {
      numberBuilder.append(jsonTxt(j))
      j += 1

      if (j < jsonTxtLen && (jsonTxt(j) == '+' || jsonTxt(j) == '-')) {
        numberBuilder.append(jsonTxt(j))
        j += 1
      }

      while (j < jsonTxtLen && (jsonTxt(j) >= '0' && jsonTxt(j) <= '9')) {
        numberBuilder.append(jsonTxt(j))
        j += 1
      }
    }

    val numberText = numberBuilder.toString()
    if (numberText == "-") {
      throw new Exception(tokenParseError(jsonTxt, j, "expect number after '-'"))
    }

    JSONToken(JSONToken.NUMBER, numberText, i)
  }

  /**
    *string = quotation-mark *char quotation-mark
    *char = unescaped /
    *       escape (
    *           %x22 /          ; "    quotation mark  U+0022
    *           %x5C /          ; \    reverse solidus U+005C
    *           %x2F /          ; /    solidus         U+002F
    *           %x62 /          ; b    backspace       U+0008
    *           %x66 /          ; f    form feed       U+000C
    *           %x6E /          ; n    line feed       U+000A
    *           %x72 /          ; r    carriage return U+000D
    *           %x74 /          ; t    tab             U+0009
    *           %x75 4HEXDIG )  ; uXXXX                U+XXXX
    *
    *escape = %x5C              ; \
    *
    *quotation-mark = %x22      ; "
    *
    *unescaped = %x20-21 / %x23-5B / %x5D-10FFFF
    */
  private def getStringToken(jsonTxt: String, jsonTxtLen: Int, i: Int): JSONToken = {
    val txtBuilder = new StringBuilder // use txt builder to collect text
    txtBuilder.append("\"")
    var j      = i + 1
    var closed = false

    // try to find another " with out escaped
    while (j < jsonTxtLen && !closed) {
      // TODO make this stronger
      // http://json.org
      if (jsonTxt(j) == '\\') {
        if (j + 2 > jsonTxtLen) {
          throw new Exception(tokenParseError(jsonTxt, j, "text should not end up with \\."))
        }
        txtBuilder.append(jsonTxt(j))
        txtBuilder.append(jsonTxt(j + 1))
        j += 2
      } else if (jsonTxt(j) == '"') {
        closed = true
        txtBuilder.append('"')
        j += 1
      } else {
        txtBuilder.append(jsonTxt(j))
        j += 1
      }
    }

    if (!closed) {
      throw new Exception(
        tokenParseError(jsonTxt, j, """missing '"' to close string text.""")
      )
    }

    JSONToken(JSONToken.STRING, txtBuilder.toString(), i)
  }

  private def tokenParseError(txt: String, location: Int, errorMessage: String): String = {
    val prev       = if (location - 5 <= 0) 0 else location - 5
    val after      = if (location + 5 >= txt.length) txt.length - 1 else location + 5
    val prevNearBy = if (prev > location) "" else txt.substring(prev, location)
    val afterNearBy =
      if (location + 1 > after) "" else txt.substring(location + 1, after)
    val cur = if (location < txt.length) txt(location) else ""
    s"""[${location}]${errorMessage}. Error happened nearby '${prevNearBy} >${cur}< ${afterNearBy}'."""
  }
}
