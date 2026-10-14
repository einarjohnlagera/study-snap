## Overview

A C++ program you write is plain text. A computer cannot run that text directly, so it must first be translated into a form the machine can execute. This note follows the path from a C++ source file to a running program, using standard C++17 and a command-line compiler such as `g++` or `clang++`. Understanding this path helps you read error messages, avoid common mistakes and know what each step is doing for you. Integrated development environments (IDEs) automate these steps with buttons, but they perform the same steps underneath.

## From source file to running program

C++ is a **compiled** language. The text you write is called the **source code**, usually stored in a file ending in `.cpp`. A tool called the **compiler** translates it into machine code, producing an **executable** file that the operating system can run.

The translation happens in a few stages, which the compiler tool normally performs in one command:

1. **Preprocessing.** Lines beginning with `#`, such as `#include <iostream>`, are handled first. An `#include` inserts the declarations of a library into your file so your code can use it.
2. **Compiling.** Your C++ is checked against the language rules and translated into machine code for each source file. Syntax and type errors are reported here.
3. **Linking.** The compiled code is combined with the library code it uses (for example the input/output library) to form one executable.

After these stages succeed, you **run** the executable. Compiling and running are separate actions: compiling creates the program; running executes it.

## A first program

Save this as `hello.cpp`:

```cpp
#include <iostream>

int main() {
    std::cout << "Hello, world!\n";
    return 0;
}
```

What each part does:

- `#include <iostream>` makes the standard input/output facilities available.
- `int main()` defines the function where execution begins. Every C++ program has exactly one `main`.
- `std::cout << ...` sends text to standard output. `std::` shows that `cout` belongs to the standard library.
- `return 0;` ends `main` and reports success to the operating system. (In `main`, falling off the end also returns 0, but writing it is clear.)

## Compiling and running from the command line

On a Unix-like system (Linux, macOS) with `g++`:

```
g++ -std=c++17 -Wall -Wextra hello.cpp -o hello
./hello
```

The first command compiles and links `hello.cpp` and writes an executable named `hello`. The second runs it from the current directory and prints `Hello, world!`.

The options mean:

| Option | Meaning |
|---|---|
| `-std=c++17` | Use the C++17 version of the language. Compilers have a default version, which may differ, so state it explicitly. |
| `-Wall -Wextra` | Turn on a broad set of warnings that point out suspicious code. |
| `-o hello` | Name the output executable `hello`. Without it, many compilers choose a default name such as `a.out`. |

On Windows, the executable is typically named with `.exe` (for example `hello.exe`) and is run as `hello.exe` or `.\hello.exe`, depending on the shell. The ideas are identical.

## The edit–compile–run cycle

Programming is rarely one pass. You repeat a cycle:

1. **Edit** the source file and save it.
2. **Compile** it.
3. **Run** the result and check the behaviour.
4. If something is wrong, go back to step 1.

A key point: after you change the source, the old executable does not change. You must compile again, or you will be running the old program. Forgetting this is one of the most common beginner confusions.

## A program that uses input

```cpp
#include <iostream>

int main() {
    int a = 0;
    int b = 0;

    std::cout << "Enter two integers: ";
    std::cin >> a >> b;

    std::cout << "Sum: " << (a + b) << '\n';
    return 0;
}
```

Compile it the same way, run it, and type `3 4` followed by Enter. It prints `Sum: 7`. The program was compiled once; it can be run any number of times with different input without recompiling.

## Three kinds of problems

It helps to know *when* a problem appears, because it tells you where to look.

| Kind | When it appears | Example |
|---|---|---|
| **Compile-time error** | While compiling; no executable is produced | A missing semicolon, an undeclared name, a type mismatch |
| **Warning** | While compiling; an executable *is* produced | A variable that is declared but never used |
| **Runtime or logic problem** | After the program starts | Wrong output, or a crash on certain input |

### Reading compiler messages

Compiler messages usually name the file, the line number and a description, for example of the general form:

```
hello.cpp:5:35: error: expected ';' before 'return'
```

Practical habits:

- **Fix the first error first.** One mistake can cause many later messages, which often disappear when the first is fixed.
- **Look at the reported line and the line before it.** A missing semicolon is often reported at the start of the *next* statement.
- **Treat warnings seriously.** A warning such as `unused variable 'x'` often marks a real mistake. With `-Wall -Wextra`, many logic slips are caught at compile time.

## Common mistakes

- **Running the source file** instead of the executable, or trying to run the program before compiling it.
- **Not recompiling** after editing, and so running the old executable.
- **Forgetting `-std=c++17`**, then seeing errors for features the compiler's default version does not support.
- **Misspelling or omitting a header**, such as using `std::cout` without `#include <iostream>`.
- **Giving the output the same name as the source file**, which can overwrite the source and lose your work.
- **Ignoring warnings** until a real bug appears.
- **Running from the wrong folder**, so the shell cannot find the executable.

## Checking that a program succeeded

When a program finishes, it returns an **exit status** to the operating system. By convention 0 means success and a non-zero value signals a problem. On Unix-like shells you can see the last status with `echo $?`. This is how scripts and tools know whether a program worked, and it is why `main` returns an `int`.

## Larger programs

Everything above uses one source file. Larger programs are split across many files and built with additional tools that automate compiling and linking. The underlying steps are the same: each source file is compiled, and the results are linked into one executable.

## Key points to remember

- C++ source code is translated by a compiler into an executable; compiling and running are separate steps.
- The translation includes preprocessing, compiling and linking, usually performed by one command.
- A typical command is `g++ -std=c++17 -Wall -Wextra file.cpp -o program`, followed by `./program`.
- Editing the source does nothing to the existing executable: recompile after every change.
- Compile-time errors stop the build, warnings do not but should be heeded, and runtime problems appear only when the program runs.
- Read compiler messages from the first error, and use the reported line number as a starting point.
