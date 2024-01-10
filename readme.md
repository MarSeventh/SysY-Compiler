# SysY编译器设计文档

[toc]

## 一、参考编译器介绍

在正式开始设计编译器前，我首先阅读了**PL/0**语言的编译器源码，重点分析了其**整体结构**、**接口设计**和**文件组织**等方面的设计方法，为我后面设计SysY语言的编译器提供借鉴。

### 1.PL/0语言简介

PL0语言是一种类PASCAL语言，是教学用程序设计语言，它比PASCAL语言简单，作了一些限制。PL0的程序结构比较完全，赋值语句作为基本结构，构造概念有顺序执行、条件执行和重复执行，分别由BEGIN/END、IF和WHILE语句表示。

### 2.PL/0编译器整体结构

该编译器的整体结构可分为如下几个部分：

- 常量和限制：程序开头定义了一些常量，如保留字的数量等
- 数据类型和数据结构：定义了symbol、alfa等数据类型和其他数据结构
- 全局变量和数组：声明了很多全局变量，包括用于词法分析和语法分析的变量、保留字表、符号表等
- 函数和过程：定义了一些过程，如`error`用于报告错误信息，`getch`用于获取字符，`getsym`用于获取下一个符号等。（词法分析、语法分析、语义分析和中间代码解释执行等）
- 函数实现和主程序：实现了前面定义的一些函数，同时最后给出了编译器的主程序`main`

### 3.接口设计

该编译器主要提供了以下几个接口：

- program pl0：程序入口
- error：编译器的报错接口
- getsym：编译器的词法分析接口，接收源文件，返回词法成分
- gen：生成中间代码的接口，生成中间代码
- interpret：解释执行生成的中间代码

### 4.文件组织

由于PL/0语言较为简洁，因此该编译器的文件结构也比较简单，仅仅分为源代码文件、输入文件和输出文件。其中源代码文件中还内嵌了类似于其他编译器的词法分析规则文件（关键字、保留字、常量等）和辅助文件（错误消息文件、中间代码文件）等。

不同于其他现实中的编译器，该编译器没有独立的代码优化、符号表管理等文件，这是由其自身的简单性所决定的。

## 二、编译器总体设计

经过对参考编译器的分析以及对其他一些资料的学习，我对自己将要设计的SysY语言编译器大致有了一个轮廓，下面将从**整体结构**、**接口设计**和**文件组织**三方面介绍我的初步设计思路。

### 1.整体结构

整个编译器我打算采用**$1+4+N$**的架构（如下图）进行设计，1指的就是编译器的程序入口，4分别指**词法分析子程序**、**语法分析子程序**、**语义分析和中间代码生成子程序**和**目标代码生成**子程序，$N$则指的是其他众多的辅助程序，例如错误处理子程序、关键字分析子程序等等。

同时，为了使编译器结构更加明晰，我还打算尽量将更多的子功能提取出来作为单独的子程序，这样不仅可以提高编译器程序的可读性，也有利于bug检查和代码优化。

![](https://alist.sanyue.site/d/imgbed/%E6%95%B4%E4%BD%93%E7%BB%93%E6%9E%84.png)

### 2.接口设计

目前我设计的主要接口有如下几个：

- Compiler：`main`作为编译器的入口
- 词法分析器：提供词法分析接口，接收源文件地址，返回提取出来的词法成分（类型、值等）
- 语法分析器：提供语法分析接口，接收词法分析器输出的词法成分，按照文法规则自顶向下分析出其中的语法成分。
- 语义分析和中间代码生成器：根据分析出来的语法成分进行语义分析，输出中间代码
- 目标代码生成器：翻译中间代码，生成MIPS汇编代码

### 3.文件组织

我采用的文件组织方式依托于整体架构，以清晰易读为主要目的，将架构的各个模块尽可能地拆分到多个文件中，采用面向对象的思想，让各个模块尽量遵循“高内聚，低耦合”的设计原则。

详细地说，就是有一个主程序文件作为程序入口，然后剩下4个关键部分分别作为四个程序文件，其他辅助工具、数据类型定义（如错误处理，关键字识别，保留字表，语法树节点的数据结构）等再分散到其他更加细小的文件当中，让各个模块各司其职，有机结合。

## 三、词法分析设计

### 1.编码前的设计

对于词法分析，我采用将线性文法转换成**确定的有穷自动机**（DFA）的方式进行解析。

首先根据文法中包含的单词类别设计多个不同的自动机，然后每一次按照读到的字符类型进入相应自动机，如果最终产生了被该自动机接受的单词，就将其类型和值返回，直到文件读取结束，如此就实现了词法分析功能。

具体而言，我计划首先定义一个`Lexer`类，该类即为我的编译器词法分析子程序的主类，在该类中将会实现字符的扫描、各个自动机的判断、单词类型和值的保存等功能。除此之外，还需要定义一个`LexType`类，该类作为一个枚举类，枚举所有可能出现的单词类型，便于其他程序进行单词类型的比较和定义。

使用这两个类，就已经基本可以完成单词的扫描和词法成分分析的工作。

### 2.编码之后的修改

编码之后的修改主要在于错误处理的地方，之前考虑的是在`Lexer`类里面定义一个方法来做错误处理，但是由于后面的各个阶段依然会出现需要错误处理的情况，为了尽可能实现模块化，我将错误处理定义为一个新的类`Error`，该类通过接收错误发生处的行号、值、错误类型的参数，选择对应的错误处理方法执行，输出相应的错误报告。

其次还有一个无关紧要的点就是我将输出单词类型的语句从`Lexer`类外提取到了类内，作为一个新的方法，虽然对于编译器本身而言没有什么实质性用处，但是很好地为后续实验要求的各种输出以及bug的查找提供了较为方便的接口，更加有利于后续的迭代开发。

#### 2.1注释处理部分的修改

在错误处理部分编码完成后，我发现词法分析部分在处理注释时有一些遗留错误和没有考虑到的点。

没有考虑到的点在于对于`/*/`类型注释的处理，之前在处理`/*`之后会直接再读取一个字符，如果不是`*`就会直接跳过，也就是说`/`被直接跳了过去，导致后面所有内容都被识别为注释。

错误的点第一在于处理多行注释时没有让行号递增，使得后面解析到的词法成分行号都出现错误。解决这个问题的方法时识别`/*`之后直接再读取一个符号，如果是`/`就退出注释，否则退一个字符在执行后续判断。

第二在于寻找末尾`/`时只有前面有奇数个`*`时才能成功识别，否则就会把`/`跳过，为了解决这个问题，将原来识别到`*`时的`if`语句修改为`while`语句，使其一直识别`*`直到遇到`/`结束，这样就不会受到`*`的个数的影响。

## 四、语法分析设计

### 1.编码前设计

对于语法分析部分，我打算采用**递归下降**的方式进行解析。

由于文法中出现了很多右部First集合有重叠的多选式，为了避免回溯，我采用预读的方式进行判断，这就需要构造一个便于预读和回退的数据结构对词法分析的结果加以保存。对于这个问题，我设计了`Word`类，该类保存了一个词法成分的类型和具体值。

除此之外，为了将解析出来的语法成分结构化，我们需要建立一个树状的数据结构保存出来的语法成分。初步思路是构造一个节点类，将自己的语法类型以及子节点列表作为属性，然后将各个语法成分定义为该类的子类，在每次递归结束后返回自身节点，挂到父节点上即可。

对于左递归的问题，我都使用合并规则右部的方法修改规则，使得文法不在出现左递归，便于后续递归下降的解析。

### 2.编码后修改

在编码过程中，我发现如果采用原来将所有语法成分都作为节点类子类的思想会导致文件结构过于繁杂，多出很多相似的类。因此，我修改了建树这一部分的数据结构，将所有语法成分都用同一个类`GrammarNode`表示，该类的一个属性表示目前节点代表什么语法成分。这样不仅大大减少了重复的代码量，还让整个文件的结构更为清晰。

在这一部分整体完成之后，我发现由于之前为了解决左递归问题修改了文法，导致在对应文法的解析部分输出与原文法的输出逻辑不符。为了解决这一问题，我在对应规则的解析子方法中采用了一点自底向上分析的方式。即在遇到第二个重复的同类节点后，就将已有节点和新的子节点挂在一个新的共同父节点上，最后将最顶层的父节点作为返回值返回。（一个例子如下）

```java
	public GrammarNode parseLOrExp() {
        GrammarNode node = new GrammarNode("LOrExp");
        node.addChild(parseLAndExp());
        while (curType == LexType.OR) {
            printToFile("LOrExp");
            GrammarNode newNode = new GrammarNode("LOrExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseLAndExp());
            node = newNode;
        }
        printToFile("LOrExp");
        return node;
    }
```

除了这两点以外，其他部分的实现基本和编码前的设计相同。

## 五、错误处理设计

错误处理主要要求输出以下错误类型：

| 错误类型                             | 错误类别码 | 解释                                                         | 对应文法及出错符号 ( … 表示省略该条规则后续部分)             |
| ------------------------------------ | ---------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 非法符号                             | a          | 格式字符串中出现非法字符报错行号为 **<FormatString>** 所在行数。 | <FormatString> → ‘“‘{<Char>}’”                               |
| 名字重定义                           | b          | 函数名或者变量名在**当前作用域**下重复定义。注意，变量一定是同一级作用域下才会判定出错，不同级作用域下，内层会覆盖外层定义。报错行号为 **<Ident>** 所在行数。 | <ConstDef>→<Ident> … <VarDef>→<Ident> … <Ident> … <FuncDef>→<FuncType><Ident> … <FuncFParam> → <BType> <Ident> … |
| 未定义的名字                         | c          | 使用了未定义的标识符报错行号为 **<Ident>** 所在行数。        | <LVal>→<Ident> … <UnaryExp>→<Ident> …                        |
| 函数参数个数不匹配                   | d          | 函数调用语句中，参数个数与函数定义中的参数个数不匹配。报错行号为函数调用语句的**函数名**所在行数。 | <UnaryExp>→<Ident>‘(’[<FuncRParams>]‘)’                      |
| 函数参数类型不匹配                   | e          | 函数调用语句中，参数类型与函数定义中对应位置的参数类型不匹配。报错行号为函数调用语句的**函数名**所在行数。 | <UnaryExp>→<Ident>‘(’[<FuncRParams>]‘)’                      |
| 无返回值的函数存在不匹配的return语句 | f          | 报错行号为 **‘return’** 所在行号。                           | <Stmt>→‘return’ {‘[’<Exp>’]’}‘;’                             |
| 有返回值的函数缺少return语句         | g          | 只需要考虑函数末尾是否存在return语句，**无需考虑数据流**。报错行号为函数**结尾的’}’** 所在行号。 | <FuncDef> → <FuncType> <Ident> ‘(’ [<FuncFParams>] ‘)’ <Block> <MainFuncDef> → ‘int’ ‘main’ ‘(’ ‘)’ <Block> |
| 不能改变常量的值                     | h          | <LVal>为常量时，不能对其修改。报错行号为 **<LVal>** 所在行号。 | <Stmt>→<LVal>‘=’ <Exp>‘;’ <Stmt>→<LVal>‘=’ ‘getint’ ‘(’ ‘)’ ‘;’ |
| 缺少分号                             | i          | 报错行号为分号**前一个非终结符**所在行号。                   | <Stmt>,<ConstDecl>及<VarDecl>中的’;’                         |
| 缺少右小括号’)’                      | j          | 报错行号为右小括号**前一个非终结符**所在行号。               | 函数调用(<UnaryExp>)、函数定义(<FuncDef>)及<Stmt>中的’)’     |
| 缺少右中括号’]’                      | k          | 报错行号为右中括号**前一个非终结符**所在行号。               | 数组定义(<ConstDef>,<VarDef>,<FuncFParam>)和使用(<LVal>)中的’]’ |
| printf中格式字符与表达式个数不匹配   | l          | 报错行号为 **‘printf’** 所在行号。                           | <Stmt> →‘printf’‘(’<FormatString>{,<Exp>}’)’‘;’              |
| 在非循环块中使用break和continue语句  | m          | 报错行号为 **‘break’** 与 **’continue’** 所在行号。          | <Stmt>→‘break’‘;’ <Stmt>→‘continue’‘;’                       |

