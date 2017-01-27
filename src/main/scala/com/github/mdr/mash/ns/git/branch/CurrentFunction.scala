package com.github.mdr.mash.ns.git.branch

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ MashFunction, ParameterModel }
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.inference.Type.classToType
import com.github.mdr.mash.ns.git.GitHelper
import com.github.mdr.mash.runtime.MashObject
import org.eclipse.jgit.api.Git

import scala.collection.JavaConverters._

object CurrentFunction extends MashFunction("git.branch.current") {

  val params = ParameterModel(Seq())

  def apply(arguments: Arguments): MashObject = {
    params.validate(arguments)
    GitHelper.withRepository { repo ⇒
      val git = new Git(repo)
      val currentBranch = repo.getFullBranch
      val branch = git.branchList.call().asScala.find(_.getName == currentBranch).get
      ListFunction.asMashObject(repo)(branch)
    }
  }

  override def typeInferenceStrategy = BranchClass

  override def summaryOpt = Some("The current branch")

}