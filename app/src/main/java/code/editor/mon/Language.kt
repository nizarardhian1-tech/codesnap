package code.editor.mon

enum class Language(
    val displayName: String,
    val extensions: Set<String>,
    val keywords: Set<String>,
    val snippets: List<String>
) {
    KOTLIN(
        displayName = "Kotlin",
        extensions = setOf("kt", "kts"),
        keywords = setOf(
            "fun", "val", "var", "class", "object", "interface", "enum", "data",
            "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
            "return", "break", "continue", "throw", "in", "is", "as", "null",
            "true", "false", "override", "public", "private", "protected", "internal",
            "import", "package", "companion", "sealed", "abstract", "final", "open",
            "const", "lateinit", "suspend", "unit", "int", "string", "boolean",
            "long", "double", "float", "char", "byte", "short", "array", "list",
            "map", "set", "collection", "sequence", "flow"
        ),
        snippets = listOf(
            "fun main() {\n    \n}",
            "class ClassName {\n    \n}",
            "data class DataClass(val id: Int, val name: String)",
            "object Singleton {\n    \n}",
            "interface InterfaceName {\n    \n}",
            "fun functionName(param: String): Int {\n    return 0\n}",
            "val variableName: String = \"value\"",
            "var mutableVariable: Int = 0",
            "if (condition) {\n    \n} else {\n    \n}",
            "when (value) {\n    option1 -> {}\n    else -> {}\n}",
            "for (item in collection) {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (e: Exception) {\n    \n}",
            "lifecycleScope.launch {\n    \n}",
            "val liveData = MutableLiveData<String>()"
        )
    ),

    JAVA(
        displayName = "Java",
        extensions = setOf("java"),
        keywords = setOf(
            "public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "enum", "extends", "implements", "import",
            "package", "void", "int", "long", "double", "float", "boolean",
            "char", "byte", "short", "string", "object", "null", "true", "false",
            "if", "else", "switch", "case", "default", "for", "while", "do",
            "break", "continue", "return", "try", "catch", "finally", "throw",
            "throws", "new", "this", "super", "instanceof"
        ),
        snippets = listOf(
            "public class ClassName {\n    public static void main(String[] args) {\n        \n    }\n}",
            "public class ClassName {\n    \n}",
            "public interface InterfaceName {\n    \n}",
            "public void methodName() {\n    \n}",
            "public int methodName(int param) {\n    return 0;\n}",
            "private String fieldName;",
            "if (condition) {\n    \n} else {\n    \n}",
            "for (int i = 0; i < length; i++) {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (Exception e) {\n    \n}",
            "List<String> list = new ArrayList<>();",
            "Map<String, Integer> map = new HashMap<>();"
        )
    ),

    CPP(
        displayName = "C++",
        extensions = setOf("cpp", "cc", "cxx", "hpp", "h"),
        keywords = setOf(
            "include", "define", "ifdef", "ifndef", "endif", "pragma",
            "int", "long", "short", "float", "double", "char", "bool", "void",
            "signed", "unsigned", "const", "static", "extern", "inline",
            "virtual", "class", "struct", "union", "enum", "namespace", "using",
            "template", "typename", "public", "private", "protected",
            "if", "else", "switch", "case", "default", "for", "while", "do",
            "break", "continue", "return", "try", "catch", "throw", "new", "delete"
        ),
        snippets = listOf(
            "#include <iostream>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}",
            "#include <vector>\n#include <string>\n#include <map>",
            "class ClassName {\npublic:\n    ClassName();\nprivate:\n    \n};",
            "void functionName(int param) {\n    \n}",
            "int functionName(int param) {\n    return 0;\n}",
            "vector<int> vec = {1, 2, 3};",
            "for (int i = 0; i < n; i++) {\n    \n}",
            "if (condition) {\n    \n} else {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (const exception& e) {\n    \n}"
        )
    ),

    LUA(
        displayName = "Lua",
        extensions = setOf("lua"),
        keywords = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "if", "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while", "print", "type",
            "pairs", "ipairs", "tonumber", "tostring", "string", "table"
        ),
        snippets = listOf(
            "function functionName(param)\n    \nend",
            "local variableName = value",
            "local function localFunction()\n    \nend",
            "if condition then\n    \nelse\n    \nend",
            "for i = 1, n do\n    \nend",
            "for i, v in ipairs(table) do\n    \nend",
            "while condition do\n    \nend",
            "local tab = {\n    key = value,\n}",
            "print(\"Hello World\")"
        )
    ),

    HTML(
        displayName = "HTML",
        extensions = setOf("html", "htm", "xhtml"),
        keywords = setOf(
            "html", "head", "body", "div", "span", "p", "a", "img", "ul", "ol",
            "li", "table", "tr", "td", "th", "form", "input", "button", "select",
            "option", "textarea", "label", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "footer", "nav", "section", "article", "script", "style",
            "link", "meta", "title", "iframe", "video", "audio", "canvas", "svg"
        ),
        snippets = listOf(
            "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"UTF-8\">\n    <title>Document</title>\n</head>\n<body>\n    \n</body>\n</html>",
            "<div class=\"\">\n    \n</div>",
            "<span class=\"\">\n    \n</span>",
            "<a href=\"\">\n    \n</a>",
            "<img src=\"\" alt=\"\" />",
            "<ul>\n    <li></li>\n</ul>",
            "<form action=\"\" method=\"post\">\n    <input type=\"text\" name=\"\" />\n    <button type=\"submit\">Submit</button>\n</form>",
            "<button type=\"button\">\n    \n</button>",
            "<script>\n    \n</script>",
            "<style>\n    \n</style>"
        )
    ),

    PYTHON(
        displayName = "Python",
        extensions = setOf("py", "pyw"),
        keywords = setOf(
            "def", "class", "import", "from", "as", "return", "yield",
            "if", "elif", "else", "for", "while", "break", "continue",
            "try", "except", "finally", "raise", "assert", "with",
            "pass", "lambda", "global", "nonlocal", "del", "in", "is",
            "not", "and", "or", "true", "false", "none", "async", "await",
            "print", "len", "range", "str", "int", "float", "list", "dict"
        ),
        snippets = listOf(
            "def function_name(param):\n    \n    pass",
            "class ClassName:\n    def __init__(self):\n        \n",
            "if __name__ == \"__main__\":\n    \n",
            "import module",
            "from module import function",
            "if condition:\n    \nelif condition:\n    \nelse:\n    \n",
            "for item in collection:\n    \n",
            "for i in range(n):\n    \n",
            "while condition:\n    \n",
            "try:\n    \nexcept Exception as e:\n    print(e)",
            "with open('file.txt', 'r') as f:\n    \n",
            "print(\"Hello World\")"
        )
    ),

    JAVASCRIPT(
        displayName = "JavaScript",
        extensions = setOf("js", "jsx", "mjs"),
        keywords = setOf(
            "var", "let", "const", "function", "return", "if", "else", "switch",
            "case", "default", "for", "while", "do", "break", "continue",
            "try", "catch", "finally", "throw", "new", "this", "class", "extends",
            "super", "import", "export", "from", "as", "async", "await", "yield",
            "typeof", "instanceof", "in", "of", "null", "undefined", "true", "false",
            "console", "log", "document", "window", "promise"
        ),
        snippets = listOf(
            "function functionName(param) {\n    \n}",
            "const functionName = (param) => {\n    \n}",
            "const variableName = value;",
            "class ClassName {\n    constructor() {\n        \n    }\n}",
            "if (condition) {\n    \n} else {\n    \n}",
            "for (let i = 0; i < length; i++) {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (error) {\n    console.error(error);\n}",
            "const promise = new Promise((resolve, reject) => {\n    \n});",
            "async function asyncFunction() {\n    await promise;\n}",
            "console.log('Hello World');",
            "document.getElementById('id');"
        )
    ),

    CSS(
        displayName = "CSS",
        extensions = setOf("css", "scss", "sass"),
        keywords = setOf(
            "color", "background", "margin", "padding", "border", "width", "height",
            "font", "display", "position", "top", "right", "bottom", "left",
            "flex", "grid", "align-items", "justify-content", "transform",
            "transition", "animation", "media", "import"
        ),
        snippets = listOf(
            "* {\n    margin: 0;\n    padding: 0;\n    box-sizing: border-box;\n}",
            ".className {\n    \n}",
            "#idName {\n    \n}",
            "body {\n    font-family: Arial, sans-serif;\n}",
            ".container {\n    max-width: 1200px;\n    margin: 0 auto;\n}",
            "@media (max-width: 768px) {\n    \n}",
            ".button {\n    padding: 10px 20px;\n    background-color: #007bff;\n    color: white;\n}"
        )
    ),

    JSON(
        displayName = "JSON",
        extensions = setOf("json"),
        keywords = setOf("true", "false", "null"),
        snippets = listOf(
            "{\n    \"key\": \"value\",\n    \"number\": 0,\n    \"array\": [],\n    \"object\": {}\n}",
            "[\n    {\n        \"id\": 1,\n        \"name\": \"\"\n    }\n]"
        )
    ),

    XML(
        displayName = "XML",
        extensions = setOf("xml"),
        keywords = setOf("xml", "version", "encoding"),
        snippets = listOf(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>",
            "<element attribute=\"value\">\n    \n</element>"
        )
    ),

    SQL(
        displayName = "SQL",
        extensions = setOf("sql", "sqlite"),
        keywords = setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "drop", "alter", "add", "column", "index",
            "primary", "key", "foreign", "references", "unique", "not", "null",
            "join", "left", "right", "inner", "outer", "on", "group", "by",
            "order", "asc", "desc", "limit", "having", "distinct", "count", "sum", "avg"
        ),
        snippets = listOf(
            "SELECT * FROM table_name WHERE condition;",
            "INSERT INTO table_name (column1, column2) VALUES (value1, value2);",
            "UPDATE table_name SET column1 = value1 WHERE condition;",
            "DELETE FROM table_name WHERE condition;",
            "CREATE TABLE table_name (\n    id INTEGER PRIMARY KEY,\n    name TEXT\n);",
            "SELECT * FROM table1 JOIN table2 ON table1.id = table2.id;"
        )
    ),

    MARKDOWN(
        displayName = "Markdown",
        extensions = setOf("md", "markdown"),
        keywords = setOf(),
        snippets = listOf(
            "# Heading 1\n## Heading 2\n### Heading 3",
            "**bold text**",
            "*italic text*",
            "`inline code`",
            "- List item 1\n- List item 2",
            "[link text](url)",
            "![alt text](image-url)"
        )
    ),

    SHELL(
        displayName = "Shell",
        extensions = setOf("sh", "bash", "zsh"),
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "do", "done", "while",
            "until", "case", "esac", "in", "function", "return", "exit",
            "echo", "read", "export", "source", "cd", "pwd", "ls", "cat", "grep"
        ),
        snippets = listOf(
            "#!/bin/bash",
            "if [ condition ]; then\n    \nfi",
            "for i in {1..10}; do\n    \ndone",
            "while [ condition ]; do\n    \ndone",
            "function myFunction() {\n    \n}",
            "echo \"Hello World\"",
            "read -p \"Prompt: \" variable"
        )
    ),

    PLAIN_TEXT(
        displayName = "Plain Text",
        extensions = setOf("txt", "log", "cfg", "conf"),
        keywords = setOf(),
        snippets = listOf()
    );

    companion object {
        fun fromExtension(ext: String): Language {
            return values().find { ext.lowercase() in it.extensions } ?: PLAIN_TEXT
        }
    }
}
