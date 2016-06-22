package com.github.mdr.mash.ns.os.pathClass

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.ConstantMethodTypeInferenceStrategy
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime.MashString
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.ns.os.CreateDirectoryFunction

object PathClassCreateDirectoryMethod extends MashMethod("createDirectory") {

  private val fileSystem = LinuxFileSystem

  import CreateDirectoryFunction.Params._

  val params = ParameterModel(Seq(CreateIntermediates))

  def apply(target: MashValue, arguments: Arguments): MashString = {
    val boundParams = params.validate(arguments)
    val createIntermediates = boundParams(CreateIntermediates).isTruthy
    val path = interpretAsPath(target)
    val resultPath = fileSystem.createDirectory(path, createIntermediates)
    asPathString(resultPath)
  }

  override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(StringClass taggedWith PathClass)

  override def summary = "Create directory at this path"

}