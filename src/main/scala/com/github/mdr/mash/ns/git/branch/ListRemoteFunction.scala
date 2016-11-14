package com.github.mdr.mash.ns.git.branch

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.{ MashFunction, ParameterModel }
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.ns.git.{ CommitHashClass, GitHelper }
import com.github.mdr.mash.runtime.{ MashList, MashObject, MashString }
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand.ListMode
import org.eclipse.jgit.lib.{ Ref, Repository }

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

object ListRemoteFunction extends MashFunction("git.branch.remoteBranches") {

  val params = ParameterModel()

  def apply(arguments: Arguments): MashList = {
    params.validate(arguments)
    GitHelper.withRepository { repo ⇒
      val git = new Git(repo)
      val branches = git.branchList.setListMode(ListMode.REMOTE).call().asScala.filterNot(_.getName endsWith "/HEAD")
      MashList(branches.map(asMashObject(repo)))
    }
  }

  def asMashObject(repo: Repository)(ref: Ref): MashObject = {
    val id = ref.getObjectId.getName
    val name = ref.getName.replaceAll("^refs/remotes/", "")
    val Seq(remote, branchName) = name.split("/", 2).toSeq
    import RemoteBranchClass.Fields._
    MashObject.of(
      ListMap(
        Remote -> MashString(remote, RemoteNameClass),
        Name -> MashString(branchName, RemoteBranchNameClass),
        Commit -> MashString(id, CommitHashClass)),
      RemoteBranchClass)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Seq(RemoteBranchClass))

  override def summary = "List remote branches in the repository"

}