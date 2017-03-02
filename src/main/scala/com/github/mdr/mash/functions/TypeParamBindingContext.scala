package com.github.mdr.mash.functions

import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core.BooleanClass
import com.github.mdr.mash.runtime.MashBoolean

import scala.PartialFunction.cond

object TypeParamBindingContext {

  def bindPatternParam(pattern: ParamPattern, typeOpt: Option[Type]): Map[String, Type] =
    pattern match {
      case ParamPattern.Object(entries)   ⇒
        val fieldTypes: Map[String, Type] = typeOpt.map {
          case Type.Object(knownFields) ⇒ knownFields
          case Type.Instance(klass)     ⇒ klass.fieldsMap.mapValues(_.fieldType)
          case _                        ⇒ Map[String, Type]()
        }.getOrElse(Map())
        def inferEntry(entry: ParamPattern.ObjectEntry): Map[String, Type] =
          entry match {
            case ParamPattern.ObjectEntry(fieldName, None)          ⇒
              Map(fieldName -> fieldTypes.getOrElse(fieldName, Type.Any))
            case ParamPattern.ObjectEntry(fieldName, Some(valuePattern)) ⇒
              bindPatternParam(valuePattern, fieldTypes.get(fieldName))
          }
        entries.flatMap(inferEntry).toMap
      case ParamPattern.Hole              ⇒
        Map()
      case ParamPattern.Ident(identifier) ⇒
        Map(identifier -> typeOpt.getOrElse(Type.Any))
      case ParamPattern.List(patterns)    ⇒
        val elementTypeOpt = typeOpt.collect { case Type.Seq(elementType) ⇒ elementType }
        patterns.flatMap(bindPatternParam(_, elementTypeOpt)).toMap
    }

  def bindParam(param: Parameter, argTypeOpt: Option[Type]): Map[String, Type] =
    param.patternOpt match {
      case Some(pattern) ⇒
        bindPatternParam(pattern, argTypeOpt)
      case None          ⇒
        (for {
          paramName ← param.nameOpt
        } yield paramName -> argTypeOpt.getOrElse(Type.Any)).toMap
    }

}

class TypeParamBindingContext(params: ParameterModel, arguments: TypedArguments) {
  import TypeParamBindingContext._

  private var boundArguments: Map[String, ValueInfo] = Map()
  private var boundNames: Map[String, Type] = Map()
  private var posToParam: Map[Int, Parameter] = Map()
  private var lastParameterConsumed = false

  def bind(): BoundTypeParams = {
    handleLastArg()
    handlePositionalArgs()
    handleFlagArgs()
    BoundTypeParams(boundArguments, boundNames, posToParam)
  }

  private def handleLastArg() {
    for {
      lastParam ← params.lastParamOpt
      paramName = lastParam.nameOpt
      lastArg ← arguments.positionArgs.lastOption
      if !lastParam.nameOpt.exists(arguments.isProvidedAsNamedArg)
    } {
      lastParameterConsumed = true
      lastParam.nameOpt.foreach(boundArguments += _ -> lastArg)
      for {
        argType ← lastArg.typeOpt
        paramName ← lastParam.nameOpt
      } boundNames += paramName -> argType
      posToParam += posOfArg(lastArg) -> lastParam
    }
  }

  private def handlePositionalArgs() = {
    val regularPosParams = params.positionalParams.filterNot(p ⇒ p.isVariadic || p.isLast)
    val positionArgs = if (lastParameterConsumed) arguments.positionArgs.init else arguments.positionArgs

    if (positionArgs.size > regularPosParams.size)
      for (variadicParam ← params.variadicParamOpt) {
        val varargs = positionArgs.drop(regularPosParams.size)
        val varargType = varargs.flatMap(_.typeOpt).headOption.getOrElse(Type.Any).seq
        variadicParam.nameOpt.foreach(boundArguments += _ -> ValueInfo(None, Some(varargType)))
        variadicParam.nameOpt.foreach(boundNames += _ -> varargType)
        val extraArgs = positionArgs.drop(regularPosParams.size)
        for (arg ← extraArgs)
          posToParam += posOfArg(arg) -> variadicParam
      }

    for ((param, arg) ← regularPosParams zip positionArgs) {
      val argTypeOpt = arg.typeOpt
      val paramNames = bindParam(param, argTypeOpt)
      param.nameOpt.foreach(boundArguments += _ -> arg)
      boundNames ++= paramNames
      posToParam += posOfArg(arg) -> param
    }
  }

  private def posOfArg(arg: ValueInfo): Int =
    arguments.arguments.indexWhere(cond(_) { case TypedArgument.PositionArg(`arg`) ⇒ true })

  private def handleFlagArgs() {
    for (flagArg ← arguments.argSet)
      bindFlagParam(flagArg, arg = ValueInfo(Some(MashBoolean.True), Some(BooleanClass)))
    for {
      (flagArg, valueOpt) ← arguments.argValues
      value ← valueOpt
    } bindFlagParam(flagArg, arg = value)
  }

  private def bindFlagParam(paramName: String, arg: ValueInfo) =
    for (param ← params.paramByName.get(paramName)) {
      param.nameOpt.foreach(boundArguments += _ -> arg)
      for {
        argType ← arg.typeOpt
        paramName ← param.nameOpt
      } boundNames += paramName -> argType
      val argIndex = arguments.arguments.indexWhere {
        case TypedArgument.LongFlag(`paramName`, _) ⇒ true
        case TypedArgument.ShortFlag(flags)         ⇒ flags contains paramName
        case _                                      ⇒ false
      }
      posToParam += argIndex -> param
    }

}