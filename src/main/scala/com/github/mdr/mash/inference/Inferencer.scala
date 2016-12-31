package com.github.mdr.mash.inference

import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.parser.AbstractSyntax._

/**
 * Inferencer used by type-inference strategies
 */
class Inferencer(typeInferencer: TypeInferencer, bindings: Map[String, Type]) {

  def applyFunction(functionType: Type, elementType: Type, functionExprOpt: Option[Expr]): Option[Type] = functionType match {
    case Type.DefinedFunction(f) ⇒
      val strategy = f.typeInferenceStrategy
      val args = Seq(positionArg(elementType))
      strategy.inferTypes(this, SimpleTypedArguments(args))
    case Type.BoundMethod(targetType, method) ⇒
      val strategy = method.typeInferenceStrategy
      val args = Seq(positionArg(elementType))
      strategy.inferTypes(this, Some(targetType), SimpleTypedArguments(args))
    case Type.Lambda(parameterModel, expr, lambdaBindings) ⇒
      parameterModel.params.map(_.name).headOption.flatMap { param ⇒
        typeInferencer.inferType(expr, lambdaBindings ++ bindings + (param -> elementType))
      }
    case Type.Instance(StringClass) | Type.Tagged(StringClass, _) ⇒
      functionExprOpt match {
        case Some(StringLiteral(s, _, _, _)) ⇒
          typeInferencer.memberLookup(elementType, s, immediateExec = true)
        case _ ⇒
          None
      }
    case _ ⇒
      None
  }

  private def positionArg(typ: Type) = TypedArgument.PositionArg(AnnotatedExpr(None, Some(typ)))

  def applyFunction2(functionType: Type, element1Type: Type, element2Type: Type): Option[Type] = functionType match {
    case Type.DefinedFunction(f) ⇒
      val strategy = f.typeInferenceStrategy
      val args = Seq(positionArg(element1Type), positionArg(element2Type))
      strategy.inferTypes(this, SimpleTypedArguments(args))
    case Type.BoundMethod(targetType, method) ⇒
      val strategy = method.typeInferenceStrategy
      val args = Seq(positionArg(element1Type), positionArg(element2Type))
      strategy.inferTypes(this, Some(targetType), SimpleTypedArguments(args))
    case Type.Lambda(parameterModel, expr, lambdaBindings) ⇒
      val paramBindings = parameterModel.params.map(_.name).zip(Seq(element1Type, element2Type)).toMap
      typeInferencer.inferType(expr, lambdaBindings ++ bindings ++ paramBindings)
    case _ ⇒
      None
  }

  def memberLookup(typ: Type, name: String): Option[Type] = typeInferencer.memberLookup(typ, name, immediateExec = true)

}
