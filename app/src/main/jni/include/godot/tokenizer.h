/**
 * @file tokenizer.h
 * @brief GDScript 词法分析器：将源代码拆分为 token 流
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include <cstdint>

namespace godot {

/**
 * @brief Token 类型枚举
 */
enum class TokenType : uint8_t {
    // 关键字
    KW_IF, KW_ELSE, KW_ELIF,
    KW_WHILE, KW_FOR, KW_IN,
    KW_BREAK, KW_CONTINUE, KW_PASS,
    KW_RETURN, KW_YIELD,
    KW_CLASS, KW_CLASS_NAME, KW_EXTENDS,
    KW_VAR, KW_CONST, KW_FUNC,
    KW_SIGNED, KW_TRUE, KW_FALSE, KW_NULL,
    KW_ASYNC, KWAwait,
    KW_MATCH, KW_WHEN,
    KW_SELF, KW_SUPER,
    KW_AND, KW_OR, KW_NOT,
    KW_IS,

    // 运算符
    OP_ASSIGN, OP_ADD, OP_SUB, OP_MUL, OP_DIV,
    OP_MOD, OP_POWER, OP_NEGATE,
    OP_EQ, OP_NEQ, OP_GT, OP_LT, OP_GE, OP_LE,
    OP_AND_ASSIGN, OP_OR_ASSIGN,
    OP_PLUS_PLUS, OP_MINUS_MINUS,

    // 括号与分隔符
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    LBRACE, RBRACE,
    COMMA, COLON, SEMICOLON, DOT, ELLIPSIS, ARROW,

    // 标识符与字面量
    IDENTIFIER,
    NUMBER_FLOAT, NUMBER_INT,
    STRING_DOUBLE, STRING_SINGLE, STRING_RAW,
    REGEX,

    // 注释
    COMMENT_SINGLE, COMMENT_MULTI,

    // 换行与空白
    NEWLINE, WS,
};

/**
 * @brief Token 结构
 */
struct Token {
    TokenType type;
    std::string lexeme;
    int line;
    int column;
};

/**
 * @brief GDScript 词法分析器
 */
class Tokenizer {
public:
    /**
     * @brief 将源代码拆分为 token 列表
     */
    static std::vector<Token> tokenize(const std::string& source);

    /**
     * @brief 获取当前行的 token（用于补全提示）
     */
    static std::vector<Token> getCurrentLineTokens(const std::string& source, int lineNum);
};

} // namespace godot
