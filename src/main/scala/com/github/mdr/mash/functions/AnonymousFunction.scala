package com.github.mdr.mash.functions

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.EvaluationContext
import com.github.mdr.mash.evaluator.Evaluator
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.parser.AbstractSyntax._

case class AnonymousFunction(params: ParameterModel, body: Expr, context: EvaluationContext) extends MashFunction(nameOpt = None) {

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val pairs =
      for (param ← params.params)
        yield param.name -> boundParams(param)
    val newScopeStack = context.scopeStack.withLambdaScope(pairs)
    Evaluator.evaluate(body)(context.copy(scopeStack = newScopeStack))
  }

  override def summary = "anonymous function"

}