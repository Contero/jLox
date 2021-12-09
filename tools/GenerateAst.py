import os

# Generate AST java class for jlox
print ("Default output is ..\\src\\jLox\\")
folder = input ("Enter folder to override or Enter to use default>")
if folder == "":
    os.chdir("..\\src\\jLox")
    folder = os.getcwd() + "\\"

expressions = []
expressions.append("Binary : Expr left, Token operator, Expr right")
expressions.append("Grouping : Expr expression")
expressions.append("Literal : Object value")
expressions.append("Unary : Token operator, Expr right")

def defineType(file, baseName, className, fieldList):
    file.write("\tstatic class " + className + " extends " + baseName + "\n\t{\n")
    #fields
    fields = fieldList.split(", ")
    for field in fields:
        file.write("\t\tfinal " + field + ";\n")
    file.write("\n")
    
    #Visitor 
    file.write("\t\t@Override\n")
    file.write("\t\t<R> R accept(Visitor<R> visitor)\n\t\t{\n")
    file.write("\t\t\treturn visitor.visit" + className + baseName + "(this);\n")
    file.write("\t\t}\n\n")
    
    #constructor
    file.write("\t\t"+ className + "(" + fieldList + ")\n")
    file.write("\t\t{\n")
    for field in fields:
        name = field.split(" ")[1]
        file.write("\t\t\tthis." + name + "=" + name + ";\n")
    file.write("\t\t}\n\t}\n\n")
    
def defineVisitor(file, baseName, types):
    file.write("\tinterface Visitor<R>\n")
    file.write("\t{\n")
    for typ in types:
        typeName = typ.split(":")[0].strip()
        file.write("\t\tR visit" + typeName + baseName + "(" + typeName + " " + baseName.lower() + ");\n")
    file.write("\t}\n\n")

def defineAst(outputDir, baseName, types):
    filePath = outputDir + baseName + ".java"
    print("Writing to " + filePath + "...")
    file = open(filePath, "w")
    file.write("package jLox;\n\n")
    file.write("import java.util.List;\n\n")
    file.write("abstract class " + baseName)
    file.write ("\n{\n")
    
    defineVisitor(file, baseName, types)
    
    # AST classes
    for typ in types:
        className = typ.split(":")[0].strip()
        print("generating "+className+"...")
        fields = typ.split(":")[1].strip()
        defineType(file, baseName, className, fields)
    
    file.write("\tabstract <R> R accept(Visitor<R> visitor);\n");
    file.write("}")
    file.close
    print("Finished!")
    
defineAst(folder, "Expr", expressions)