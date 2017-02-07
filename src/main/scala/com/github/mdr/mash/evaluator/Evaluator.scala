package com.github.mdr.mash.evaluator

import com.github.mdr.mash.functions._
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.os.linux.LinuxEnvironmentInteractions
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.{ DocComment, QuotationType }
import com.github.mdr.mash.runtime._

import scala.PartialFunction.condOpt

case class EvaluationContext(scopeStack: ScopeStack, namespaceOpt: Option[Namespace] = None)

object Evaluator extends EvaluatorHelper {

  private val environmentInteractions = LinuxEnvironmentInteractions

  def evaluate(expr: Expr)(implicit context: EvaluationContext): MashValue = {
    try {
      ExecutionContext.checkInterrupted()
      val simpleResult = simpleEvaluate(expr)
      ExecutionContext.checkInterrupted()
      val finalResult = expr match {
        case _: Identifier | _: MemberExpr ⇒ invokeNullaryFunctions(simpleResult, sourceLocation(expr))
        case _                             ⇒ simpleResult
      }
      finalResult
    } catch {
      case e: EvaluatorException             ⇒
        throw e
      case e: EvaluationInterruptedException ⇒
        throw e
      case t: Exception                      ⇒
        throw EvaluatorException("Unexpected error in evaluation: " + t.toString,
          stack = sourceLocation(expr).toList.map(loc ⇒ StackTraceItem(loc)),
          cause = t)
    }
  }

  /**
    * If the given value is a function or bound method that allows nullary invocation, invoke it immediately and
    * return the result; otherwise, return the input value unchanged.
    */
  def invokeNullaryFunctions(value: MashValue, locationOpt: Option[SourceLocation]): MashValue =
    value match {
      case f: MashFunction if f.allowsNullary                     ⇒
        InvocationEvaluator.addInvocationToStackOnException(locationOpt, Some(f)) {
          f(Arguments())
        }
      case BoundMethod(target, method, _) if method.allowsNullary ⇒
        InvocationEvaluator.addInvocationToStackOnException(locationOpt) {
          method(target, Arguments())
        }
      case _                                                      ⇒ value
    }

  /**
    * Evaluate the given expression. If the result is a function/bound method that allows a nullary call, it is not called.
    */
  def simpleEvaluate(expr: Expr)(implicit context: EvaluationContext): MashValue =
    expr match {
      case Hole(_) | PipeExpr(_, _, _) | HeadlessMemberExpr(_, _, _) ⇒ // Should have been removed from the AST by now
        throw new EvaluatorException("Unexpected AST node: " + expr, sourceLocation(expr))
      case thisExpr: ThisExpr                                        ⇒ evaluateThisExpr(thisExpr)
      case interpolatedString: InterpolatedString                    ⇒ evaluateInterpolatedString(interpolatedString)
      case ParenExpr(body, _)                                        ⇒ evaluate(body)
      case blockExpr: BlockExpr                                      ⇒ evaluateBlockExpr(blockExpr)
      case Literal(v, _)                                             ⇒ v
      case memberExpr: MemberExpr                                    ⇒ MemberEvaluator.evaluateMemberExpr(memberExpr, invokeNullaryWhenVectorising = true).result
      case lookupExpr: LookupExpr                                    ⇒ LookupEvaluator.evaluateLookupExpr(lookupExpr)
      case invocationExpr: InvocationExpr                            ⇒ InvocationEvaluator.evaluateInvocationExpr(invocationExpr)
      case LambdaExpr(params, body, _)                               ⇒ makeAnonymousFunction(params, body)
      case binOp: BinOpExpr                                          ⇒ BinaryOperatorEvaluator.evaluateBinOpExpr(binOp)
      case chainedOpExpr: ChainedOpExpr                              ⇒ BinaryOperatorEvaluator.evaluateChainedOp(chainedOpExpr)
      case assExpr: AssignmentExpr                                   ⇒ AssignmentEvaluator.evaluateAssignment(assExpr)
      case assExpr: PatternAssignmentExpr                            ⇒ AssignmentEvaluator.evaluatePatternAssignment(assExpr)
      case ifExpr: IfExpr                                            ⇒ evaluateIfExpr(ifExpr)
      case ListExpr(elements, _)                                     ⇒ MashList(elements.map(evaluate(_)))
      case mishExpr: MishExpr                                        ⇒ MishEvaluator.evaluateMishExpr(mishExpr)
      case expr: MishInterpolation                                   ⇒ MishEvaluator.evaluateMishInterpolation(expr)
      case MishFunction(command, _)                                  ⇒ SystemCommandFunction(command)
      case decl: FunctionDeclaration                                 ⇒ evaluateFunctionDecl(decl)
      case decl: ClassDeclaration                                    ⇒ evaluateClassDecl(decl)
      case helpExpr: HelpExpr                                        ⇒ HelpEvaluator.evaluateHelpExpr(helpExpr)
      case StatementSeq(statements, _)                               ⇒ evaluateStatements(statements)
      case lit: StringLiteral                                        ⇒ evaluateStringLiteral(lit)
      case MinusExpr(subExpr, _)                                     ⇒ evaluateMinusExpr(subExpr)
      case identifier: Identifier                                    ⇒ evaluateIdentifier(identifier)
      case objectExpr: ObjectExpr                                    ⇒ evaluateObjectExpr(objectExpr)
    }