### 1.编码前设计

#### 1.1数据结构设计

根据课上老师的讲解以及课本的内容，我们知道错误处理的核心就在于**符号表的构建**。符号表应该采用栈式结构，进入一个作用域时就在栈顶新增一个子表，在表中遇到变量、常量等的定义就将相应的定义存入子表中，如果要使用已定义的变量、常量或函数，则需要从该层子表开始回溯到栈底的子表，直到找出有关定义为止。

为了实现该结构，我首先定义了`TableItem`和`Table`两个类。其中`TableItem`用来存储表项，有name、kind、type、level、dimension等公共属性以及为函数特别定义的parasNum和parasDimen等用来存储形参有关内容的专有属性。`Table`则是符号表类，将表项`TableItem`的集合作为属性，同时定义了插入、查找等操作表项的方法。

这两个基本数据结构定义好之后，还需要进行最后一步的封装操作，将`Table`作为子表存入栈式符号表当中。这里我计划采用Hashmap结构，key值为本层子表在栈中的层数，value即是本层子表的引用。这样不仅能够方便地实现栈结构的各种功能，还优化了栈的查询和构造过程。

#### 1.2整体思路

分析上表中列出的错误类型，我们可以将它们分成两大类，第一类是在词法分析过程中处理的（如a），第二类则是在语法分析过程中处理的（剩下的类型）。

对于第一类，处理方案是在词法分析过程中对非法符号进行特判，遇到不符合`<FormatString>`要求的非法符号就调用`Error`接口进行输出。

对于第二类，我们可以继续细分，将其分成**建表时错误**（b）、**查表时错误**（cdefgh）以及**表无关错误**（ijklm）。对于建表时错误，需要在向`Table`中插入表项时判断名字是否重定义等。对于查表时错误，需要在使用相应的变量、常量或者函数时，从表中读取其kind、type、dimension以及函数形参等信息，并和当前使用处的要求做匹配，发现类型或者维数、参数个数、类型不匹配等就调用错误接口报错。对于表无关错误，需要在语法分析解析到相应位置时判断是否此处的符号和期望符号不同，例如缺少括号等，如果缺少则调用错误接口输出。在处理`printf`格式字符与表达式个数不匹配的问题时，一个小trick是在词法分析解析格式字符的过程中将其中`d%`的个数统计保存到`number`属性当中，便于在语法分析判断错误时直接调用进行比较。

#### 1.3一些注意点

1. 建立新的子表和弹出栈顶表的时机：我们知道建立子表的作用是区分不同的作用域，因此建表的时机自然是在进入新的作用域时，具体表现在**函数定义**和新的**Block**，处，而函数定义有一点特殊之处，即他的形参也要存到新的子表中，所以在解析到Block时需要特判，如果不是函数形参后的Block就新建子表，否则和形参使用同一个表，无需新建子表。弹表的设计在于从一个作用域出来的时候，也就是函数定义结束或者Block结束的时候，这时同样注意函数定义的Block和普通Block的区别即可。
2. 函数参数类型匹配性的判断：对于SysY语言而言，参数类型的匹配主要在于参数维度的比较。传入实参的维度可能有0维、1维、2维、-1维（void）四种类型。由于文法的限制，传入的实参如果是一个运算表达式，那么它一定是0维，否则可以是一维数组（1维）、二维数组（2维）以及函数（0维），那么对于形参维数的判断其实就只需要判断最外层是一个几维数组即可，这可以通过标识符代表的维数减去后面所跟中括号的个数来实现。

### 2.编码后修改

按照编码前的设计思路，本次的编码完成得还算比较顺利，数据结构和实现逻辑基本遵循了之前设计好的框架，只是在一些未考虑到的细节上做了些许修改。

比较明显的有以下几点：

#### 2.1函数实参的处理

由于之前构造语法树时给每一个解析的方法都增加了树节点作为返回值，因此现在无法再使用实参解析方法的返回值来表示实参的个数、类型等信息。退而求其次，我给`parse`类增加了表示实参个数的私有属性`paraRDimen`来记录函数的实参个数，而实参类型则通过解析函数调用的方法在调用实参解析方法时传入新的ArrayList列表来存储。通过这两个数据，就能够保存下来调用函数的实参个数和类型，从而进行个数和类型匹配的判断。

#### 2.2当前处理函数的处理

由于`return`等语句只能在函数体内出现，因此为了表示当前正在解析的代码是否位于函数内，亦或是位于什么函数内，我采用`isFuncDef`来表示前者，`nowFunc`来表示后者。在进入函数定义时将`isFuncDef`置为true，并将当前函数的表项存在`nowFunc`当中。这么做还解决了一个潜在的问题，即如果定义重名函数时当前函数的表项不会存入符号表，如果通过查符号表来获取当前函数信息会出现一些bug（如return返回值类型错误无法正确处理），而`nowFunc`的引入很好地解决了这一问题，即使当前函数没有存入符号表，但存入了`nowFunc`当中，所以其中的相关错误也可以继续处理，避免漏掉错误或错误误报。

