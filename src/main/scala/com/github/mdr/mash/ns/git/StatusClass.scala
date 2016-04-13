package com.github.mdr.mash.ns.git

import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.ns.os.PermissionsSectionClass
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.evaluator.Field
import com.github.mdr.mash.ns.time.DateTimeClass
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.ns.core.ObjectClass
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.MashObject
import com.github.mdr.mash.inference.ConstantMethodTypeInferenceStrategy
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.evaluator.MashList

object StatusClass extends MashClass("git.Status") {

  object Fields {
    val Added = Field("added", "Added files", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Changed = Field("changed", "Changed files", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Missing = Field("missing", "Missing files", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Modified = Field("modified", "Tracked files modified", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Removed = Field("removed", "Removed files", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Untracked = Field("untracked", "Untracked files", Type.Seq(Type.Tagged(StringClass, PathClass)))
  }

  import Fields._

  override lazy val fields = Seq(Added, Changed, Missing, Modified, Removed, Untracked)

  def summary = "Git status"

  case class Wrapper(obj: MashObject) {
    private def unmashify(field: Field): Seq[String] =
      obj(field).asInstanceOf[MashList].items.map(_.asInstanceOf[MashString].s)
    def added = unmashify(Added)
    def changed = unmashify(Changed)
    def missing = unmashify(Missing)
    def modified = unmashify(Modified)
    def removed = unmashify(Removed)
    def untracked = unmashify(Untracked)
    def hasChangesToBeCommitted = added.nonEmpty || changed.nonEmpty || removed.nonEmpty
    def hasUnstagedChanges = modified.nonEmpty || missing.nonEmpty
  }

}