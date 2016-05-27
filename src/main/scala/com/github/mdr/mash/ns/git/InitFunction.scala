package com.github.mdr.mash.ns.git

import org.eclipse.jgit.api.Git

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy

object InitFunction extends MashFunction("git.init") {

  val params = ParameterModel(Seq())

  def apply(arguments: Arguments) {
    params.validate(arguments)
    Git.init.call()
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Unit)

  override def summary = "Create an empty Git repository"

}