  def evaluateObjectExpr(objectExpr: ObjectExpr)(implicit context: EvaluationContext): MashObject = {
    def getFieldName(fieldNameExpr: Expr): String =
      fieldNameExpr match {
        case Identifier(name, _) ⇒
          name
        case _                   ⇒
          evaluate(fieldNameExpr) match {
            case MashString(s, _) ⇒ s
            case x                ⇒ throw new EvaluatorException("Invalid object label of type " + x.typeName, sourceLocation(fieldNameExpr))
          }
      }
    val fields = objectExpr.fields.map {
      case FullObjectEntry(field, value, _)           ⇒ getFieldName(field) -> evaluate(value)
      case entry @ ShorthandObjectEntry(field, sourceInfoOpt) ⇒
        val evaluatedIdentifier = evaluateIdentifier(field, sourceInfoOpt.map(_.location))
        val finalResult = invokeNullaryFunctions(evaluatedIdentifier, sourceLocation(entry))
        field -> finalResult
    }
    MashObject.of(fields)
  }

  def evaluateBlockExpr(blockExpr: BlockExpr)(implicit context: EvaluationContext): MashValue = {
    val newContext = context.copy(scopeStack = context.scopeStack.withLeakyScope(Seq()))
    Evaluator.evaluate(blockExpr.expr)(newContext)
  }

  def evaluateThisExpr(thisExpr: ThisExpr)(implicit context: EvaluationContext): MashValue = context.scopeStack.thisOpt.getOrElse {
    throw new EvaluatorException(s"No binding for 'this'", sourceLocation(thisExpr))
  }

  def evaluateIdentifier(identifier: Identifier)(implicit context: EvaluationContext): MashValue =
    evaluateIdentifier(identifier.name, sourceLocation(identifier))

  private def evaluateIdentifier(name: String, locationOpt: Option[SourceLocation])(implicit context: EvaluationContext): MashValue =
    context.scopeStack.lookup(name).getOrElse {
      throw new EvaluatorException(s"No binding for '$name'", locationOpt)
    }

  private def evaluateMinusExpr(subExpr: Expr)(implicit context: EvaluationContext): MashValue = evaluate(subExpr) match {
    case n: MashNumber ⇒ n.negate
    case x             ⇒ throw new EvaluatorException("Could not negate a value of type " + x.typeName, sourceLocation(subExpr))
  }

  def evaluateStringLiteral(lit: StringLiteral): MashValue = {
    val StringLiteral(s, quotationType, tildePrefix, _) = lit
    val tagOpt = condOpt(quotationType) { case QuotationType.Double ⇒ PathClass }
    val detilded = if (tildePrefix) environmentInteractions.home + s else s
    MashString(detilded, tagOpt)
  }

  private def evaluateStatements(statements: Seq[Expr])(implicit context: EvaluationContext): MashValue = {
    var result: MashValue = MashUnit
    for (statement ← statements)
      result = evaluate(statement)
    result
  }

  private def evaluateFunctionDecl(decl: FunctionDeclaration)(implicit context: EvaluationContext): UserDefinedFunction = {
    val function = userDefinedFunction(decl)
    context.scopeStack.set(function.name, function)
    function
  }

  private def evaluateClassDecl(decl: ClassDeclaration)(implicit context: EvaluationContext): UserDefinedClass = {
    val ClassDeclaration(docCommentOpt, _, className, paramList, bodyOpt, _) = decl
    val params = parameterModel(paramList, Some(context))

    def makeMethod(decl: FunctionDeclaration)(implicit context: EvaluationContext): UserDefinedMethod = {
      val FunctionDeclaration(docCommentOpt, attributes, functionName, paramList, body, _) = decl
      val methodParams = parameterModel(paramList, Some(context), docCommentOpt)
      val isPrivate = attributes.exists(_.name == Attributes.Private)
      UserDefinedMethod(docCommentOpt, functionName, methodParams, paramList, body, context, isPrivate)
    }
    val methods = bodyOpt.map(_.methods).getOrElse(Seq()).map(makeMethod)

    val klass = UserDefinedClass(docCommentOpt, className, context.namespaceOpt, params, methods)
    context.scopeStack.set(className, klass)
    klass
  }

