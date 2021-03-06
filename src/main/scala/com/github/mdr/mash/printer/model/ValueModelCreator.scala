package com.github.mdr.mash.printer.model

import com.github.mdr.mash.printer.{ FieldRenderer, ViewConfig }
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.terminal.TerminalInfo

class ValueModelCreator(terminalInfo: TerminalInfo, viewConfig: ViewConfig) {

  private val fieldRenderer = new FieldRenderer(viewConfig)

  def create(value: MashValue): ValueModel =
    ValueModel(fieldRenderer.renderField(value), value)
}