#### 2.3循环的处理

由于`break`和`continue`需要判断当前语句是否处于循环体内，因此必须有一个标记来记录这一信息。对于这一问题，我定义了`loopNum`来表示当前处于循环体的层数，当进入新的循环体时`llopNum`自增，离开循环体时`loopNum`自减，遇到break和continue时只要`loopNum`不为0就说明语句合法。

#### 2.4其他遗留问题

在本次迭代后的测试过程中，我还测试出了几个词法分析部分的历史遗留bug，主要体现在词法分析时注释的处理部分，具体说明见词法分析部分的编码后修改。

## 六、中间代码生成

### 1.四元式设计

#### 1.1主函数和Block块

```
main_begin,_,_,_

block_begin,_,_,_
xxxxxxx
block_end,_,_,_

block_begin,_,_,_
xxxxxxx
block_end,_,_,_

main_end,_,_,_
```

#### 1.2表达式

遵循 op,操作数1,操作数2,结果 的格式

```
//如x+y
ADD, x, y, t1
//!x等二目运算
NOT, 0, x, t0
```

#### 1.3赋值语句

变量赋值：

```
//如x = a
ASSIGN, a, , x
```

涉及数组元素：

此处对之前错误处理的符号表进行修改，添加对数组每一维大小的记录，服务于多维数组的计算。在解析到数组定义时直接计算出每一维表达式的结果，将其存入符号表中。

```
//用数组元素赋值
//一维数组，如a = num[0]
ARRAY_GET, num, 0, t1
ASSIGN, t1, , a
//二维数组, 如a = num[1][0] (num定义为num[4][4])
MUL, 1, 4, t1
ADD, 0, t1, t2
ARRAY_GET, num, t2, t3
ASSIGN, t3, , a

//给数组赋值
如num[2][3] = b
MUL, 2, 4, t1
ADD, 3, t1, t2
ASSIGN, b, t2, num
```

#### 1.4条件语句

采用的跳转和分支指令和MIPS基本相同，有`bne, beq, ble, blt, bge, bgt`等，无条件跳转采用`jmp`。

例如对于`'if' '(' Cond ')' Stmt [ 'else' Stmt ]`，遵循以下步骤：

1.计算条件表达式的值，存入t1

2.生成条件跳转四元式`beq, t1, 0, else1`

3.生成Stmt执行语句

4.生成无条件跳转四元式`jmp, out, , `

5.生成标签`else1`

6.生成Stmt执行语句

7.生成标签`out`

对于条件表达式的计算，遵循短路求值的原则，在每一个子语句计算完成之后更新最外层`t1`的值，若`t1==1`则跳转到下一个`&&`的地方，若`t1==0`则跳转到下一个`||`的地方。

#### 1.5循环语句

对于`for`类型循环语句，需要用跳转指令和标记来进行循环控制。例如对于`'for' '(' ForStmt1 ';' Cond ';' ForStmt2 ')' Stmt`，遵循以下步骤：

1.执行循环变量赋值语句`ForStmt1`

2.生成标签`checkin`

3.计算条件表达式的值，存入`t`

4.生成条件跳转四元式`beq, t, 0, out`

5.生成`Stmt`的四元式

6.生成标签`update`

7.执行循环变量更新语句`ForStmt2`

8.生成无条件跳转四元式`jmp, checkin, , `

9.生成标签`out`

对于`break`语句，执行以下操作：

1.生成无条件跳转语句`jmp, out, , `

对于`continue`语句，执行以下操作：

1.生成无条件跳转语句`jmp, update, , `

#### 1.6函数定义

在处理函数定义时，需要生成函数的入口代码、参数初始化代码、局部变量的声明和初始化、函数体中的控制流程代码和函数的返回值等。

对于：

```c
int f(int a,int b){
    xxxx
    return xxx;
}
```

生成的四元式如下：

```
func_begin,f,_,_
DEF,a,_,_
DEF,b,_,_
xxxxxxxx
RET, t0, , 
func_end,f,_,_
```

#### 1.7函数调用

函数调用时调用方需要将参数、返回地址（由call指令执行）等压入操作数栈。被调用方从操作数栈中取出返回地址和参数后执行函数体，最终将返回值压入操作数栈，跳转到返回地址的下一条指令，之后调用方将返回值从操作数栈取出，存入临时变量。

例如，调用函数`f(x,y)`的四元式表达为：

```
ASSIGN, x, , t1
push, t1, 0, f
ASSIGN, y, ,t2
push, t2, 1, f
call, f, , 
```

返回后调用方的操作：

```
pop, , , r1
```

#### 1.8输入和输出

对于输入如`b = getint()`操作，生成的四元式如下：

```
getint, , 0, b
//b[1][1] = getint()
getint, , index, b
```

对于`printf`，可以将需要输出的字符串通过%d分割成多个字符串，对于每个%d，生成print_int中间代码，对于每一个字符串，生成print_str中间代码。

例如，对于如下代码：

```perl
printf("%d hello %d", x, y);
```

我们可以用如下四元式表示：

```c
assign, x, , t0
print_push, t0, 0, 
assign, y, , t1
print_push, t1, 1, 
print_int, 0, 1,
print_string, hello,_,_
print_int, 1, 1, //opnum2是print参数的最大索引号
```

#### 1.9声明语句

```
//变量 int a;
DEF, var, ,a 
//常量
DEF, const, , b
//形参
DEF, param, , c
//数组
DEF, var, array, d
```

#### 1.10生成标签

```
LABEL, , , label1
```

### 2.数据结构

新增了`IRCode`类来表示四元式，其属性有四元式的操作符，操作数，结果等内容。具体结构如下：

```java
public class IRCode {
    private IROperator operator;

    private String opIdent1;//操作数1
    private String opIdent2;//操作数2
    private String resultIdent;//结果

    private int opNum1;//操作数1
    private int opNum2;//操作数2
    private int resultNum;//结果

    private TableItem defItem = null;//符号表表项

    private boolean isAddress = false;//是否是地址

    private String printString = null;

    private boolean op1IsNum = false;
    private boolean op2IsNum = false;

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent, boolean isAddress) {//是否是地址
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.isAddress = isAddress;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent, TableItem defItem) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.defItem = defItem;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, int opNum2, String resultIdent) { //给数组赋值
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        op2IsNum = true;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, int opNum1, int opNum2, String resultIdent) { //给数组赋值
        this.operator = operator;
        this.opNum1 = opNum1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        op1IsNum = true;
        op2IsNum = true;
        printString = operator.toString() + ", " + opNum1 + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, int opNum1, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opNum1 = opNum1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        op1IsNum = true;
        printString = operator.toString() + ", " + opNum1 + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, int opNum2, String resultIdent, boolean isAddress) {//是否是地址
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        this.isAddress = isAddress;
        op2IsNum = true;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }
}
```

`IROperator`类是一个枚举类，枚举四元式的操作符。

```java
public enum IROperator {
    note,
    main_begin, main_end, block_begin, block_end, func_begin, func_end,
    ADD, SUB, MUL, DIV, MOD, ASSIGN,
    GETINT, PRINT_INT, PRINT_STR,
    AND, OR, NOT,
    ARRAY_GET,
    BNE, BEQ, BGE, BGT, BLE, BLT, JMP, LABEL,
    SLT, SLE, SEQ, SNE, SGE, SGT,
    DEF,
    CALL, RET,
    PUSH, POP;
}
```

## 七、目标代码生成

