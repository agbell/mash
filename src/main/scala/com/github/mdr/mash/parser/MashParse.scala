package com.github.mdr.mash.parser

import scala.PartialFunction.cond
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

import com.github.mdr.mash.lexer.LexerResult
import com.github.mdr.mash.lexer.Token
import com.github.mdr.mash.lexer.TokenType._
import com.github.mdr.mash.parser.ConcreteSyntax._

class MashParse(lexerResult: LexerResult, initialForgiving: Boolean)
    extends Parse(lexerResult, initialForgiving)
    with InterpolatedStringParse 
    with ObjectParse 
    with MishParse 
    with FunctionParse
    with InvocationParse {

  def program(): Expr = {
    val result = statementSeq()
    if (!EOF && !forgiving)
      errorExpectedToken("end of input")
    result
  }

  private def statementExpr(): Expr =
    if (DEF)
      functionDeclaration()
    else
      pipeExpr()

  protected def pipeExpr(): Expr = {
    val expr = lambdaExpr(mayContainPipe = true)
    continuePipeExpr(expr)
  }

  private def continuePipeExpr(previousExpr: Expr): Expr =
    if (PIPE) {
      val pipeToken = nextToken()
      val right = lambdaExpr(mayContainPipe = false)
      val pipeExpr = PipeExpr(previousExpr, pipeToken, right)
      continuePipeExpr(pipeExpr)
    } else
      previousExpr

  protected def assignmentExpr(): Expr = {
    val left = ifExpr()
    if (SHORT_EQUALS || PLUS_EQUALS || MINUS_EQUALS || TIMES_EQUALS || DIVIDE_EQUALS) {
      val equals = nextToken()
      val aliasOpt = if (ALIAS) Some(nextToken()) else None
      val right = pipeExpr()
      AssignmentExpr(left, equals, aliasOpt, right)
    } else
      left
  }

  private def ifExpr(): Expr =
    if (IF) {
      val ifToken = nextToken()
      val cond = orExpr()
      val thenToken =
        if (THEN)
          nextToken()
        else if (forgiving) {
          val lastTokenOfCond = cond.tokens.last
          Token(THEN, lastTokenOfCond.region.posAfter, 0, lastTokenOfCond.source)
        } else
          errorExpectedToken("then")
      val body = pipeExpr()
      val elseOpt =
        if (ELSE) {
          val elseToken = nextToken()
          val elseBody = pipeExpr()
          Some(elseToken, elseBody)
        } else
          None
      IfExpr(ifToken, cond, thenToken, body, elseOpt)
    } else
      orExpr()

  private def orExpr(): Expr = {
    val expr = andExpr()
    if (OR) {
      val or = nextToken()
      val right = orExpr()
      BinOpExpr(expr, or, right)
    } else
      expr
  }

  private def andExpr(): Expr = {
    val expr = comparisonExpr()
    if (AND) {
      val and = nextToken()
      val right = andExpr()
      BinOpExpr(expr, and, right)
    } else
      expr
  }

  private def continueChaining(op: Token): Boolean = cond(op.tokenType) {
    case LESS_THAN | LESS_THAN_EQUALS       ⇒ LESS_THAN || LESS_THAN_EQUALS
    case GREATER_THAN | GREATER_THAN_EQUALS ⇒ GREATER_THAN || GREATER_THAN_EQUALS
  }

  private def comparisonExpr(): Expr = {
    val expr = additiveExpr()
    if (LONG_EQUALS || NOT_EQUALS || GREATER_THAN || GREATER_THAN_EQUALS || LESS_THAN_EQUALS || LESS_THAN) {
      val op = nextToken()
      val right = additiveExpr()
      if (continueChaining(op)) {
        val opExprs = ArrayBuffer(op -> right)
        safeWhile(continueChaining(op)) {
          val op2 = nextToken()
          val right2 = additiveExpr()
          opExprs += (op2 -> right2)
        }
        ChainedOpExpr(expr, opExprs)
      } else
        BinOpExpr(expr, op, right)
    } else
      expr
  }

  private def additiveExpr(): Expr = {
    var expr = multiplicativeExpr()
    safeWhile(PLUS | MINUS) {
      val op = nextToken()
      val right = multiplicativeExpr()
      expr = BinOpExpr(expr, op, right)
    }
    expr
  }

  private def multiplicativeExpr(): Expr = {
    var expr = invocationExpr()
    safeWhile(TIMES | DIVIDE) {
      val op = nextToken()
      val right = invocationExpr()
      expr = BinOpExpr(expr, op, right)
    }
    expr
  }
  
  protected def prefixExpr(): Expr =
    if (MINUS) {
      val minus = nextToken()
      val expr = prefixExpr()
      MinusExpr(minus, expr)
    } else if (DOT || DOT_NULL_SAFE) {
      val dotToken = nextToken()
      val identifier =
        if (IDENTIFIER)
          nextToken()
        else if (forgiving)
          syntheticToken(IDENTIFIER, dotToken)
        else
          errorExpectedToken("identifier")
      continueSuffixExpr(HeadlessMemberExpr(dotToken, identifier))
    } else
      suffixExpr()

  private def suffixExpr(): Expr = {
    val expr = primaryExpr()
    continueSuffixExpr(expr)
  }

  private def continueSuffixExpr(previousExpr: Expr): Expr =
    if (DOT || DOT_NULL_SAFE)
      continueSuffixExpr(memberExpr(previousExpr))
    else if (LSQUARE_LOOKUP)
      continueSuffixExpr(lookupExpr(previousExpr))
    else if (LPAREN_INVOKE)
      continueSuffixExpr(parenInvocationExpr(previousExpr))
    else if (QUESTION)
      continueSuffixExpr(helpExpr(previousExpr))
    else
      previousExpr

  private def helpExpr(previousExpr: Expr): HelpExpr = {
    val question = nextToken()
    HelpExpr(previousExpr, question)
  }

  private def memberExpr(previousExpr: Expr): MemberExpr = {
    val dotToken = nextToken()
    val identifier =
      if (IDENTIFIER)
        nextToken()
      else if (forgiving)
        syntheticToken(IDENTIFIER, dotToken)
      else
        errorExpectedToken("identifier")
    MemberExpr(previousExpr, dotToken, identifier)
  }

  private def lookupExpr(previousExpr: Expr): LookupExpr = {
    val lsquare = nextToken()
    val indexExpr = pipeExpr()
    val rsquare =
      if (RSQUARE)
        nextToken()
      else if (forgiving)
        syntheticToken(RSQUARE, indexExpr.tokens.last)
      else
        errorExpectedToken("]")
    LookupExpr(previousExpr, lsquare, indexExpr, rsquare)
  }

  private def primaryExpr(): Expr =
    if (NUMBER_LITERAL || STRING_LITERAL || TRUE || FALSE || NULL)
      Literal(nextToken())
    else if (STRING_START)
      interpolatedString()
    else if (IDENTIFIER)
      Identifier(nextToken())
    else if (MISH_WORD)
      MishFunction(nextToken())
    else if (HOLE)
      Hole(nextToken())
    else if (LPAREN)
      parenExpr()
    else if (LSQUARE)
      listExpr()
    else if (LBRACE)
      speculate(objectExpr()) getOrElse blockExpr()
    else if (MISH_INTERPOLATION_START || MISH_INTERPOLATION_START_NO_CAPTURE)
      mishInterpolation()
    else if (forgiving)
      Literal(syntheticToken(STRING_LITERAL))
    else
      unexpectedToken()

  private def statementSeq(): Expr = {
    var statements = ArrayBuffer[Statement]()
    var continue = true
    safeWhile(continue) {
      if (RBRACE || RPAREN || EOF)
        continue = false
      else if (SEMI) {
        val semi = nextToken()
        statements += Statement(statementOpt = None, Some(semi))
      } else {
        val statement = statementExpr()
        if (SEMI) {
          val semi = nextToken()
          statements += Statement(Some(statement), Some(semi))
        } else {
          statements += Statement(Some(statement), semiOpt = None)
          continue = false
        }
      }
    }
    statements match {
      case Seq(Statement(Some(statement), None)) ⇒ statement
      case _                                     ⇒ StatementSeq(statements)
    }
  }

  private def blockExpr(): BlockExpr = {
    val lbrace = nextToken()
    val statements = statementSeq()
    val rbrace =
      if (RBRACE)
        nextToken()
      else if (forgiving)
        syntheticToken(RBRACE)
      else
        errorExpectedToken("}")
    BlockExpr(lbrace, statements, rbrace)
  }

  private def parenExpr(): Expr = {
    val lparen = nextToken()
    val expr = statementSeq()
    val rparen =
      if (RPAREN)
        nextToken()
      else if (forgiving)
        syntheticToken(RPAREN, expr.tokens.lastOption getOrElse lparen)
      else
        errorExpectedToken(")")
    ParenExpr(lparen, expr, rparen)
  }

  private def listExpr(): Expr = {
    val lsquare = nextToken()
    if (RSQUARE) {
      val rsquare = nextToken()
      ListExpr(lsquare, None, rsquare)
    } else {
      val firstItem = pipeExpr()
      val items = ArrayBuffer[(Token, Expr)]()
      safeWhile(COMMA) {
        val comma = nextToken()
        val item = pipeExpr()
        items += (comma -> item)
      }
      val rsquare =
        if (RSQUARE)
          nextToken()
        else if (forgiving) {
          val lastExpr = (firstItem +: items.map(_._2)).last
          val lastToken = lastExpr.tokens.last
          syntheticToken(RSQUARE, lastToken)
        } else
          errorExpectedToken("]")
      ListExpr(lsquare, Some(ListExprContents(firstItem, items)), rsquare)
    }
  }

}