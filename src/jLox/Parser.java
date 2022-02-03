package jLox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static jLox.TokenType.*;

public class Parser
{
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    /**
     * Initializes instance of parser and list of tokens to parse
     * @param tokens List of tokens to parse
     */
    Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    /**
     * Parses tokens into list of statements, beginning with call to declaration
     * @return List of statements parsed into AST
     */
    List<Stmt> parse()
    {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd())
        {
            statements.add(declaration());
        }

        return statements;
    }

    /**
     * Begins the chain of expressions parsing attempts
     * by calling the assignment expression
     * @return the next expression object that can be parsed
     */
    private Expr expression()
    {
        return assignment();
    }

    /**
     * Attempts to create declaration statement from next tokens.
     * If not a declaration, calls statement to attempt to parse next statement type
     * @return statement parsed from tokens
     */
    private Stmt declaration()
    {
        try
        {
            if (match(CLASS))
            {
                return classDeclaration();
            }
            if (match (FUN))
            {
                return function(CallableType.FUNCTION);
            }
            if (match(VAR))
            {
                return varDeclaration();
            }

            return statement();
        }
        catch (ParseError error)
        {
            synchronize();
            return null;
        }
    }

    /**
     * generates a class tree
     * @return class statement
     */
    private Stmt classDeclaration()
    {
        Token name = consume(IDENTIFIER, "Expect class name");
        consume(LEFT_BRACE, "Expect { before class body");

        List<Stmt.Function> methods = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())
        {
            methods.add(function(CallableType.METHOD));
        }

        consume(RIGHT_BRACE, "Expect } after class body");

        return new Stmt.Class(name, methods);
    }

    /**
     * Attempts to create a statement by looking at tokens and matching to keywords
     * If no match, kicks tokens to Expression logic
     * @return the statement tree;
     */
    private Stmt statement()
    {
        if (match(FOR))
            return forStatement();
        if (match(IF))
            return ifStatement();
        if (match(PRINT))
            return printStatement();
        if (match(RETURN))
            return returnStatement();
        if (match(WHILE))
            return whileStatement();
        if (match(EXIT))
            return exitStatement();
        if (match(LEFT_BRACE))
            return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt forStatement()
    {
        consume(LEFT_PAREN, "Expect '(' after for.");

        Stmt initializer;
        if (match(SEMICOLON))
        {
            initializer = null;
        }
        else if(match(VAR))
        {
            initializer = varDeclaration();
        }
        else
        {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON))
        {
            condition = expression();
        }
        consume (SEMICOLON, "Expect ; after loop condition");

        Expr increment = null;
        if (!check(SEMICOLON))
        {
            increment = expression();
        }
        consume(RIGHT_PAREN, "expect ')' after for clauses");

        Stmt body = statement();

        if (increment != null)
        {
            body = new Stmt.Block(
                    Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null)
        {
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);

        if (initializer != null)
        {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement()
    {
            consume(LEFT_PAREN, "Expect '(' after 'if'.");
            Expr condition  = expression();
            consume(RIGHT_PAREN, "Expect ')' after condition.");

            Stmt thenBranch = statement();
            Stmt elseBranch = null;

            if (match(ELSE))
            {
                elseBranch = statement();
            }

            return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement()
    {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement()
    {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON))
        {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt exitStatement()
    {
        if (match(SEMICOLON))
            return new Stmt.Exit(new Expr.Literal(0.0));
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Exit(value);
    }

    private Stmt varDeclaration()
    {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL))
        {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ; after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement()
    {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt expressionStatement()
    {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression");
        return new Stmt.Expression(expr);
    }

    /**
     * Creates function object from next tokens, does not include fun keyword
     * starts are parameter list, creates block object to hold body statements
     * @param kind The type of callable
     * @return the function statement
     */
    private Stmt.Function function(CallableType kind)
    {
        Token name = consume(IDENTIFIER, "Expect " + kind.toString().toLowerCase() + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind.toString().toLowerCase() + " name.");
        List<Token> parameters = new ArrayList<>();

        if (!check(RIGHT_PAREN))
        {
            do
            {
                if (parameters.size() >= 255)
                {
                    error(peek(), "can't have more than 255 parameters.");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            }
            while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before " + kind.toString().toLowerCase() + " body.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private List<Stmt> block()
    {
        List<Stmt> statements = new ArrayList<>();

        while(!check(RIGHT_BRACE) && !isAtEnd())
        {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block;");
        return statements;
    }

    /**
     * Attempts to parse an assignment (variable or field) out of next tokens
     * gets lower order expression and looks for = Token indicating an assignment
     * If the lower expression is a Variable name, creates Assign expression
     * If it is a property getter, creates Set (object is instance, name and value)
     * @return the parsed expression node
     */
    private Expr assignment()
    {
        Expr expr = or();

        if (match(EQUAL))
        {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable)
            {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            else if (expr instanceof Expr.Get)
            {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or()
    {
        Expr expr = and();

        while (match(OR))
        {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and()
    {
        Expr expr = ternary();

        while (match(AND))
        {
            Token operator = previous();
            Expr right = ternary();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr ternary()
    {
        Expr expr = equality();

        if (match(QUESTION))
        {
            Expr conditional = expr;
            Expr ifTrue = expression();
            consume(COLON, "Expecting : ");
            Expr ifFalse = expression();
            expr = new Expr.Ternary(conditional, ifTrue, ifFalse);
        }

        return expr;
    }

    private Expr equality()
    {
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL))
        {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison()
    {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL))
        {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term()
    {
        Expr expr = factor();

        while (match(MINUS,PLUS))
        {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor()
    {
        Expr expr = unary();

        while (match(SLASH,STAR))
        {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary()
    {
        if (match(BANG, MINUS))
        {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    /**
     * Finishes a call,
     * @param callee
     * @return
     */
    private Expr finishCall(Expr callee)
    {
        List<Expr> arguments = new ArrayList<>();

        // checks for empty parameter list
        if(!check(RIGHT_PAREN))
        {
            do
            {
                if (arguments.size() >= 255)
                {
                    error (peek(), "Can't have more than 255 arguments.");
                }
               arguments.add(expression());
            }
            while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    /**
     * attempts to parse call (function or method)
     * primary will find a literal value, identifier, or grouping
     * If the next token is ( or ., if finishes the call parse
     * for a function (finishCall()) or property (create GET expression)
     * Otherwise, not a call and kicks it back up the chain
     * @return the parsed node
     */
    private Expr call()
    {
        Expr expr = primary();

        while (true)
        {
            if (match(LEFT_PAREN))
            {
                expr = finishCall(expr);
            }
            else if (match(DOT))
            {
                Token name = consume(IDENTIFIER, "Expect property name after '.'");
                expr = new Expr.Get(expr, name);
            }
            else
            {
                break;
            }
        }

        return expr;
    }

    /**
     * Parses a primary token by matching reserved values, literals, identifiers, or (
     * a ( will trigger another parse beginning back expression() after the (
     * @return the parsed expression
     */
    private Expr primary()
    {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);
        if (match(THIS)) return new Expr.This(previous());
        if (match(IDENTIFIER))
            return new Expr.Variable(previous());

        if (match(LEFT_PAREN))
        {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    /**
     * Checks to see if the next token is of one of the provided types
     * If it is a match, the token is consumed
     * @param types The types to check
     * @return An indicator of whether the token is of the provided types
     */
    private boolean match(TokenType... types)
    {
        for (TokenType type : types)
        {
            if (check(type))
            {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * Consumes the next token, checks to make sure it is the correct type
     * throws error if not
     * @param type The expected type of the next token
     * @param message The error message if the token type is not the expected type
     * @return the consumed token
     */
    private Token consume(TokenType type, String message)
    {
        if (check(type))
        {
            return advance();
        }

        throw error(peek(), message);
    }

    /**
     * Checks the type of the next token, but does not advance or consume it
     * @param type The expected type of the next token
     * @return Indicator of whether the next token matches the expected type
     */
    private boolean check(TokenType type)
    {
        if (isAtEnd())
        {
            return false;
        }

        return peek().type == type;
    }

    /**
     * Moves to the next token pointer
     * @return The token advanced over
     */
    private Token advance()
    {
        if (!isAtEnd())
        {
            current++;
        }
        return previous();
    }

    /**
     * Checks to see if the next token is the end of the file
     * @return Indicator of if the pointer is at the end of the file
     */
    private boolean isAtEnd()
    {
        return peek().type == EOF;
    }

    /**
     * Provides the next Token, does not advance the pointer
     * @return the Next Token
     */
    private Token peek()
    {
        return tokens.get(current);
    }

    /**
     * Returns the token behind the next token pointer
     * @return The last token
     */
    private Token previous()
    {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message)
    {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize()
    {
        advance();

        while (!isAtEnd())
        {
            if (previous().type == SEMICOLON)
            {
                return;
            }

            switch (peek().type)
            {
                case CLASS:
                case FOR:
                case FUN:
                case IF:
                case PRINT:
                case RETURN:
                case WHILE:
                case EXIT:
                    return;
            }

            advance();
        }
    }
}