MIPS寄存器表：
![image-20231114104127459](https://alist.sanyue.site/d/imgbed/image-20231114104127459.png)

生成MIPS目标代码的工作相当于对中间代码进行了一个翻译，其中有几个需要着重考虑的问题。

### 1.寄存器分配

在不考虑优化的情况下，完全可以将所有变量都存储在内存中，寄存器仅暂存变量的值， 操作结束后将结果写入内存中的相应位置。

初步计划为`$t0-$t9`这10个寄存器开辟一个列表，每次使用寄存器时从中取出一个失效的寄存器，将数据存入后标记为有效，当该寄存器的值被存入内存或在计算中使用后被标记为失效，可被下一次使用。

### 2.数组处理

不同类型数组在存储空间中的分配方案分别为：

- 如果数组是全局数组，那么它里面的各个值依次排列在全局数据区
- 如果数组是函数中定义的局部数组，则数组中的值依次排列在对应函数的活动记录中
- 如果数组是参数数组，那么活动记录中记录的是数组的基地址

对于数组的操作分为**存取**和**数组地址传递**：

- 数组存取：对于全局数组，直接用其数组内偏移加上数组基地址进行存取。对于局部数组，根据其相对于所属活动记录基地址的偏移和所属活动记录基地址计算数组基地址，再加上数组内偏移进行存取。
- 数组地址传递：函数调用时进行判断，如果传递的数组本就是一个地址（全局或参数数组），那么直接将该地址进行传递。否则根据数组在活动记录内偏移和活动记录基地址来计算数组基地址，将求得的数组基地址进行传递。

### 3.函数调用

在函数开始时，首先将`$sp`的值赋给`$fp`，作为目前活动记录开始地址的记录。

#### 3.1调用方：

**（调用前）PUSH操作：**

调用方需要PUSH的数据有：传递的参数（前四个在`$a0-$a3`中，其他依次入栈），`$fp`的值，`$ra`的值

**（调用后）POP操作：**

调用完成后调用方需要POP的数据有（与之前PUSH的顺序相反）：`$ra`的值，`$fp`的值，函数返回值（在`$v0`中）

#### 3.2被调用方：

首先调整`$fp`的值，然后执行POP操作：将传递的参数存入自己内存的中相应形参处（前四个从`$a0-$a3`中取出，其他的从`$fp+8`开始向上取（注意需要反向出栈）

### 3.3运行栈模拟图

![运行栈](https://alist.sanyue.site/d/imgbed/运行栈.png)

### 4.MIPSCode格式约定

#### 4.1文本输出

```
//.data
text, null, null, ".data"
```

#### 4.2全局数据定义

```
//int a = 0;
dataDefine, null, null, a
//str0:hhh
strDefine, hhh, null, str0
```

#### 4.3move指令

```
//move $fp, $sp
move, $sp, null, $fp
```

#### 4.4访存类型指令

```c
//sw $t0, 0($fp)
sw, $t0, 0, $fp
//lw $t1, 4($fp)
lw, $fp, 4, $t1
//sw $v0, a + 4
sw, $v0, 4, a /**重要**/
//lw $t1, label($fp)
lw, $fp, label, $t1
//lw $t0, a + 4
lw, a, 4, $t0
```

#### 4.5计算类型指令

```
立即数相关
//addi $t1, $t0, 4 
addi, $t0, 4, $t1
三地址
//mul, t1, t2, t3 
mult, $t1, $t2, null
mflo, , , $t3
//add, t1, t2, t3
add, $t1, $t2, $t3
二元运算
//not, 0, t1, t2
seq, $t1, $0, $t2
//比较置1的操作，除了slt外都支持寄存器寄存器和寄存器立即数
slt要比较立即数必须用slti
```

#### 4.6li指令

```
//li $v0, 5
li, 5, , $v0
```

#### 4.7输入输出指令

```
//getint, , , b
li, 5, , $v0
syscall, , , 
sw, $v0, offset, $fp
```

#### 4.8标签

```
//label1
label, , , label1
```

#### 4.9跳转

```
//j label1
j, , , label1
//jal f1
jal, , , f1
//jr $ra
jr, , , $ra
```

#### 4.10数组约定

规定数组内元素地址递增，即0号元素地址为基地址。

# 代码优化文档

## 一、中端优化

### 1.死代码删除

#### 1.1无用函数删除

有些函数返回值为`void`类型，并且在执行过程中没有打印、修改全局变量、修改参数数组和调用其他函数的情况，对于这些函数我们将其调用部分直接删除。

具体实现为：

```java
public void optimizeFunc() {
        initTable();
        TableItem funcItem = null;
        boolean isDeadFunc = true;
        boolean isInFunc = false;
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin) {
                isDeadFunc = true;
                funcItem = irCode.getFuncItem();
                curTable.addItem(funcItem);
                if (funcItem.getType().equals("int")) {
                    isDeadFunc = false;
                }
                isInFunc = true;
                addLevel();
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.PRINT_STR || operator == IROperator.PRINT_INT) {
                if (isInFunc) {
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.ASSIGN || operator == IROperator.GETINT) {
                String desName = irCode.getResultIdent();
                TableItem desItem = findVarParam(desName);
                if (isInFunc && desItem != null &&
                        (desItem.isGlobal() || (desItem.getKind().equals("param") && desItem.isArray()))) {//全局变量或者数组参数
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.CALL) {
                TableItem callFuncItem = irCode.getFuncItem();
                if (funcItem != null && !callFuncItem.getName().equals(funcItem.getName())) {
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.func_end) {
                if (isDeadFunc) {
                    int originSize = optimizedIRList.size();
                    //删除所有调用该函数的语句
                    deleteDeadFunc(funcItem);
                    deleteLevel();
                    //重新运行该方法
                    if (originSize != optimizedIRList.size()) {
                        optimizeFunc();
                        return;
                    }
                }
                isInFunc = false;
                funcItem = null;
            }
        }
    }
```

#### 1.2无用定义点删除

无用定义点及相关语句的删除主要分为两部分：

- 块内无用定义点和相关语句删除
- 非活跃变量无用定义点删除

##### 1.2.1块内无用定义点删除

这一步的删除操作基于2中对基本块的划分以及到达定义数据流的分析。

主要思想是在块内如果对于某一个**局部非数组变量**进行连续多次定义(>=2)，且存在后一次定义之前完全未使用前一次定义的情况，则前一次定义为死代码，可以直接删除。

例如对于以下代码：

```java
	int i = 2,j = 5;
    const int a1 = 1, a2 = 2;
    i = getint();
    j = getint();
```

其中i和j的第一次定义完全没有用到，所以直接优化掉即可。

第一次块内无用定义点删除可以在常量传播优化完成之后进行。

具体实现如下：

```java
public boolean deleteDeadDefInBlock(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            boolean isDefPoint = isInPointMap(defPoints, irCode);
            if (isDefPoint) {
                DefPoint defPoint = getDefPointByIRCode(defPoints, irCode);
                TableItem defItem = defPoint.getDefItem();
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                int blockEnd = curBlock.getEnd();
                boolean isDeadDef = true;
                int nextDefPointNum = -1;
                for (int j = i + 1; j <= blockEnd; j++) {
                    //寻找块内该变量的下一个定义点
                    if (getSameItemDefPoint(optimizedIRList.get(j), defPoints, defItem) != null) {
                        nextDefPointNum = j;
                        break;
                    }
                }
                if (nextDefPointNum == -1) {
                    //块内该变量没有下一个定义点，保留该定义点
                    isDeadDef = false;
                } else {
                    //块内该变量有下一个定义点，判断该定义点的值是否被使用
                    for (int j = i + 1; j <= nextDefPointNum; j++) {
                        if (optimizedIRList.get(j).getOpIdent1() != null && optimizedIRList.get(j).getOpIdent1().equals(defItem.getName())) {
                            isDeadDef = false;
                            break;
                        }
                        if (optimizedIRList.get(j).getOpIdent2() != null && optimizedIRList.get(j).getOpIdent2().equals(defItem.getName())) {
                            isDeadDef = false;
                            break;
                        }
                    }
                }
                if (isDeadDef) {
                    irCode.setDead();
                }
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }
```

##### 1.2.2块间无用定义点删除

该优化主要基于**活跃变量分析**进行，如果某个**局部非数组变量**的定义在整个数据流中都不再活跃，那么直接将对应的定义点以及相关中间代码删除。

最终实现是在块内无用定义点删除的基础上增量开发，具体实现如下：

```java
public boolean deleteDeadDef(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            boolean isDefPoint = isInPointMap(defPoints, irCode);
            if (isDefPoint) {
                DefPoint defPoint = getDefPointByIRCode(defPoints, irCode);
                TableItem defItem = defPoint.getDefItem();
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                int blockEnd = curBlock.getEnd();
                boolean isDeadDef = true;
                int nextDefPointNum = -1;
                for (int j = i + 1; j <= blockEnd; j++) {
                    //寻找块内该变量的下一个定义点
                    if (getSameItemDefPoint(optimizedIRList.get(j), defPoints, defItem) != null) {
                        nextDefPointNum = j;
                        break;
                    }
                }
                if (nextDefPointNum == -1) {
                    //块内该变量没有下一个定义点，判断该定义点的值是否在该块内使用
                    isDeadDef = isDeadDef(i, defItem, blockEnd, isDeadDef);
                    //判断是否在活跃变量的out集合中
                    if (curBlock.getOutActiveVarList().contains(defItem)) {
                        isDeadDef = false;
                    }
                } else {
                    //块内该变量有下一个定义点，判断该定义点的值是否被使用
                    isDeadDef = isDeadDef(i, defItem, nextDefPointNum, isDeadDef);
                }
                if (isDeadDef) {
                    irCode.setDead();
                }
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }
```

活跃变量分析的具体实现：

```java
public void setInOutActiveVarList(HashMap<String, BasicBlock> blockMap) {
        boolean unFinished = true;
        while (unFinished) {
            unFinished = false;
            for (BasicBlock curBlock : blockMap.values()) {
                ArrayList<BasicBlock> nextBlocks = curBlock.getNextBlocks();
                ArrayList<TableItem> inActiveVarList = curBlock.getInActiveVarList();
                ArrayList<TableItem> outActiveVarList = curBlock.getOutActiveVarList();
                //求解活跃变量的out集合
                for (BasicBlock nextBlock : nextBlocks) {
                    for (TableItem outActiveVar : nextBlock.getInActiveVarList()) {
                        if (!outActiveVarList.contains(outActiveVar)) {
                            outActiveVarList.add(outActiveVar);
                            unFinished = true;
                        }
                    }
                }
                //求解out - def
                ArrayList<TableItem> outSubDefVarList = new ArrayList<>(outActiveVarList);
                outSubDefVarList.removeAll(curBlock.getDefVarList());
                //求解in：use 并 (out - def)
                ArrayList<TableItem> useVarList = curBlock.getUseVarList();
                for (TableItem useVar : useVarList) {
                    if (!inActiveVarList.contains(useVar)) {
                        inActiveVarList.add(useVar);
                        unFinished = true;
                    }
                }
                for (TableItem outSubDefVar : outSubDefVarList) {
                    if (!inActiveVarList.contains(outSubDefVar)) {
                        inActiveVarList.add(outSubDefVar);
                        unFinished = true;
                    }
                }
            }
        }
    }
```

#### 1.3无用DEF语句删除

由于某些变量或者常量的值在前面的优化中已经全部赋值到了相应的操作指令中，导致这些变量或常量的定义语句和对应的赋值语句失效了，对于这些语句我们可以直接删除（否则可能导致多次的无效的**内存分配和内存操作**，因此在进行完上述优化之后我又进行了一次死定义删除的优化，结果对于测试点1效果显著。

优化的主要思路是对于某个DEF定义点，如果后面的所有语句都没有用到该变量或常量的值，那么可以直接删除该DEF语句，同时删除所有对该变量进行赋值的语句。

具体的优化方法如下：

```java
public boolean deleteDeadDef(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            boolean isDefPoint = isInPointMap(defPoints, irCode);
            if (isDefPoint) {
                DefPoint defPoint = getDefPointByIRCode(defPoints, irCode);
                TableItem defItem = defPoint.getDefItem();
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                int blockEnd = curBlock.getEnd();
                boolean isDeadDef = true;
                int nextDefPointNum = -1;
                if (irCode.getOperator() == IROperator.DEF && irCode.getDefItem().getKind().equals("param")) {
                    //参数豁免
                    isDeadDef = false;
                }
                for (int j = i + 1; j <= blockEnd; j++) {
                    //寻找块内该变量的下一个定义点
                    if (getSameItemDefPoint(optimizedIRList.get(j), defPoints, defItem) != null) {
                        nextDefPointNum = j;
                        break;
                    }
                }
                if (nextDefPointNum == -1) {
                    //块内该变量没有下一个定义点，判断该定义点的值是否在该块内使用
                    isDeadDef = isDeadDef(i, defItem, blockEnd, isDeadDef);
                    //判断是否在活跃变量的out集合中
                    if (curBlock.getOutActiveVarList().contains(defItem)) {
                        isDeadDef = false;
                    }
                } else {
                    //块内该变量有下一个定义点，判断该定义点的值是否被使用
                    isDeadDef = isDeadDef(i, defItem, nextDefPointNum, isDeadDef);
                }
                if (isDeadDef) {
                    irCode.setDead();
                }
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }
```

**优化过程中出现的问题**：

由于开始对无用变量的定义是后面没有被使用过，但是对于函数的参数而言，如果该参数是数组参数的话，那么对它的赋值也会产生实际影响，除此之外，函数参数的定义还会影响到参数压栈和出栈的指令，因此对于函数参数的定义我们应该选择豁免（即在无用DEF删除的过程中，应该对**函数的参数不予考虑**）。

### 2.常量传播

利用到达定义数据流分析，当某个计算中出现的变量的**所有有效定义点的值均为同一个常数**时，可以直接将该变量用常数替换，以减少运算量。为了实现这一目标，首先需要将研究对象（一般一个函数体）切分为基本块，然后对这些基本块进行到达定义数据流分析（由于全局变量和局部数组变量存在被其他函数修改的风险，因而该分析**仅针对局部非数组变量进行**）

**数据流方程**：$out = gen \cup (in - kill)$

为了实现该优化，我设计了表示基本块的类`BasicBlock`和表示定义点的类`DefPoint`，具体的数据类型如下：

BasicBlock：

```java
private String blockName;
    private int start;
    private int end;
    private ArrayList<IRCode> blockIRCodes = new ArrayList<>();
    private ArrayList<BasicBlock> nextBlocks = new ArrayList<>();
    private ArrayList<BasicBlock> preBlocks = new ArrayList<>();
    private ArrayList<DefPoint> genPoints = new ArrayList<>();
    private ArrayList<DefPoint> killPoints = new ArrayList<>();
    private ArrayList<DefPoint> inDefPoints = new ArrayList<>();
    private ArrayList<DefPoint> outDefPoints = new ArrayList<>();

    public BasicBlock(String blockName) {
        this.blockName = blockName;
    }
```

DefPoint：

```java
private String pointName;
    private IRCode irCode;
    private TableItem defItem;
    private BasicBlock block;

    public DefPoint(String pointName, IRCode irCode, TableItem defItem, BasicBlock block) {
        this.pointName = pointName;
        this.irCode = irCode;
        this.defItem = defItem;
        this.block = block;
    }
```

常量传播优化主要分为以下几个阶段：

- 基本块划分
- gen、kill集合生成
- 迭代计算in、out集合
- 根据每个基本块的in集合和基本块内的定义点，判断某处使用的变量是否可以替换为常量

实现逻辑如下：

```java
public void constSpread() {
        boolean unFinished = true;
        while (unFinished) {
            initTable();
            TableItem funcItem = null;
            for (int i = 0; i < optimizedIRList.size(); i++) {
                IRCode irCode = optimizedIRList.get(i);
                IROperator operator = irCode.getOperator();
                if (operator == IROperator.DEF) {
                    curTable.addItem(irCode.getDefItem());
                } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                    HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                    HashMap<String, DefPoint> defPoints = new HashMap<>();//初始化定义点列表
                    funcItem = irCode.getFuncItem();
                    curTable.addItem(funcItem);
                    addLevel();
                    splitBasicBlocks(blockMap, i);//划分基本块

                    backupTable();//备份符号表
                    setDefPoints(defPoints, blockMap, i);//设置定义点，即gen集合
                    rollBackTable();//恢复符号表

                    setKillPoints(defPoints, blockMap);//设置kill集合

                    setInOutDefPoints(defPoints, blockMap);//迭代求解in、out集合

                    backupTable();//备份符号表
                    unFinished = constSpreadInBlock(defPoints, blockMap, i);//常量传播优化
                    rollBackTable();//恢复符号表

                    if (unFinished) {
                        break;
                    }
                } else if (operator == IROperator.block_begin) {
                    addLevel();
                } else if (operator == IROperator.block_end) {
                    deleteLevel();
                } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                    deleteLevel();
                }
            }
        }
    }
```

在实现过程中，我遇到的问题主要有：

- 划分基本块并且生成数据流关系的时候，没有考虑跳转到后面块的情况，解决方案是先划分好基本块，然后再完善一次数据流的关系。
- 之前设计的mips生成器没有完全适配操作数1和操作数2均为常数的情况，具体表现在函数参数和打印参数等地方，对此进行了一定的适配。
- 基本块划分的方法考虑不够细致，开始将return作为一个划分标准，但是没有考虑void函数可以没有return语句，导致该类函数确实最后一个基本块。

### 3.计算优化

在各种优化过后，可能会出现很多计算中一个操作符为常数的情况，对于这些情况，我们可以对**常数是0和1的情况**针对性处理。

例如，对于`MUL, #t0, 1, #t1`的情况，可以直接将后续的所有`#t1`替换为`#t0`进行计算，这样可以处理掉很多不必要的多余运算。

具体实现如下：

```java
public boolean optimizeCalculate() {
        boolean unFinished = true;
        for (IRCode irCode : optimizedIRList) {
            IROperator operator = irCode.getOperator();
            boolean canDelete = false;
            if (irCode.getResultIdent() != null && irCode.getResultIdent().startsWith("#")) {
                if (irCode.op1IsNum() && irCode.getOpIdent2() != null && irCode.getOpIdent2().startsWith("#")) {
                    int opNum = irCode.getOpNum1();
                    String opIdent = irCode.getOpIdent2();
                    String resultIdent = irCode.getResultIdent();
                    if (opNum == 1) {
                        canDelete = calculateSpread1(operator, opIdent, resultIdent, true);
                    } else if (opNum == 0) {
                        canDelete = calculateSpread0(operator, opIdent, resultIdent, true);
                    }
                } else if (irCode.op2IsNum() && irCode.getOpIdent1() != null && irCode.getOpIdent1().startsWith("#")) {
                    int opNum = irCode.getOpNum2();
                    String opIdent = irCode.getOpIdent1();
                    String resultIdent = irCode.getResultIdent();
                    if (opNum == 1) {
                        canDelete = calculateSpread1(operator, opIdent, resultIdent, false);
                    } else if (opNum == 0) {
                        canDelete = calculateSpread0(operator, opIdent, resultIdent, false);
                    }
                }
            }
            if (canDelete) {
                irCode.setDead();
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }
```

其中，对于常数是0和1的情况处理逻辑如下：

**常数是0**：

```java
public boolean calculateSpread0(IROperator operator, String opIdent, String resultIdent, boolean isOp1Num) {
        boolean canSubstitute = false;
        String substituteIdent = null;
        int substituteNum = 0;
        boolean substituteIsNum = false;
        boolean finish = false;
        switch (operator) {
            case ADD:
                substituteIdent = opIdent;
                canSubstitute = true;
                break;
            case SUB:
                if (!isOp1Num) {
                    //只有op2是数字才能替换
                    substituteIdent = opIdent;
                    canSubstitute = true;
                }
                break;
            case MUL, AND:
                canSubstitute = true;
                substituteIsNum = true;
                break;
            case DIV, MOD:
                if (isOp1Num) {
                    //只有被除数是0可以替换
                    substituteIsNum = true;
                    canSubstitute = true;
                }
                break;
            default:
                break;
        }
        return substituteCalculate(resultIdent, canSubstitute, substituteIdent, substituteNum, substituteIsNum);
    }
```

**常数是1**：

```java
public boolean calculateSpread1(IROperator operator, String opIdent, String resultIdent, boolean isOp1Num) {
        boolean canSubstitute = false;
        String substituteIdent = null;
        int substituteNum = 0;
        boolean substituteIsNum = false;
        boolean finish = false;
        switch (operator) {
            case MUL:
                substituteIdent = opIdent;
                canSubstitute = true;
                break;
            case DIV:
                if (!isOp1Num) {
                    //只有op2是1才能替换
                    substituteIdent = opIdent;
                    canSubstitute = true;
                }
                break;
            case MOD:
                if (!isOp1Num) {
                    //只有op2是1才能替换
                    substituteNum = 0;
                    substituteIsNum = true;
                    canSubstitute = true;
                }
                break;
            case OR:
                substituteNum = 1;
                substituteIsNum = true;
                canSubstitute = true;
                break;
            default:
                break;
        }
        return substituteCalculate(resultIdent, canSubstitute, substituteIdent, substituteNum, substituteIsNum);
    }
```

### 4.循环优化

在之前的循环实现逻辑中，每次在循环结束更新完循环变量之后，都会跳转到`for_checkin`的部分进行判断，这样无形之中增加了很多跳转的工作。实际上，我们可以采用`do-while`循环的思想，在第一次进入循环之前判断是否能进入循环，之后每次循环结束后继续判断是否需要回到循环开始的位置，而不是直接跳回循环快之前再进行判断。这样以来可以节省掉每次循环结束后的`JMP`指令，使得中间代码更加简洁。

例如：

```
bb1:
addi $1, $1, 1
beq $1, $2, bb3
j bb2
....
bb2:
...    #code1
j bb1
bb3:
....
```

可以优化为：

```
bb1:
...     #code1
addi $1, $1, 1
bne $1, $2, bb1
bb3:
...
```

优化后的循环逻辑为：

1.执行循环变量赋值语句`ForStmt1`

2.解析条件表达式`Cond`

3.生成标签`for_in`

4.生成`Stmt`的四元式

5.生成标签`for_update`

6.执行循环变量更新语句`ForStmt2`

7.生成标签`for_checkin`

8.解析条件表达式`Cond`

9.生成标签`for_out`

### 5.公共子表达式删除（DAG）

在一些基本块中，有可能会出现不同变量的值相同的情况，例如：

```
c <- a + b
d <- c - b
e <- a + b
f <- e - b
```

其中c和e的取值显然相同，可以直接优化为：

```
c <- a + b
d <- c - b
f <- c - b
```

这时又可以发现d和f的取值相同，可以继续优化为：

```
c <- a + b
d <- c - b
```

当然，在进行公共子表达式消除的过程中，由于我们改变后的结果只能保证程序在该基本块内部的正确性，无法保证跨基本快或者跨函数等情况下程序是否正确，因此我们进行公共子表达式消除的对象依然只能是**局部非数组变量**。

至于DAG图的实现，我们采用`DAGMap`类来保存每个基本块的DAG图，将**局部变量或数字**作为叶节点，运算符作为中间节点，同时运算结果也保存在中间节点当中，后续如果出现某个局部变量A的运算结果落在已有的某个中间节点（包含局部变量B）中，那么考虑两种情况。

第一种，该**局部非数组变量跨基本块不活跃**。如果该基本块中**后续没有对局部变量B重新赋值的地方**，那么直接删除对A赋值的语句，将该基本块中后续对A的读取都转换成对B的读取。如果**后续有对B重新赋值的地方**，那么本次对A的赋值变成**将B的值赋给A**，同时将下一次给B赋值之前对A的读取都转换成对B的读取（目的是暴露更多可优化的机会）。

第二种，该**局部非数组变量跨基本块活跃**。此时应该采取和上一种情况中第二种解决方案。

具体逻辑如下：

```Java
public boolean dagOptimize() {
        boolean unFinished = false;
        initTable();
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                splitBasicBlocks(blockMap, i);//划分基本块

                backupTable();//备份符号表
                setDefUseVarList(blockMap, i);//设置活跃变量Use Def集合
                rollBackTable();//恢复符号表
                setInOutActiveVarList(blockMap);//迭代求解活跃变量in、out集合

                backupTable();//备份符号表
                HashMap<String, DefPoint> defPoints = new HashMap<>();
                setDefPoints(defPoints, blockMap, i);//设置定义点，即gen集合
                rollBackTable();//恢复符号表

                ArrayList<TableItem> allGlobalVarList = new ArrayList<>();//统计函数体内所有的跨基本块活跃变量
                for (BasicBlock block : blockMap.values()) {
                    ArrayList<TableItem> inActiveVarList = block.getInActiveVarList();
                    for (TableItem item : inActiveVarList) {
                        if (!allGlobalVarList.contains(item)) {
                            allGlobalVarList.add(item);
                        }
                    }
                }

                //分析每个基本块内部的公共子表达式
                backupTable();
                unFinished = dagAnalyse(blockMap, defPoints, allGlobalVarList, i);
                rollBackTable();
                if (unFinished) {
                    break;
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            }
        }
        return unFinished;
    }
```

## 二、后端优化

### 1、指令选择

前言：为了更方便的实现更多功能，以及更加方便翻译高级语言，Mars给我们提供了充分的伪指令使用，例如 `subi $t1, $t2, 100`、`move $1, $2`、`ble $t1, $t2, label` 等，能够缩短指令的条数，增加代码的可读性。但是由于Mars的局限性，以及体系结构的特性，很多时候伪指令虽然表面上降低了指令的条数，但是实际上反而会使FinalCycle增加，在非循环语句当中，这样的问题当然可以忽略，而在循环次数很多的循环体当中，哪怕每个语句多翻译了一条代码都会严重影响性能，而subi等语句其实是十分常见的计算语句，在循环当中也可能高频出现。另一方面，在进行图着色寄存器分配时，伪指令可能会隐蔽地更改寄存器的值，导致数据流分析错误，可能会产生一些很难发现的bug。所以不使用Mars低效的伪指令，转而自己封装一套翻译机制是提升性能的有效方法。

---

#### 1.1分支指令

##### beq

beq在进行寄存器和立即数的比较时，会先把立即数加`$0`赋值给新的寄存器，而我们程序中大多数跳转又都是和0进行比较，因此可以考虑把立即数0都换成`$0`，即使用`beqz`指令。

bne,bgt,bge,blt,ble指令也都可以按照上述方法进行优化。

#### subi

subi指令在翻译时会被翻译成一条和`$0`相加的addi指令和一条sub指令，但其实我们只需要翻译为一条addi指令就可以满足条件。

例如：

```
subi $t1, $t0, -1
```

可以翻译为：

```
addi $t1, $t0, 1
```

#### lw、sw

lw、sw指令在操作一个标识符时，会首先计算标识符和立即数的和，再加上$0的值，因此如果立即数为0时，我们可以直接省略该立即数，减少目标代码量。

### 2.乘法优化

乘法优化主要分为两部分：

- 2的整数幂优化
- 2的整数幂周围的数

优化方法：

```java
public void optimizeMulRegImm(String opReg, int opNum, String desReg) {
        if (!optimize) {
            String immReg = getReg(String.valueOf(opNum));
            addMIPSCode(new MIPSCode(MIPSOperator.li, opNum, null, immReg));//常数opNum赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.mult, immReg, opReg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(immReg);//释放常数寄存器
        } else {
            boolean isNegate = opNum < 0;
            if (isNegate) {
                opNum = -opNum;//后面都考虑非负整数
            }
            if (opNum == 0) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            } else if (opNum == 1) {
                addMIPSCode(new MIPSCode(MIPSOperator.move, opReg, null, desReg));
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            } else if (isPowerOfTwo(opNum)) {
                int shift = (int) (Math.log(opNum) / Math.log(2));
                addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg, shift, desReg));
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            } else if (nearPowerOfTwo(opNum) != 0) {
                int off = nearPowerOfTwo(opNum);
                int shift = (int) (Math.log(opNum + off) / Math.log(2));
                addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg, shift, desReg));
                for (int i = 0; i < Math.abs(off); i++) {
                    addMIPSCode(new MIPSCode(off > 0 ? MIPSOperator.sub : MIPSOperator.addu, desReg, opReg, desReg));
                }
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            } else {
                if (isNegate) {
                    opNum = -opNum;//恢复操作数
                }
                String immReg = getReg(String.valueOf(opNum));
                addMIPSCode(new MIPSCode(MIPSOperator.li, opNum, null, immReg));//常数opNum赋值给寄存器
                addMIPSCode(new MIPSCode(MIPSOperator.mult, immReg, opReg, null));
                addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
                freeReg(immReg);//释放常数寄存器
            }
        }
    }
```

### 3.除法优化

除法优化的思路为将除法转换为乘法和移位操作。

优化方法：

```java
public void optimizeDiv(String opReg, int divisor, String desReg) {
        if (!optimize) {
            String immReg = getReg(String.valueOf(divisor));
            addMIPSCode(new MIPSCode(MIPSOperator.li, divisor, null, immReg));//常数opNum赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, opReg, immReg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(immReg);//释放常数寄存器
        } else {
            boolean isNegate = divisor < 0;
            if (isNegate) {
                divisor = -divisor;//后面都考虑非负整数
            }
            if (divisor == 1) {
                addMIPSCode(new MIPSCode(MIPSOperator.move, opReg, null, desReg));
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            } else if (isPowerOfTwo(divisor)) {
                int shift = (int) (Math.log(divisor) / Math.log(2));
                String tempReg = getReg("temp");
                addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg, 32 - shift, desReg));
                addMIPSCode(new MIPSCode(MIPSOperator.sltu, "$0", desReg, desReg));
                addMIPSCode(new MIPSCode(MIPSOperator.slt, opReg, "$0", tempReg));//被除数为负数置1
                addMIPSCode(new MIPSCode(MIPSOperator.and, tempReg, desReg, tempReg));
                addMIPSCode(new MIPSCode(MIPSOperator.sra, opReg, shift, desReg));
                addMIPSCode(new MIPSCode(MIPSOperator.add, desReg, tempReg, desReg));
                freeReg(tempReg);
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            } else {
                long multiplier = chooseMultiplier(divisor, 32);
                if (multiplier != -1 && multiplier < Math.pow(2, 32) + Math.pow(2, 31)) {//能找到multiplier
                    if (multiplier < Math.pow(2, 31)) {//multiplier不会溢出，且在int范围内
                        int l = (int) Math.floor(Math.log(multiplier * divisor) / Math.log(2)) - 32;
                        String multiplierReg = getReg(String.valueOf(multiplier));
                        addMIPSCode(new MIPSCode(MIPSOperator.li, (int) multiplier, null, multiplierReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.mult, opReg, multiplierReg, null));
                        addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.sra, desReg, l, desReg));

                        addMIPSCode(new MIPSCode(MIPSOperator.slt, opReg, "$0", multiplierReg));//被除数为负数置1
                        addMIPSCode(new MIPSCode(MIPSOperator.add, desReg, multiplierReg, desReg));
                        freeReg(multiplierReg);//释放常数寄存器
                    } else {
                        int l = (int) Math.floor(Math.log(multiplier * divisor) / Math.log(2)) - 32;
                        String multiplierReg = getReg(String.valueOf(multiplier));
                        addMIPSCode(new MIPSCode(MIPSOperator.li, (int) (multiplier - Math.pow(2, 32)), null, multiplierReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.mult, opReg, multiplierReg, null));
                        addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.add, desReg, opReg, desReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.sra, desReg, l, desReg));

                        addMIPSCode(new MIPSCode(MIPSOperator.slt, opReg, "$0", multiplierReg));//被除数为负数置1
                        addMIPSCode(new MIPSCode(MIPSOperator.add, desReg, multiplierReg, desReg));
                        freeReg(multiplierReg);//释放常数寄存器
                    }
                } else {//无法进行优化
                    String immReg = getReg(String.valueOf(divisor));
                    addMIPSCode(new MIPSCode(MIPSOperator.li, divisor, null, immReg));//常数opNum赋值给寄存器
                    addMIPSCode(new MIPSCode(MIPSOperator.div, opReg, immReg, null));
                    addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
                    freeReg(immReg);//释放常数寄存器
                }
                if (isNegate) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sub, "$0", desReg, desReg));
                }
            }
        }
    }
```

### 4.取模优化

将取模运算优化成 a - a / b * b

```java
public void optimizeMod(String opReg, int opNum, String desReg) {//取模运算优化为a - a / b * b，从而运用前面除法的优化方法
        if (!optimize) {
            String immReg = getReg(String.valueOf(opNum));
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, immReg));//常数赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, opReg, immReg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
            freeReg(immReg);//释放常数寄存器
        } else {
            boolean isNegate = opNum < 0;
            if (isNegate) {
                opNum = -opNum;//后面都考虑非负整数,mod符号与除数无关
            }
            if (opNum == 1) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            } else {
                optimizeDiv(opReg, opNum, desReg);
                String immReg = getReg(String.valueOf(opNum));
                addMIPSCode(new MIPSCode(MIPSOperator.li, opNum, null, immReg));
                addMIPSCode(new MIPSCode(MIPSOperator.mult, desReg, immReg, null));
                addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
                addMIPSCode(new MIPSCode(MIPSOperator.sub, opReg, desReg, desReg));
                freeReg(immReg);//释放常数寄存器
            }

        }
    }
```

### 5.全局寄存器分配

由于在之前生成目标代码的过程中，我们对所有非临时变量的操作都通过访存进行，没有考虑全局寄存器的分配问题，导致了大量多余的访存操作。现在，我计划使用图着色的方法为所有**局部非数组变量**进行全局寄存器`$s0-$s7`的分配，分配完成后，在该函数体内，**该变量与该寄存器捆绑**，所有对于该变量的赋值操作（ASSIGN，GETINT）和读取该变量的操作（ASSIGN）都将转换为对该寄存器进行相应操作。**变量开始与全局寄存器捆绑的时机是进入函数体时，释放全局寄存器的时机是退出函数体时**。

为了实现该优化，我们需要在中间代码优化的阶段维护一个**全局寄存器池**，并且在变量的`TableItem`中添加全局寄存器属性。之后，根据活跃变量的数据流分析生成冲突图，然后再按照图着色算法从全局寄存器中给全局变量分配寄存器，分配完成后，将分配结果存入对应的`TableItem`中。

在后端生成MIPS代码时，如果对某一**局部非数组变量**进行赋值或者读取，那么可以将对应的操作施加在它对应的全局寄存器上。

**需要注意的是，使用全局寄存器后，在函数调用时需要额外对全局寄存器的值进行维护**，维护的方法与临时寄存器不同，而是将寄存器中的值存入到相应变量在栈中的位置，调用完成以后再把它们读取回相应全局寄存器当中。

**对后端的修改主要有**：

- ASSIGN给临时变量赋值时，如果赋值操作符拥有全局寄存器，则使用该寄存器进行赋值
- ASSIGN给变量赋值时，如果被赋值操作符拥有全局寄存器，则将值赋给该寄存器
- GETINT给变量赋值时，如果被赋值操作符拥有全局寄存器，则将值赋给该寄存器
- 被调用函数给参数赋值时，如果被赋值参数拥有全局寄存器，则将值赋给该寄存器
- 调用函数时，对全局寄存器进行保护，将寄存器的值写入栈中变量所在位置；与此对应，函数调用结束后，将寄存器的值从栈中取出。
- 一个函数体结束时，释放全局寄存器堆

中端分配全局寄存器的实现：

```java
public void optimizeGlobalReg() {
        initTable();
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                splitBasicBlocks(blockMap, i);//划分基本块

                backupTable();//备份符号表
                setDefUseVarList(blockMap, i);//设置活跃变量Use Def集合
                rollBackTable();//恢复符号表
                setInOutActiveVarList(blockMap);//迭代求解活跃变量in、out集合

                //构造冲突图
                ConflictGraph conflictGraph = new ConflictGraph();
                for (BasicBlock block : blockMap.values()) {
                    ArrayList<TableItem> inActiveVarList = block.getInActiveVarList();
                    conflictGraph.addConflict(inActiveVarList);
                }
                //为变量分配寄存器
                conflictGraph.initAllocateStack(8);
                TableItem node = conflictGraph.getOneNode();
                while (node != null) {
                    boolean success = allocateGlobalReg(node);
                    if (!success) {
                        break;
                    } /*else {
                        System.out.println("分配全局寄存器成功：" + irCode.print() + " " + node.getName() + " " + node.getGlobalRegName());
                    }*/
                    node = conflictGraph.getOneNode();
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
                freeGlobalReg();
            }
        }
    }
```

后端进行寄存器池维护的具体实现：

```java
public void allocateGlobalReg(String name, MIPSTableItem item) {
        for (Register register : globalRegisters) {
            if (register.isAvailable() && register.getName().equals(name)) {
                register.setGlobalBusy(item);
                return;
            }
        }
    }

    public void preventGlobalReg() {
        for (Register register : globalRegisters) {
            if (!register.isAvailable()) {
                //全局寄存器被分配，进行保护
                addMIPSCode(new MIPSCode(MIPSOperator.sw, register.getName(), null, -register.getGlobalItem().getOffset() - 4 + "($fp)"));
            }
        }
    }

    public void recoverGlobalReg() {
        for (Register register : globalRegisters) {
            if (!register.isAvailable()) {
                //全局寄存器被分配，进行恢复
                addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -register.getGlobalItem().getOffset() - 4, register.getName()));
            }
        }
    }

    public void freeGlobalReg() {
        for (Register register : globalRegisters) {
            register.setAvailable();
        }
    }
```

**对于传参时全局寄存器的保护和恢复问题的针对性优化**：

一般情况下，程序的大部分函数调用都发生在main函数当中，而main函数使用的全局寄存器一般而言也比较多，因此在多次调用其他函数的过程中会因为全局寄存器的保护和恢复产生很大的开销（也许是竞速中一个点在进行全局寄存器分配后性能反而下降的原因）。为了解决这一问题，我们可以定义一个**函数全局寄存器的闭包**，即该函数即其调用函数所有用到的全局寄存器的集合，这时当我们在一个函数中调用其他函数时，需要保护和恢复的全局寄存器的值仅仅是**当前活跃的全局寄存器集合与被调函数全局寄存器闭包集合的交集**。除此之外，为了尽量减少不同函数用到的全局寄存器交集中寄存器的数目，我们在分配全局寄存器时也进行一定的优化，**每次分配的寄存器为目前使用次数最少的全局寄存器**，这样能够让不同函数用到的全局寄存器尽量错开，减少了维护的代价。

### 6.局部窥孔

在完成寄存器分配之后，容易发现生成的MIPS代码中出现了大量冗余的操作，例如在将一个全局寄存器的值`move`到一个临时寄存器当中后，马上又使用该临时寄存器进行计算，这时我们完全可以将两条指令合并，**将后面对该临时寄存器再次赋值之前所有用到该临时寄存器的地方都替换为对应的全局寄存器**。

比如对于下面这段代码：

```asm
# printf
	# $s0 is flag
	move $t0, $s0
	addi $sp, $sp, -4
	sw $t0, ($sp)
```

可以直接优化为：

```asm
# printf
	# $s0 is flag
	addi $sp, $sp, -4
	sw $s0, ($sp)
```

除此之外，一些比较赋值语句和跳转语句也可以进行优化合并，例如：

```asm
sle $t1, $t2, $t3
bnez $t1, label
```

可以优化为：

```asm
ble $t2, $t3,label
```

再例如，有些寄存器被赋值之后马上又赋值给了其他寄存器，那么我们可以直接将两条语句进行合并，省去第二次赋值的操作。如：

```asm
lw $t0, 0($sp)
move $a0, $t0
```

可以优化为：

```asm
lw $a0, 0($sp)
```

还有一些因为之前的优化产生的寄存器将自己的值赋给自己的情况，对于这种语句显然也需要删除。

如：

```asm
move $a0, $a0
```

这一句直接可以删除。

再比如连续多次对同一内存地址进行读写，并且中间没有读取该内存地址的值，那么仅有最后一次的写操作是有效的，前面的写操作都可以被优化掉。

例如：

```asm
sw $t0, -4($sp)
...#没有修改$sp的值，也没有读取该地址中的值
sw $t1, -4($sp)
```

可以优化为：

```asm
...#没有修改$sp的值，也没有读取该地址中的值
sw $t1, -4($sp)
```

### 7.临时寄存器分配

在之前的寄存器分配方案中，我们只考虑了对跨越多个基本块的全局变量的寄存器分配方案，但是实际情况中也有许多变量**仅在一个基本块中起作用**，对于这部分变量，我们可以使用和全局寄存器分配类似的逻辑为它们分配目前仍处于空闲状态的`$t5~$t9`寄存器，并且在后端生成代码的过程中将它们和响应的寄存器捆绑发生作用。

中端进行寄存器和**局部非数组变量**的捆绑：考虑的空间为基本块内部，临时寄存器池释放的时机为**退出基本块**时。分配方案采用最简单的**先来先服务**原则（此处用图着色有点杀鸡用牛刀的感觉）。

**后端需要进行的适配**：和适配全局寄存器分配时的操作基本一致，不同点在于现在临时寄存器池释放的时机位于基本块的结尾，而在后端原先没有引入基本块的概念，为此我们需要**将ircode作为桥梁**，基本块开始和结束的ircode加上相应的标识，方便后端进行判断和使用。

中端确定基本块和IR的对应关系逻辑如下：

```java
public void bindBlockAndIr() {
        HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                splitBasicBlocks(blockMap, i);//划分基本块
                //确定基本块和IR的对应关系
                for (BasicBlock block : blockMap.values()) {
                    for (int j = block.getStart(); j <= block.getEnd(); j++) {
                        optimizedIRList.get(j).setBasicBlock(block);
                        if (j == block.getStart()) {
                            optimizedIRList.get(j).setBasicBlockBegin();
                        }
                        if (j == block.getEnd()) {
                            optimizedIRList.get(j).setBasicBlockEnd();
                        }
                    }
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            }
        }
    }
```

对临时寄存器的保护和恢复机制和全局寄存器的基本一致，仍然采用求各个函数临时寄存器闭包的策略。

但是发现进行临时寄存器优化后出现了负优化的现象，应该是和局部窥孔的优化发生了冲突，因此放弃该优化，选择回滚。

### 8.指令顺序调整

优化之前在进行参数和临时寄存器入栈等操作的时候，都是入栈一个数据就调整栈顶指针`$sp`，这样就产生了很多对`$sp`指针的计算操作，而这些操作其实很多都可以合并到一起。比如对于临时寄存器，可以等它们全部入栈之后一次性调整栈顶指针。同时，这样的操作还能带来新的**窥孔优化**机会，比如对于如下代码：

```asm
	sw $v0, -4($sp)
	addi $sp, $sp, -4
	addi $sp, $sp, -4
	sw $a0, ($sp)
```

容易看到在临时寄存器入栈完成，参数入栈开始的位置有两条连续对`$sp`寄存器做减法的指令，而这两条指令实际可以进行合并，并且这样的结构并不在少数。