  private def userDefinedFunction(decl: FunctionDeclaration)(implicit context: EvaluationContext): UserDefinedFunction = {
    val FunctionDeclaration(docCommentOpt, _, functionName, paramList, body, _) = decl
    val params = parameterModel(paramList, Some(context), docCommentOpt)
    UserDefinedFunction(docCommentOpt, functionName, params, body, context)
  }

  def makeParameter(param: FunctionParam,
                    evaluationContextOpt: Option[EvaluationContext] = None,
                    docCommentOpt: Option[DocComment] = None): Parameter = {
    val FunctionParam(_, nameOpt, isVariadic, defaultExprOpt, patternOpt, _) = param
    val defaultValueGeneratorOpt = evaluationContextOpt.flatMap(implicit context ⇒
      defaultExprOpt.map(defaultExpr ⇒ () ⇒ evaluate(defaultExpr)))
    val docSummaryOpt =
      for {
        name ← nameOpt
        docComment ← docCommentOpt
        paramComment ← docComment.getParamComment(name)
      } yield paramComment.summary

    Parameter(nameOpt, docSummaryOpt, defaultValueGeneratorOpt = defaultValueGeneratorOpt,
      isVariadic = isVariadic, isFlag = param.isFlag, isLast = param.isLast, isLazy = param.isLazy,
      isNamedArgsParam =  param.isNamedArgsParam, patternOpt = patternOpt.map(makeParamPattern))
  }

  private def makeParamEntry(entry: ObjectPatternEntry): ParamPattern.ObjectEntry =
    ParamPattern.ObjectEntry(entry.field, entry.valuePatternOpt map makeParamPattern)

  def makeParamPattern(pattern: Pattern): ParamPattern = pattern match {
    case ObjectPattern(entries, _)   ⇒ ParamPattern.Object(entries.map(makeParamEntry))
    case HolePattern(_)              ⇒ ParamPattern.Hole
    case IdentPattern(identifier, _) ⇒ ParamPattern.Ident(identifier)
    case ListPattern(patterns, _)    ⇒ ParamPattern.List(patterns.map(makeParamPattern))
  }

  def parameterModel(paramList: ParamList,
                     evaluationContextOpt: Option[EvaluationContext] = None,
                     docCommentOpt: Option[DocComment] = None): ParameterModel = {
    val evaluationContext = evaluationContextOpt.getOrElse(EvaluationContext(ScopeStack(Nil)))
    val parameters: Seq[Parameter] = paramList.params.map(makeParameter(_, Some(evaluationContext), docCommentOpt))
    for (context <- evaluationContextOpt)
      verifyParameters(paramList)(context)
    ParameterModel(parameters)
  }

  private def verifyParameters(paramList: ParamList)(implicit context: EvaluationContext) {
    val params = paramList.params
    if (params.count(_.isVariadic) > 1)
      throw new EvaluatorException("Multiple variadic parameters are not allowed")
    for ((name, params) ← params.groupBy(_.nameOpt).collect { case (Some(name), ps) if ps.length > 1 ⇒ name -> ps }.headOption)
      throw new EvaluatorException(s"Duplicate parameter $name", params.lastOption.flatMap(sourceLocation))
  }

  private def evaluateInterpolatedString(interpolatedString: InterpolatedString)(implicit context: EvaluationContext): MashString = {
    val InterpolatedString(start, parts, end, _) = interpolatedString
    val chunks =
      MashString(start, PathClass) +:
        parts.map {
          case StringPart(s)  ⇒ MashString(s, PathClass)
          case ExprPart(expr) ⇒ evaluate(expr) match {
            case ms: MashString ⇒ ms
            case x              ⇒ MashString(ToStringifier.stringify(x))
          }
        } :+ MashString(end, PathClass)
    chunks.reduce(_ + _)
  }

  private def makeAnonymousFunction(paramList: ParamList, body: Expr)(implicit context: EvaluationContext) =
    AnonymousFunction(parameterModel(paramList, Some(context)), body, context)

  private def evaluateIfExpr(ifExpr: IfExpr)(implicit context: EvaluationContext) = {
    val IfExpr(cond, body, elseOpt, _) = ifExpr
    val result = evaluate(cond)
    if (result.isTruthy)
      evaluate(body)
    else
      elseOpt.map(evaluate).getOrElse(MashUnit)
  }

}
