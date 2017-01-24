package com.github.mdr.mash.ns.git

import com.github.mdr.mash.evaluator.{ Field, MashClass }
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.{ NumberClass, StringClass }
import com.github.mdr.mash.ns.git.branch.LocalBranchNameClass
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.runtime._

object StatusClass extends MashClass("git.Status") {

  object Fields {
    val Branch = Field("branch", Some("Current branch"), StringClass taggedWith LocalBranchNameClass)
    val UpstreamBranch = Field("upstreamBranch", Some("The name of the upstream branch (if any, else null)"), StringClass)
    val AheadCount = Field("aheadCount", Some("Number of commits that the local branch is ahead of the remote-tracking branch"), NumberClass)
    val BehindCount = Field("behindCount", Some("Number of commits that the local branch is behind the remote-tracking branch"), NumberClass)
    val Added = Field("added", Some("New files that have been staged"), Seq(PathClass))
    val Changed = Field("changed", Some("Changed files that have been staged"), Seq(PathClass))
    val Missing = Field("missing", Some("Files that have been deleted, but not staged"), Seq(PathClass))
    val Modified = Field("modified", Some("Modified files that have not been staged"), Seq(PathClass))
    val Removed = Field("removed", Some("Files that have been deleted and staged"), Seq(PathClass))
    val Untracked = Field("untracked", Some("Untracked files"), Seq(PathClass))
    val Conflicting = Field("conflicting", Some("Conflicting files"), Seq(PathClass))
  }

  import Fields._

  override lazy val fields =
    Seq(Branch, UpstreamBranch, AheadCount, BehindCount, Added, Changed, Missing, Modified, Removed, Untracked, Conflicting)

  override def summaryOpt = Some("The status of a git repository")

  case class Wrapper(obj: MashObject) {
    private def unmashify(field: Field): Seq[String] =
      obj(field).asInstanceOf[MashList].elements.map(_.asInstanceOf[MashString].s)
    def added = unmashify(Added)
    def changed = unmashify(Changed)
    def missing = unmashify(Missing)
    def modified = unmashify(Modified)
    def removed = unmashify(Removed)
    def untracked = unmashify(Untracked)
    def conflicting = unmashify(Conflicting)
    def hasChangesToBeCommitted = added.nonEmpty || changed.nonEmpty || removed.nonEmpty
    def hasUnstagedChanges = modified.nonEmpty || missing.nonEmpty
    def branch = obj(Branch).asInstanceOf[MashString].s
    def upstreamBranchOpt: Option[String] = MashNull.option(obj(UpstreamBranch)).map(_.asInstanceOf[MashString].s)
    def aheadCount = obj(AheadCount).asInstanceOf[MashNumber].asInt.get
    def behindCount = obj(BehindCount).asInstanceOf[MashNumber].asInt.get
  }

}