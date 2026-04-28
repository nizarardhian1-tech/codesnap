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
            "const", "lateinit", "suspend", "coroutine", "async", "await", "let",
            "run", "apply", "also", "takeIf", "takeUnless", "repeat", "unit",
            "Int", "String", "Boolean", "Long", "Double", "Float", "Char", "Byte",
            "Short", "Array", "List", "Map", "Set", "Collection", "Sequence", "Flow",
            "androidx", "activity", "fragment", "viewmodel", "livedata", "coroutines"
        ),
        snippets = listOf(
            "fun main() {\n    \n}",
            "class ClassName {\n    \n}",
            "data class DataClass(val id: Int, val name: String)",
            "object Singleton {\n    \n}",
            "interface InterfaceName {\n    \n}",
            "enum class EnumName {\n    OPTION1, OPTION2\n}",
            "sealed class SealedClass {\n    \n}",
            "fun functionName(param: Type): ReturnType {\n    return value\n}",
            "val variableName: Type = value",
            "var mutableVariable: Type = value",
            "if (condition) {\n    \n} else {\n    \n}",
            "when (value) {\n    option1 -> {}\n    else -> {}\n}",
            "for (item in collection) {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (e: Exception) {\n    \n} finally {\n    \n}",
            "lifecycleScope.launch {\n    \n}",
            "viewModelScope.launch {\n    \n}",
            "val liveData = MutableLiveData<Type>()",
            "val viewModel = ViewModelProvider(this)[ViewModelClass::class.java]",
            "@Composable\nfun ComposableFunction() {\n    \n}"
        )
    ),

    JAVA(
        displayName = "Java",
        extensions = setOf("java"),
        keywords = setOf(
            "public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "enum", "extends", "implements", "import",
            "package", "void", "int", "long", "double", "float", "boolean",
            "char", "byte", "short", "String", "Object", "null", "true", "false",
            "if", "else", "switch", "case", "default", "for", "while", "do",
            "break", "continue", "return", "try", "catch", "finally", "throw",
            "throws", "new", "this", "super", "instanceof", "synchronized",
            "volatile", "transient", "native", "strictfp", "assert", "var"
        ),
        snippets = listOf(
            "public class ClassName {\n    public static void main(String[] args) {\n        \n    }\n}",
            "public class ClassName {\n    \n}",
            "public interface InterfaceName {\n    \n}",
            "public enum EnumName {\n    OPTION1, OPTION2\n}",
            "public void methodName() {\n    \n}",
            "public int methodName(int param) {\n    return 0;\n}",
            "private String fieldName;",
            "public ClassName() {\n    \n}",
            "if (condition) {\n    \n} else {\n    \n}",
            "switch (variable) {\n    case value:\n        break;\n    default:\n        \n}",
            "for (int i = 0; i < length; i++) {\n    \n}",
            "for (Type item : collection) {\n    \n}",
            "while (condition) {\n    \n}",
            "do {\n    \n} while (condition);",
            "try {\n    \n} catch (Exception e) {\n    e.printStackTrace();\n} finally {\n    \n}",
            "List<Type> list = new ArrayList<>();",
            "Map<KeyType, ValueType> map = new HashMap<>();",
            "public static final String CONSTANT = \"value\";",
            "@Override\npublic String toString() {\n    return super.toString();\n}",
            "Thread thread = new Thread(() -> {\n    \n});\nthread.start();"
        )
    ),

    CPP(
        displayName = "C++",
        extensions = setOf("cpp", "cc", "cxx", "c++", "hpp", "h", "hxx"),
        keywords = setOf(
            "include", "define", "ifdef", "ifndef", "endif", "pragma",
            "int", "long", "short", "float", "double", "char", "bool", "void",
            "signed", "unsigned", "const", "static", "extern", "register",
            "volatile", "inline", "virtual", "explicit", "friend", "typedef",
            "class", "struct", "union", "enum", "namespace", "using", "template",
            "typename", "public", "private", "protected", "operator", "this",
            "new", "delete", "throw", "try", "catch", "nullptr", "NULL",
            "if", "else", "switch", "case", "default", "for", "while", "do",
            "break", "continue", "return", "goto", "sizeof", "alignof",
            "auto", "decltype", "constexpr", "noexcept", "override", "final"
        ),
        snippets = listOf(
            "#include <iostream>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}",
            "#include <vector>\n#include <string>\n#include <map>\n#include <algorithm>",
            "class ClassName {\npublic:\n    ClassName();\n    ~ClassName();\nprivate:\n    \n};",
            "struct StructName {\n    int field1;\n    string field2;\n};",
            "void functionName(int param) {\n    \n}",
            "int functionName(int param) {\n    return 0;\n}",
            "template<typename T>\nT functionName(T param) {\n    return param;\n}",
            "vector<int> vec = {1, 2, 3};",
            "map<string, int> myMap;",
            "for (int i = 0; i < n; i++) {\n    \n}",
            "for (auto& item : collection) {\n    \n}",
            "while (condition) {\n    \n}",
            "if (condition) {\n    \n} else {\n    \n}",
            "switch (variable) {\n    case value:\n        break;\n    default:\n        \n}",
            "try {\n    \n} catch (const exception& e) {\n    cerr << e.what() << endl;\n}",
            "int* ptr = new int(0);\ndelete ptr;",
            "cout << \"Hello\" << endl;",
            "cin >> variable;",
            "#define MACRO_NAME value",
            "const int CONSTANT = 100;"
        )
    ),

    LUA(
        displayName = "Lua",
        extensions = setOf("lua"),
        keywords = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "if", "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while", "goto", "self",
            "print", "type", "pairs", "ipairs", "tonumber", "tostring",
            "string", "table", "math", "os", "io", "coroutine", "debug"
        ),
        snippets = listOf(
            "function functionName(param)\n    \nend",
            "local variableName = value",
            "local function localFunction()\n    \nend",
            "if condition then\n    \nelse\n    \nend",
            "for i = 1, n do\n    \nend",
            "for i, v in ipairs(table) do\n    \nend",
            "for k, v in pairs(table) do\n    \nend",
            "while condition do\n    \nend",
            "repeat\n    \nuntil condition",
            "local tab = {\n    key = value,\n}",
            "function ClassName:method()\n    \nend",
            "print(\"Hello World\")",
            "local result = pcall(function()\n    \nend)",
            "coroutine.create(function()\n    \nend)",
            "require(\"module\")"
        )
    ),

    HTML(
        displayName = "HTML",
        extensions = setOf("html", "htm", "xhtml"),
        keywords = setOf(
            "html", "head", "body", "div", "span", "p", "a", "img", "ul", "ol",
            "li", "table", "tr", "td", "th", "form", "input", "button", "select",
            "option", "textarea", "label", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "footer", "nav", "section", "article", "aside", "main",
            "script", "style", "link", "meta", "title", "base", "br", "hr",
            "iframe", "video", "audio", "source", "canvas", "svg", "path"
        ),
        snippets = listOf(
            "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>Document</title>\n</head>\n<body>\n    \n</body>\n</html>",
            "<div class=\"\">\n    \n</div>",
            "<span class=\"\">\n    \n</span>",
            "<a href=\"\">\n    \n</a>",
            "<img src=\"\" alt=\"\" />",
            "<ul>\n    <li></li>\n</ul>",
            "<ol>\n    <li></li>\n</ol>",
            "<table>\n    <tr>\n        <td></td>\n    </tr>\n</table>",
            "<form action=\"\" method=\"post\">\n    <input type=\"text\" name=\"\" />\n    <button type=\"submit\">Submit</button>\n</form>",
            "<input type=\"text\" name=\"\" placeholder=\"\" />",
            "<button type=\"button\">\n    \n</button>",
            "<select name=\"\">\n    <option value=\"\"></option>\n</select>",
            "<textarea name=\"\" rows=\"5\"></textarea>",
            "<label for=\"\"></label>",
            "<header>\n    \n</header>",
            "<footer>\n    \n</footer>",
            "<nav>\n    \n</nav>",
            "<section>\n    \n</section>",
            "<article>\n    \n</article>",
            "<script>\n    \n</script>",
            "<style>\n    \n</style>",
            "<link rel=\"stylesheet\" href=\"\" />",
            "<meta name=\"\" content=\"\" />",
            "<video src=\"\" controls></video>",
            "<canvas id=\"\" width=\"400\" height=\"400\"></canvas>"
        )
    ),

    PYTHON(
        displayName = "Python",
        extensions = setOf("py", "pyw", "pyi"),
        keywords = setOf(
            "def", "class", "import", "from", "as", "return", "yield",
            "if", "elif", "else", "for", "while", "break", "continue",
            "try", "except", "finally", "raise", "assert", "with",
            "pass", "lambda", "global", "nonlocal", "del", "in", "is",
            "not", "and", "or", "True", "False", "None", "async", "await",
            "print", "len", "range", "str", "int", "float", "list", "dict",
            "set", "tuple", "bool", "type", "isinstance", "hasattr", "getattr"
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
            "try:\n    \nexcept Exception as e:\n    print(e)\nfinally:\n    \n",
            "with open('file.txt', 'r') as f:\n    \n",
            "list_comprehension = [x for x in iterable]",
            "dict_comprehension = {k: v for k, v in items}",
            "lambda x: x + 1",
            "@decorator\ndef function():\n    \n",
            "async def async_function():\n    await something()",
            "print(\"Hello World\")",
            "result = map(function, iterable)",
            "result = filter(function, iterable)",
            "sorted_list = sorted(iterable, key=lambda x: x)"
        )
    ),

    JAVASCRIPT(
        displayName = "JavaScript",
        extensions = setOf("js", "jsx", "mjs", "cjs"),
        keywords = setOf(
            "var", "let", "const", "function", "return", "if", "else", "switch",
            "case", "default", "for", "while", "do", "break", "continue",
            "try", "catch", "finally", "throw", "new", "this", "class", "extends",
            "super", "import", "export", "from", "as", "async", "await", "yield",
            "typeof", "instanceof", "in", "of", "null", "undefined", "true", "false",
            "console", "log", "error", "warn", "info", "document", "window", "Promise"
        ),
        snippets = listOf(
            "function functionName(param) {\n    \n}",
            "const functionName = (param) => {\n    \n}",
            "const variableName = value;",
            "let mutableVariable = value;",
            "class ClassName {\n    constructor() {\n        \n    }\n}",
            "if (condition) {\n    \n} else {\n    \n}",
            "switch (variable) {\n    case value:\n        break;\n    default:\n        \n}",
            "for (let i = 0; i < length; i++) {\n    \n}",
            "for (const item of array) {\n    \n}",
            "for (const key in object) {\n    \n}",
            "while (condition) {\n    \n}",
            "try {\n    \n} catch (error) {\n    console.error(error);\n}",
            "const promise = new Promise((resolve, reject) => {\n    \n});",
            "async function asyncFunction() {\n    await promise;\n}",
            "const array = [1, 2, 3];",
            "const object = { key: value };",
            "console.log('Hello World');",
            "document.getElementById('id');",
            "document.querySelector('.class');",
            "array.map(item => item.property)",
            "array.filter(item => condition)",
            "array.reduce((acc, item) => acc + item, 0)"
        )
    ),

    CSS(
        displayName = "CSS",
        extensions = setOf("css", "scss", "sass", "less"),
        keywords = setOf(
            "color", "background", "background-color", "background-image",
            "margin", "padding", "border", "width", "height", "font", "font-size",
            "font-weight", "font-family", "text-align", "text-decoration",
            "display", "position", "top", "right", "bottom", "left", "float",
            "clear", "overflow", "visibility", "opacity", "z-index", "flex",
            "grid", "align-items", "justify-content", "flex-direction", "gap",
            "transform", "transition", "animation", "keyframes", "media", "import"
        ),
        snippets = listOf(
            "* {\n    margin: 0;\n    padding: 0;\n    box-sizing: border-box;\n}",
            ".className {\n    \n}",
            "#idName {\n    \n}",
            "body {\n    font-family: Arial, sans-serif;\n    \n}",
            "div {\n    display: flex;\n    align-items: center;\n    justify-content: center;\n}",
            ".container {\n    max-width: 1200px;\n    margin: 0 auto;\n    padding: 0 20px;\n}",
            "@media (max-width: 768px) {\n    \n}",
            "@keyframes animationName {\n    0% { }\n    100% { }\n}",
            ".button {\n    padding: 10px 20px;\n    background-color: #007bff;\n    color: white;\n    border: none;\n    cursor: pointer;\n}",
            ".card {\n    border: 1px solid #ddd;\n    border-radius: 8px;\n    padding: 16px;\n    box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n}"
        )
    ),

    JSON(
        displayName = "JSON",
        extensions = setOf("json"),
        keywords = setOf("true", "false", "null"),
        snippets = listOf(
            "{\n    \"key\": \"value\",\n    \"number\": 0,\n    \"boolean\": true,\n    \"null\": null,\n    \"array\": [],\n    \"object\": {}\n}",
            "[\n    {\n        \"id\": 1,\n        \"name\": \"\"\n    }\n]",
            "{\n    \"name\": \"\",\n    \"version\": \"1.0.0\",\n    \"description\": \"\"\n}"
        )
    ),

    XML(
        displayName = "XML",
        extensions = setOf("xml", "xsd", "xslt"),
        keywords = setOf("xml", "version", "encoding", "DOCTYPE", "CDATA"),
        snippets = listOf(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    \n</root>",
            "<element attribute=\"value\">\n    \n</element>",
            "<!-- Comment -->"
        )
    ),

    SQL(
        displayName = "SQL",
        extensions = setOf("sql", "sqlite", "db"),
        keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "INDEX",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "NOT", "NULL",
            "DEFAULT", "AUTO_INCREMENT", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
            "ON", "GROUP", "BY", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET",
            "HAVING", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX", "LIKE",
            "IN", "BETWEEN", "EXISTS", "UNION", "ALL", "AS", "CASE", "WHEN", "THEN", "END"
        ),
        snippets = listOf(
            "SELECT * FROM table_name WHERE condition;",
            "SELECT column1, column2 FROM table_name;",
            "INSERT INTO table_name (column1, column2) VALUES (value1, value2);",
            "UPDATE table_name SET column1 = value1 WHERE condition;",
            "DELETE FROM table_name WHERE condition;",
            "CREATE TABLE table_name (\n    id INT PRIMARY KEY AUTO_INCREMENT,\n    name VARCHAR(100),\n    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n);",
            "DROP TABLE table_name;",
            "SELECT * FROM table1 JOIN table2 ON table1.id = table2.id;",
            "SELECT COUNT(*) FROM table_name;",
            "SELECT * FROM table_name ORDER BY column_name DESC LIMIT 10;"
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
            "```language\ncode block\n```",
            "- List item 1\n- List item 2",
            "1. Numbered item 1\n2. Numbered item 2",
            "[link text](url)",
            "![alt text](image-url)",
            "> blockquote",
            "---\n(horizontal rule)",
            "| Column 1 | Column 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |"
        )
    ),

    SHELL(
        displayName = "Shell/Bash",
        extensions = setOf("sh", "bash", "zsh", "fish"),
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "do", "done", "while",
            "until", "case", "esac", "in", "function", "return", "exit",
            "echo", "print", "read", "export", "source", "cd", "pwd", "ls",
            "cat", "grep", "sed", "awk", "find", "xargs", "pipe", "redirect"
        ),
        snippets = listOf(
            "#!/bin/bash",
            "if [ condition ]; then\n    \nfi",
            "if [ condition ]; then\n    \nelse\n    \nfi",
            "for i in {1..10}; do\n    \ndone",
            "for file in *.txt; do\n    \ndone",
            "while [ condition ]; do\n    \ndone",
            "case $variable in\n    pattern1)\n        \n        ;;\n    *)\n        \n        ;;\nesac",
            "function myFunction() {\n    \n}",
            "myFunction() {\n    \n}",
            "echo \"Hello World\"",
            "read -p \"Prompt: \" variable",
            "export VARIABLE=value",
            "command1 | command2",
            "command > output.txt",
            "command >> output.txt"
        )
    ),

    PLAIN_TEXT(
        displayName = "Plain Text",
        extensions = setOf("txt", "log", "cfg", "conf", "ini"),
        keywords = setOf(),
        snippets = listOf()
    );

    companion object {
        fun fromExtension(ext: String): Language {
            return values().find { ext.lowercase() in it.extensions } ?: PLAIN_TEXT
        }
    }
}
