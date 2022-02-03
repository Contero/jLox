package jLox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;


/**
 * Service to resolve variables to ensure proper scoping
 * Visitor methods walk the AST tree defining scope at blocks, until variables or functions are used
 * names and distance in environment tree are sent to interpreter for lookup
 */
public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>
{
    private final Interpreter interpreter;
    private final Stack<Map<String, Flags>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    private class Flags
    {
        public Boolean assigned = false;
        public Boolean used = false;

        Flags() {}

        Flags(Boolean assigned, Boolean used)
        {
            this.assigned = assigned;
            this.used = used;
        }
    }

    Resolver(Interpreter interpreter)
    {
        this.interpreter = interpreter;
    }

    private enum FunctionType
    {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType
    {
        NONE,
        CLASS
    }

    private ClassType currentClass = ClassType.NONE;

    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt)
    {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        beginScope();
        scopes.peek().put("this", new Flags(true, true));

        for (Stmt.Function method : stmt.methods)
        {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init"))
            {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method, declaration);
        }

        endScope();

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        declare(stmt.name);
        if (stmt.initializer != null)
        {
            resolve(stmt.initializer);
        }

        define(stmt.name);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr)
    {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme).assigned == Boolean.FALSE)
        {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);

        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr)
    {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
        {
            resolve(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt)
    {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        if (currentFunction == FunctionType.NONE)
        {
            Lox.error(stmt.keyword, "Can't return from top-level code, dick.");
        }
        if (stmt.value != null)
        {
            if (currentFunction == FunctionType.INITIALIZER)
            {
                Lox.error(stmt.keyword, "Can't return a value from an initializer");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr)
    {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr)
    {
        resolve(expr.callee);

        for (Expr argument : expr.arguments)
        {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr)
    {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr)
    {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr)
    {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr)
    {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override public Void visitSetExpr(Expr.Set expr)
    {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr)
    {
        if (currentClass == ClassType.NONE)
        {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class, and you're like school in summertime, no class.");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr)
    {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr)
    {
        resolve(expr.condition);
        resolve(expr.ifTrue);
        resolve(expr.ifFalse);
        return null;
    }

    @Override
    public Void visitExitStmt(Stmt.Exit stmt)
    {
        return null;
    }

    void resolve(List<Stmt> statements)
    {
        for (Stmt statement : statements)
        {
            resolve(statement);
        }
    }

    /**
     * Executes visitor method to resolve variables on statement
     * @param stmt The statement to resolve
     */
    void resolve(Stmt stmt)
    {
        stmt.accept(this);
    }

    /**
     * Executes visitor method to resolve variables on expression
     * @param expr The expression to resolve
     */
    void resolve(Expr expr)
    {
        expr.accept(this);
    }

    private void resolveFunction(Stmt.Function function, FunctionType type)
    {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params)
        {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();

        currentFunction = enclosingFunction;
    }

    /**
     * Pushes new scope onto stack
     */
    private void beginScope()
    {
        scopes.push(new HashMap<>());
    }

    /**
     * pops scope out of stack;
     */
    private void endScope()
    {
        Map<String, Flags> scope = scopes.pop();
        scope.forEach((k,v) -> { if (!v.used)
                {
                    Lox.error(new Token(TokenType.IDENTIFIER, k, null, 0), "Variable declared and never used");
                }
        });
    }


    /**
     * Declares a variable in top scope in scope stack
     * @param name The variable name token
     */
    private void declare(Token name)
    {
        if (scopes.isEmpty())
        {
            return;
        }

        Map<String, Flags> scope = scopes.peek();

        if (scope.containsKey(name.lexeme))
        {
            Lox.error(name, "Already a variable with this name in scope, turd.");
        }
        scope.put(name.lexeme, new Flags());
    }

    private void define(Token name)
    {
        if (scopes.isEmpty())
        {
            return;
        }

        Flags flags = new Flags();
        flags.assigned = true;
        scopes.peek().put(name.lexeme, flags);
    }

    /**
     * Walks up the scope stack looking for variable, puts it in interpreter map with stack distance
     * @param expr The expression calling the variable
     * @param name The variable name Token
     */
    private void resolveLocal(Expr expr, Token name)
    {
        for (int i = scopes.size() - 1; i >= 0; i--)
        {
            if (scopes.get(i).containsKey(name.lexeme))
            {
                Flags flags = scopes.get(i).get(name.lexeme);
                flags.used = true;
                scopes.get(i).put(name.lexeme, flags);
                interpreter.resolve(expr, scopes.size() - 1- i);
                return;
            }
        }
    }
}
