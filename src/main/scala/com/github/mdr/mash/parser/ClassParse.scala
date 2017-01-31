package com.github.mdr.mash.parser

import com.github.mdr.mash.lexer.{ Token, TokenType }
import com.github.mdr.mash.lexer.TokenType._
import com.github.mdr.mash.parser.ConcreteSyntax._

import scala.collection.mutable.ArrayBuffer

trait ClassParse {
  self: MashParse ⇒

  protected def classDeclaration(): ClassDeclaration = {
    val classToken = consumeRequiredToken("class", CLASS)
    val name = consumeRequiredToken("class", IDENTIFIER)
    val params = classParamList()
    val bodyOpt = if (LBRACE) Some(classBody()) else None
    ClassDeclaration(docComment(classToken), classToken, name, params, bodyOpt)
  }

  private def classBody(): ClassBody = {
    val lbrace = nextToken()
    val methods: ArrayBuffer[Method] = ArrayBuffer()
    while (tokenCanStartAMethod) {
      val methodDecl = functionDeclaration()
      val semiOpt = consumeOptionalToken(SEMI)
      methods += Method(methodDecl, semiOpt)
    }
    val rbrace = consumeRequiredToken("class", RBRACE)
    ClassBody(lbrace, methods, rbrace)
  }

  private def tokenCanStartAMethod: Boolean = DEF || AT

}
