/**
 * @file tokenizer.cpp
 * @brief GDScript 词法分析器实现
 * @author Agnes Assistant
 */

#include "godot/tokenizer.h"
#include <sstream>
#include <cctype>
#include <unordered_set>

namespace godot {

// GDScript 关键字集合
static const std::unordered_set<std::string> KEYWORDS = {
    "if", "elif", "else", "while", "for", "in", "break", "continue", "pass",
    "return", "yield", "class", "class_name", "extends", "var", "const", "func",
    "const", "true", "false", "null", "async", "await", "match", "when",
    "self", "super", "and", "or", "not", "is"
};

// 内置类型
static const std::unordered_set<std::string> BUILTIN_TYPES = {
    "int", "float", "bool", "string", "void", "null",
    "Vector2", "Vector3", "Vector4", "Color", "Rect2", "Rect2i", "Recti",
    "AABB", "Plane", "Quaternion", "Mat2", "Mat2x3", "Mat3", "Mat3x4", "Mat4",
    "Transform2D", "Transform3D", "Projection", "Basis", "CapsuleShape2D",
    "CapsuleShape3D", "CircleShape2D", "CollisionShape2D", "CollisionShape3D",
    "Polygon2D", "SphereShape3D", "SubViewport", "SubViewportContainer",
    "RayCast2D", "RayCast3D", "NavigationMesh", "NavigationRegion2D",
    "NavigationRegion3D", "Path2D", "Path3D", "PolyShape2D", "World2D",
    "World3D", "Node", "Node2D", "Node3D", "Control", "CanvasItem",
    "CharacterBody2D", "CharacterBody3D", "RigidBody2D", "RigidBody3D",
    "Area2D", "Area3D", "StaticBody2D", "StaticBody3D", "AnimatedSprite2D",
    "AnimatedSprite3D", "Sprite2D", "Sprite3D", "ParticleProcessMaterial",
    "ParticlegEmitter", "Script", "Resource", "Image", "ImageTexture",
    "Texture2D", "Texture3D", "TextureCube", "AudioStream", "AudioServer",
    "Input", "InputEvent", "DisplayServer", "OS", "ResourceLoader",
    "ResourceSaver", " FileAccess", "HTTPRequest", "HTTPClient", "JSON",
    "JSONRPC", "XMLParser", "Socket", "TCPServer", "TCPClient",
    "GDScript", "Callable", "Signal", "Dictionary", "Array", "StringName",
    "NodePath", "RID", "Object", "Variant", " godot::ClassDB"
};

std::vector<Token> Tokenizer::tokenize(const std::string& source) {
    std::vector<Token> tokens;
    int line = 1;
    int column = 1;
    int i = 0;
    int len = static_cast<int>(source.size());

    while (i < len) {
        char c = source[i];

        // 跳过空白（保留换行符用于结构化）
        if (c == ' ' || c == '\t') {
            column++;
            i++;
            continue;
        }

        // 换行
        if (c == '\n') {
            tokens.push_back({TokenType::NEWLINE, "\n", line, column});
            line++;
            column = 1;
            i++;
            continue;
        }

        // 单行注释
        if (c == '#') {
            int start = i;
            while (i < len && source[i] != '\n') i++;
            tokens.push_back({TokenType::COMMENT_SINGLE,
                source.substr(start, i - start), line, column});
            column = i - start + 1;
            continue;
        }

        // 多行注释
        if (c == '/' && i + 1 < len && source[i + 1] == '*') {
            int start = i;
            i += 2;
            while (i + 1 < len && !(source[i] == '*' && source[i + 1] == '/')) i++;
            if (i + 1 < len) i += 2;
            tokens.push_back({TokenType::COMMENT_MULTI,
                source.substr(start, i - start), line, column});
            column += i - start;
            continue;
        }

        // 字符串（双引号）
        if (c == '"') {
            int start = i;
            i++;
            bool escaped = false;
            while (i < len && (source[i] != '"' || escaped)) {
                if (source[i] == '\\' && !escaped) {
                    escaped = true;
                } else {
                    escaped = false;
                }
                i++;
            }
            if (i < len) i++;  // 跳过结束引号
            tokens.push_back({TokenType::STRING_DOUBLE,
                source.substr(start, i - start), line, column});
            column += i - start;
            continue;
        }

        // 原始字符串
        if (c == '@' && i + 1 < len && source[i + 1] == '"') {
            int start = i;
            i += 2;
            int depth = 1;
            while (i < len && depth > 0) {
                if (source[i] == '"') {
                    if (i + 1 < len && source[i + 1] == '"') {
                        i += 2;  // 转义的引号
                    } else {
                        depth--;
                        i++;
                    }
                } else {
                    i++;
                }
            }
            tokens.push_back({TokenType::STRING_RAW,
                source.substr(start, i - start), line, column});
            column += i - start;
            continue;
        }

        // 正则表达式
        if (c == '/' && i + 1 < len && source[i + 1] != '*') {
            int start = i;
            i++;
            bool escaped = false;
            while (i < len && (source[i] != '/' || escaped)) {
                if (source[i] == '\\' && !escaped) {
                    escaped = true;
                } else {
                    escaped = false;
                }
                i++;
            }
            if (i < len) i++;
            tokens.push_back({TokenType::REGEX,
                source.substr(start, i - start), line, column});
            column += i - start;
            continue;
        }

        // 数字
        if (std::isdigit(c) || (c == '.' && i + 1 < len && std::isdigit(source[i + 1]))) {
            int start = i;
            bool hasDot = false;
            if (c == '.') hasDot = true;
            i++;
            while (i < len && (std::isdigit(source[i]) || source[i] == '.')) {
                if (source[i] == '.') hasDot = true;
                i++;
            }
            TokenType type = hasDot ? TokenType::NUMBER_FLOAT : TokenType::NUMBER_INT;
            tokens.push_back({type, source.substr(start, i - start), line, column});
            column += i - start;
            continue;
        }

        // 标识符和关键字
        if (std::isalpha(c) || c == '_') {
            int start = i;
            while (i < len && (std::isalnum(source[i]) || source[i] == '_')) i++;
            std::string lexeme = source.substr(start, i - start);
            TokenType type = TokenType::IDENTIFIER;
            if (KEYWORDS.count(lexeme)) {
                type = TokenType::KW_IF;  // 默认关键字类型
                if (lexeme == "elif") type = TokenType::KW_ELSE;
                else if (lexeme == "else") type = TokenType::KW_ELSE;
                else if (lexeme == "while") type = TokenType::KW_WHILE;
                else if (lexeme == "for") type = TokenType::KW_FOR;
                else if (lexeme == "in") type = TokenType::KW_IN;
                else if (lexeme == "break") type = TokenType::KW_BREAK;
                else if (lexeme == "continue") type = TokenType::KW_CONTINUE;
                else if (lexeme == "pass") type = TokenType::KW_PASS;
                else if (lexeme == "return") type = TokenType::KW_RETURN;
                else if (lexeme == "yield") type = TokenType::KW_YIELD;
                else if (lexeme == "class") type = TokenType::KW_CLASS;
                else if (lexeme == "class_name") type = TokenType::KW_CLASS_NAME;
                else if (lexeme == "extends") type = TokenType::KW_EXTENDS;
                else if (lexeme == "var") type = TokenType::KW_VAR;
                else if (lexeme == "const") type = TokenType::KW_CONST;
                else if (lexeme == "func") type = TokenType::KW_FUNC;
                else if (lexeme == "true") type = TokenType::KW_TRUE;
                else if (lexeme == "false") type = TokenType::KW_FALSE;
                else if (lexeme == "null") type = TokenType::KW_NULL;
                else if (lexeme == "async") type = TokenType::KW_ASYNC;
                else if (lexeme == "await") type = TokenType::KWAwait;
                else if (lexeme == "match") type = TokenType::KW_MATCH;
                else if (lexeme == "when") type = TokenType::KW_WHEN;
                else if (lexeme == "self") type = TokenType::KW_SELF;
                else if (lexeme == "super") type = TokenType::KW_SUPER;
                else if (lexeme == "and") type = TokenType::KW_AND;
                else if (lexeme == "or") type = TokenType::KW_OR;
                else if (lexeme == "not") type = TokenType::KW_NOT;
                else if (lexeme == "is") type = TokenType::KW_IS;
            }
            tokens.push_back({type, lexeme, line, column});
            column += i - start;
            continue;
        }

        // 运算符和标点
        std::string twoChar = (i + 1 < len) ? source.substr(i, 2) : "";
        switch (c) {
            case '=':
                if (twoChar == "==") {
                    tokens.push_back({TokenType::OP_EQ, "==", line, column});
                    i += 2; column += 2;
                } else if (twoChar == "=") {
                    tokens.push_back({TokenType::OP_ASSIGN, "=", line, column});
                    i++; column++;
                }
                break;
            case '!':
                if (twoChar == "!=") {
                    tokens.push_back({TokenType::OP_NEQ, "!=", line, column});
                    i += 2; column += 2;
                }
                break;
            case '<':
                if (twoChar == "<=") {
                    tokens.push_back({TokenType::OP_LE, "<=", line, column});
                    i += 2; column += 2;
                } else {
                    tokens.push_back({TokenType::OP_LT, "<", line, column});
                    i++; column++;
                }
                break;
            case '>':
                if (twoChar == ">=") {
                    tokens.push_back({TokenType::OP_GE, ">=", line, column});
                    i += 2; column += 2;
                } else {
                    tokens.push_back({TokenType::OP_GT, ">", line, column});
                    i++; column++;
                }
                break;
            case '+':
                if (twoChar == "++") {
                    tokens.push_back({TokenType::OP_PLUS_PLUS, "++", line, column});
                    i += 2; column += 2;
                } else {
                    tokens.push_back({TokenType::OP_ADD, "+", line, column});
                    i++; column++;
                }
                break;
            case '-':
                if (twoChar == "--") {
                    tokens.push_back({TokenType::OP_MINUS_MINUS, "--", line, column});
                    i += 2; column += 2;
                } else if (twoChar == "->") {
                    tokens.push_back({TokenType::ARROW, "->", line, column});
                    i += 2; column += 2;
                } else {
                    tokens.push_back({TokenType::OP_SUB, "-", line, column});
                    i++; column++;
                }
                break;
            case '*':
                tokens.push_back({TokenType::OP_MUL, "*", line, column});
                i++; column++;
                break;
            case '/':
                tokens.push_back({TokenType::OP_DIV, "/", line, column});
                i++; column++;
                break;
            case '%':
                tokens.push_back({TokenType::OP_MOD, "%", line, column});
                i++; column++;
                break;
            case '^':
                tokens.push_back({TokenType::OP_POWER, "^", line, column});
                i++; column++;
                break;
            case '~':
                tokens.push_back({TokenType::OP_NEGATE, "~", line, column});
                i++; column++;
                break;
            case '(':
                tokens.push_back({TokenType::LPAREN, "(", line, column});
                i++; column++;
                break;
            case ')':
                tokens.push_back({TokenType::RPAREN, ")", line, column});
                i++; column++;
                break;
            case '[':
                tokens.push_back({TokenType::LBRACKET, "[", line, column});
                i++; column++;
                break;
            case ']':
                tokens.push_back({TokenType::RBRACKET, "]", line, column});
                i++; column++;
                break;
            case '{':
                tokens.push_back({TokenType::LBRACE, "{", line, column});
                i++; column++;
                break;
            case '}':
                tokens.push_back({TokenType::RBRACE, "}", line, column});
                i++; column++;
                break;
            case ',':
                tokens.push_back({TokenType::COMMA, ",", line, column});
                i++; column++;
                break;
            case ':':
                tokens.push_back({TokenType::COLON, ":", line, column});
                i++; column++;
                break;
            case ';':
                tokens.push_back({TokenType::SEMICOLON, ";", line, column});
                i++; column++;
                break;
            case '.':
                if (i + 2 < len && source[i+1] == '.' && source[i+2] == '.') {
                    tokens.push_back({TokenType::ELLIPSIS, "...", line, column});
                    i += 3; column += 3;
                } else {
                    tokens.push_back({TokenType::DOT, ".", line, column});
                    i++; column++;
                }
                break;
            default:
                // 未知字符，跳过
                i++;
                column++;
                break;
        }
    }

    return tokens;
}

std::vector<Token> Tokenizer::getCurrentLineTokens(
    const std::string& source, int lineNum) {
    std::istringstream stream(source);
    std::string line;
    int currentLine = 1;

    while (std::getline(stream, line)) {
        if (currentLine == lineNum) {
            return tokenize(line);
        }
        currentLine++;
    }
    return {};
}

} // namespace godot
