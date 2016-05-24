package com.github.mdr.mash.ns.git

import com.github.mdr.mash.ns.git.stash._

object GitNamespace {

  val name = "git"

  val GitFunctions = Seq(
    AddFunction,
    BranchesFunction,
    CloneFunction,
    CommitFunction,
    CreateBranchFunction,
    DeleteBranchFunction,
    FetchFunction,
    LogFunction,
    PullFunction,
    PushFunction,
    StageFunction,
    StatusFunction,
    SwitchFunction,
    StashApplyFunction,
    StashCreateFunction,
    UnstageFunction)
}