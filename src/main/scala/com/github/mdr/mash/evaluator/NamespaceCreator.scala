package com.github.mdr.mash.evaluator

import com.github.mdr.mash.functions.MashFunction
import scala.collection.immutable.ListMap
import com.github.mdr.mash.ns.StandardFunctions
import com.github.mdr.mash.functions.HasName

object NamespaceCreator {

  def createNamespace: MashObject = {
    val allObjects = StandardFunctions.Functions ++ StandardFunctions.GitFunctions ++ StandardFunctions.AllClasses
    createNamespace(allObjects.map(makeThing))
  }

  private def makeThing(hasName: HasName): Thing = Thing(hasName.segments, hasName)

  private case class Thing(segments: Seq[String], value: Any) {
    def first: String = segments.head
    def rest: Thing = Thing(segments.tail, value)
    def isLeaf = segments.size == 1
  }

  private def createNamespace(things: Seq[Thing]): MashObject = {
    val thingsByName: Map[String, Seq[Thing]] = things.groupBy(_.first)
    val fields: Seq[(String, Any)] =
      thingsByName.map {
        case (name, things) ⇒
          val value = things match {
            case Seq(thing) if thing.isLeaf ⇒
              thing.value
            case _ ⇒
              require(!things.exists(_.isLeaf))
              createNamespace(things.map(_.rest))
          }
          name -> value
      }.toSeq.sortBy(_._1)
    MashObject(ListMap(fields: _*), classOpt = None)
  }

}