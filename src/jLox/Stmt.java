package jLox;

import java.util.List;

abstract class Stmt
{
	interface Visitor<R>
	{
		R visitBlockStmt(Block stmt);
		R visitExpressionStmt(Expression stmt);
		R visitPrintStmt(Print stmt);
		R visitVarStmt(Var stmt);
		R visitExitStmt(Exit stmt);
	}

	static class Block extends Stmt
	{
		final List<Stmt> statements;

		@Override
		<R> R accept(Visitor<R> visitor)
		{
			return visitor.visitBlockStmt(this);
		}

		Block(List<Stmt> statements)
		{
			this.statements=statements;
		}
	}

	static class Expression extends Stmt
	{
		final Expr expression;

		@Override
		<R> R accept(Visitor<R> visitor)
		{
			return visitor.visitExpressionStmt(this);
		}

		Expression(Expr expression)
		{
			this.expression=expression;
		}
	}

	static class Print extends Stmt
	{
		final Expr expression;

		@Override
		<R> R accept(Visitor<R> visitor)
		{
			return visitor.visitPrintStmt(this);
		}

		Print(Expr expression)
		{
			this.expression=expression;
		}
	}

	static class Var extends Stmt
	{
		final Token name;
		final Expr initializer;

		@Override
		<R> R accept(Visitor<R> visitor)
		{
			return visitor.visitVarStmt(this);
		}

		Var(Token name, Expr initializer)
		{
			this.name=name;
			this.initializer=initializer;
		}
	}

	static class Exit extends Stmt
	{
		final Object value;

		@Override
		<R> R accept(Visitor<R> visitor)
		{
			return visitor.visitExitStmt(this);
		}

		Exit(Object value)
		{
			this.value=value;
		}
	}

	abstract <R> R accept(Visitor<R> visitor);
}