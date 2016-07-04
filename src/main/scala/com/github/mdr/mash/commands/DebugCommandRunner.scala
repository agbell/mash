package com.github.mdr.mash.commands

import java.io.PrintStream

import com.github.mdr.mash.compiler._
import com.github.mdr.mash.lexer.MashLexer
import com.github.mdr.mash.parser.AbstractSyntax.Expr
import com.github.mdr.mash.parser.MashParserException
import com.github.mdr.mash.parser.PrettyPrinter
import com.github.mdr.mash.parser.TreePrettyPrinter
import com.github.mdr.mash.runtime.MashObject

class DebugCommandRunner(output: PrintStream, globals: MashObject) {

  def runDebugCommand(keyword: String, args: String, bareWords: Boolean) {
    def compile(s: String): Expr =
      Compiler.compile(CompilationUnit(s, mish = false), globals.immutableFields,
        CompilationSettings(forgiving = true, inferTypes = true, bareWords = bareWords))
    (keyword, args) match {
      case ("p" | "pretty", actualCmd) ⇒
        TreePrettyPrinter.printTree(compile(actualCmd))
      case ("e" | "expression", actualCmd) ⇒
        output.println(PrettyPrinter.pretty(compile(actualCmd)))
      case ("t" | "type", actualCmd) ⇒
        output.println(compile(actualCmd))
      case ("tokens", actualCmd) ⇒
        MashLexer.tokenise(actualCmd, forgiving = true, includeCommentsAndWhitespace = true).foreach(output.println)
    }
  }

}