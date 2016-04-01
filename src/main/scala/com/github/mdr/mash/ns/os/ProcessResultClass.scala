package com.github.mdr.mash.ns.os

import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.evaluator.Field
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.ns.core.NumberClass
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.evaluator.MashObject
import com.github.mdr.mash.evaluator.MashList
import com.github.mdr.mash.inference.ConstantMethodTypeInferenceStrategy
import com.github.mdr.mash.ns.core.ObjectClass
import com.github.mdr.mash.subprocesses.ProcessResult
import scala.collection.immutable.ListMap
import com.github.mdr.mash.evaluator.MashNumber

object ProcessResultClass extends MashClass("os.ProcessResult") {

  object Fields {
    val ExitStatus = Field("exitStatus", "Exit status of process", Type.Instance(NumberClass))
    val Stdout = Field("stdout", "Captured stdout", Type.Instance(StringClass))
  }

  import Fields._

  override val fields = Seq(ExitStatus, Stdout)

  def summary = "The result of running a process"

  override val methods = Seq(
    LineMethod,
    LinesMethod,
    ToNumberMethod,
    ToPathMethod,
    ToStringMethod)

  def fromResult(processResult: ProcessResult): MashObject = {
    import ProcessResultClass.Fields._
    MashObject(
      ListMap(
        ExitStatus -> MashNumber(processResult.exitStatus),
        Stdout -> MashString(processResult.stdout)),
      ProcessResultClass)
  }

  case class Wrapper(x: Any) {
    val obj = x.asInstanceOf[MashObject]
    def stdout = obj(Stdout).asInstanceOf[MashString].s
    def line: String = Wrapper(x).stdout.split("\n").headOption.getOrElse("")
  }

  object LinesMethod extends MashMethod("lines") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashList = {
      params.validate(arguments)
      MashList(Wrapper(target).stdout.split("\n").map(MashString(_)))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Seq(Type.Instance(StringClass)))

    override def summary = "The standard output of the process as as sequence of lines"

  }

  object LineMethod extends MashMethod("line") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      MashString(Wrapper(target).line)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(StringClass))

    override def summary = "The first line of the standard output of the process (or empty, if no output)"

  }


  object ToNumberMethod extends MashMethod("toNumber") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      MashNumber(Wrapper(target).line.toDouble)
    }

    override def summary = "Parse the stdout of the process as a number"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Instance(NumberClass))

  }

  object ToPathMethod extends MashMethod("toPath") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      MashString(Wrapper(target).line, PathClass)
    }

    override def summary = "Tag the stdout as a path"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, PathClass))

  }  

  object ToStringMethod extends MashMethod("toString") {

    val params = ParameterModel()

    def apply(target: Any, arguments: Arguments): MashString = {
      params.validate(arguments)
      MashString(Wrapper(target).stdout)
    }

    override def typeInferenceStrategy = ObjectClass.ToStringMethod.typeInferenceStrategy

    override def summary = ObjectClass.ToStringMethod.summary

  }

}