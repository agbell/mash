package com.github.mdr.mash.ns.os.pathClass

import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.functions.{ BoundParams, MashMethod, ParameterModel }
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.ns.os.{ CreateDirectoryFunction, PathClass }
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime.{ MashString, MashValue }

object CreateDirectoryMethod extends MashMethod("createDirectory") {

  private val fileSystem = LinuxFileSystem

  import CreateDirectoryFunction.Params._

  val params = ParameterModel(CreateIntermediates)

  def call(target: MashValue, boundParams: BoundParams): MashString = {
    val createIntermediates = boundParams(CreateIntermediates).isTruthy
    val path = interpretAsPath(target)
    val resultPath = fileSystem.createDirectory(path, createIntermediates)
    asPathString(resultPath)
  }

  override def typeInferenceStrategy = StringClass taggedWith PathClass

  override def summaryOpt = Some("Create directory at this path")

}