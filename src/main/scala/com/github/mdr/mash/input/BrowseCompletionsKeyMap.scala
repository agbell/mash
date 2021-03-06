package com.github.mdr.mash.input

import com.github.mdr.mash.input.InputSequence._
import com.github.mdr.mash.input.Key._
import com.github.mdr.mash.repl.completions.BrowseCompletionActions._

object BrowseCompletionsKeyMap extends KeyMap(NormalKeyMap.map ++ Map(
  KeyPress(Tab) -> NextCompletion,
  KeyPress(Tab, shift = true) -> PreviousCompletion,
  KeyPress(Right) -> NavigateRight,
  KeyPress(BasicKey('b'), control = true) -> NavigateLeft,
  KeyPress(Left) -> NavigateLeft,
  KeyPress(BasicKey('p'), control = true) -> NavigateUp,
  KeyPress(Up) -> NavigateUp,
  KeyPress(BasicKey('n'), control = true) -> NavigateDown,
  KeyPress(Down) -> NavigateDown,
  KeyPress(Enter) -> AcceptCompletion))
