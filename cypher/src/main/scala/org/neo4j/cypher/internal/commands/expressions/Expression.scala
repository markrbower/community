/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.commands.expressions

import org.neo4j.cypher._
import internal.symbols._
import collection.Map

abstract class Expression extends (Map[String, Any] => Any)
with Typed
with TypeSafe {
  def rewrite(f: Expression => Expression): Expression
  def exists(f: Expression => Boolean) = filter(f).nonEmpty
  def filter(f: Expression => Boolean): Seq[Expression]
  def subExpressions = filter( _ != this)
  def containsAggregate = exists(_.isInstanceOf[AggregationExpression])

  /*When calculating the type of an expression, the expression should also
  make sure to check the types of any downstream expressions*/
  protected def calculateType(symbols: SymbolTable): CypherType

  def evaluateType(expectedType: CypherType, symbols: SymbolTable): CypherType = {
    val t = calculateType(symbols)

    if (!expectedType.isAssignableFrom(t) &&
        !t.isAssignableFrom(expectedType)) {
      throw new CypherTypeException("%s expected to be of type %s but it is of type %s".format(this, expectedType, t))
    }

    t
  }

  def assertTypes(symbols: SymbolTable) {
    evaluateType(AnyType(), symbols)
  }

  override def toString() = getClass.getSimpleName
}

case class CachedExpression(key:String, typ:CypherType) extends Expression {
  def apply(m: Map[String, Any]) = m(key)

  def rewrite(f: (Expression) => Expression) = f(this)
  def filter(f: (Expression) => Boolean) = if(f(this)) Seq(this) else Seq()

  def calculateType(symbols: SymbolTable) = typ

  def symbolTableDependencies = Set(key)

  override def toString() = "Cached(%s of type %s)".format(key, typ)
}

abstract class Arithmetics(left: Expression, right: Expression)
  extends Expression {
  def throwTypeError(bVal: Any, aVal: Any): Nothing = {
    throw new CypherTypeException("Don't know how to " + this + " `" + bVal + "` with `" + aVal + "`")
  }

  def apply(m: Map[String, Any]) = {
    val aVal = left(m)
    val bVal = right(m)

    (aVal, bVal) match {
      case (x: Number, y: Number) => calc(x, y)
      case _ => throwTypeError(bVal, aVal)
    }
  }

  def calc(a: Number, b: Number): Number

  def filter(f: (Expression) => Boolean) = if(f(this))
    Seq(this) ++ left.filter(f) ++ right.filter(f)
  else
    left.filter(f) ++ right.filter(f)

  def calculateType(symbols: SymbolTable): CypherType = {
    left.evaluateType(NumberType(), symbols)
    right.evaluateType(NumberType(), symbols)
    NumberType()
  }
}

trait ExpressionWInnerExpression extends Expression {
  def inner:Expression
  def myType:CypherType
  def expectedInnerType:CypherType

  def calculateType(symbols: SymbolTable): CypherType = {
    inner.evaluateType(expectedInnerType, symbols)

    myType
  }
}