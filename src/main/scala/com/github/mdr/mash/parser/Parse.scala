package com.github.mdr.mash.parser

import com.github.mdr.mash.lexer.{ DocComment ⇒ LexerDocComment, LexerResult, Token, TokenType }
import com.github.mdr.mash.lexer.TokenType._
import com.github.mdr.mash.utils.PointedRegion

import scala.language.implicitConversions

class Parse(lexerResult: LexerResult, initialForgiving: Boolean) {

  private val tokens = lexerResult.tokens.toArray

  /**
    * Index of the token currently being examined
    */
  private var pos = 0

  /**
    * Whether we are currently forgiving; we might temporarily stop being forgiving to do speculative parsing.
    */
  protected var forgiving: Boolean = initialForgiving

  /**
    * Indicate that a synthetic inferred semi was just inferred
    */
  private var inferredSemi: Boolean = false

  /**
    * Return the current token. If it's past the end of the token sequence, then return the last token (EOF).
    */
  protected def currentToken: Token = {
    val token = currentSequenceToken
    if (shouldInferSemicolon(token))
      syntheticToken(SEMI)
    else
      token
  }

  private def getToken(pos: Int): Token =
    if (pos < tokens.length)
      tokens(pos)
    else
      tokens.last

  private def currentSequenceToken: Token = getToken(pos)

  private def shouldInferSemicolon(token: Token): Boolean =
    lexerResult.inferredSemicolonCandidates.contains(token) && !inferredSemi

  protected def docComment(token: Token): Option[LexerDocComment] = lexerResult.docComments.get(token)

  private def currentTokenType = currentToken.tokenType

  /**
    * Lookahead in the token stream.
    */
  protected def lookahead(n: Int): TokenType = {
    require(n >= 0)
    // Step forward through the token stream to make use of the semicolon inference logic, then rewind
    val oldPos = pos
    val oldInferredSemi = inferredSemi
    try {
      for (_ ← 1 to n) nextToken()
      nextToken().tokenType
    } finally {
      pos = oldPos
      inferredSemi = oldInferredSemi
    }
  }

  protected def currentLocation = PointedRegion(currentToken.offset, currentToken.region)

  /**
    * Consume the current token, and advance to the next.
    *
    * @return the token before advancing
    */
  protected def nextToken(): Token = {
    val token = currentSequenceToken
    if (shouldInferSemicolon(token)) {
      inferredSemi = true
      syntheticToken(SEMI)
    } else {
      inferredSemi = false
      pos += 1
      token
    }
  }

  /**
    * We're testing token types a lot, a bit of shorthand helps.
    */
  protected implicit def tokenType2Boolean(tokenType: TokenType): Boolean = currentTokenType == tokenType

  protected def errorExpectedToken(contextOpt: Option[String], expected: String) = {
    val actual = if (currentToken.text.isEmpty) describeToken(currentToken.tokenType) else currentToken.text
    val message = contextOpt match {
      case Some(context) ⇒ s"While parsing $context, expected '$expected', but instead found '$actual'"
      case None ⇒ s"Expected '$expected', but instead found '$actual'"
    }
    throw new MashParserException(message, currentLocation)
  }

  /**
    * Speculatively parse from the current position. If it succeeds, we return Some(..), and any consumed tokens
    * remain consumed. Otherwise, we return None, and the state of the parse remains unchanged.
    */
  protected def speculate[T](p: ⇒ T): Option[T] = {
    val oldPos = pos
    val oldInferredSemi = inferredSemi
    val oldForgiving = forgiving
    forgiving = false
    try
      Some(p)
    catch {
      case _: MashParserException ⇒
        pos = oldPos
        inferredSemi = oldInferredSemi
        None
    } finally
      forgiving = oldForgiving
  }

  /**
    * Create a synthetic token of the given type
    */
  protected def syntheticToken(tokenType: TokenType): Token =
    syntheticToken(tokenType, afterTokenOpt = if (pos > 0) Some(getToken(pos - 1)) else None)

  private def syntheticToken(tokenType: TokenType, afterTokenOpt: Option[Token]): Token = {
    val offset = afterTokenOpt.map(_.region.posAfter) getOrElse 0
    val source = afterTokenOpt.map(_.source) getOrElse ""
    Token(tokenType, offset, length = 0, source)
  }

  /**
    * While loop that errors if it's not making progress
    */
  protected def safeWhile(cond: ⇒ Boolean)(body: ⇒ Any) {
    var oldPos = pos
    var noAdvanceCount = 0
    while (cond) {
      body
      if (oldPos == pos)
        noAdvanceCount += 1
      else
        noAdvanceCount = 0
      assert(noAdvanceCount < 10, "Infinite loop detected parsing at position " + pos + ", current token is " + currentToken)
      oldPos = pos
    }
  }

  protected def unexpectedToken() =
    if (EOF)
      throw new MashParserException(s"Unexpected end-of-input", currentLocation)
    else
      throw new MashParserException(s"Unexpected token '${currentToken.text}'", currentLocation)

  protected def consumeRequiredToken(contextOpt: Option[String], tokenType: TokenType): Token =
    if (tokenType)
      nextToken()
    else if (forgiving)
      syntheticToken(tokenType)
    else
      errorExpectedToken(contextOpt, describeToken(tokenType))

  protected def consumeRequiredToken(context: String, tokenType: TokenType): Token =
    consumeRequiredToken(Some(context), tokenType)

  protected def consumeRequiredToken(tokenType: TokenType): Token =
    consumeRequiredToken(None, tokenType)

  protected def describeToken(tokenType: TokenType) = TokenNames.getOrElse(tokenType, tokenType.toString)

  private val TokenNames: Map[TokenType, String] = Map(
    IDENTIFIER -> "identifier",
    RBRACE -> "}",
    RPAREN -> ")",
    RSQUARE -> "]",
    CLASS -> "class",
    COLON -> ":",
    THEN -> "then",
    NAMESPACE -> "namespace",
    SHORT_EQUALS -> "=",
    RIGHT_ARROW -> "->",
    STRING_END -> "end of string"
  )

}