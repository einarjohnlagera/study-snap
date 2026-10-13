## Overview

As programs grow, keeping everything in one source file becomes unmanageable. C++ lets you split a program into **modules**: a group of related functions kept in their own files so that they can be written, tested and reused independently. In C++17, a module is organized as a **header file** (the promise) and a **source file** (the implementation). This note explains how to split code that way, how the pieces are compiled and joined, the errors that appear when something is missing, and the habits that keep modules reusable. It uses plain command-line compilation with several source files, and does not cover build tools.

## Why split a program

- **Reuse.** A function written once can be used by many programs.
- **Readability.** Each file has one purpose and a manageable size.
- **Testing.** A module can be tested on its own (testing is covered separately).
- **Teamwork and compile time.** Different people can work on different files, and changing one file does not require rewriting the others.

## Declaration versus definition

Two kinds of statement are involved:

- A **declaration** tells the compiler that something exists and what its type is, without giving the body: `int gcd(int a, int b);`
- A **definition** provides the actual body: `int gcd(int a, int b) { ... }`

Other files that *use* `gcd` need only the declaration. The definition lives in one place.

## The header and source file pair

Take a small module of integer utilities.

**`mathutils.h`** (the header: what the module offers)

```cpp
#ifndef MATHUTILS_H
#define MATHUTILS_H

// Returns the greatest common divisor of two positive integers.
int gcd(int a, int b);

// Returns true if n is a prime number (n >= 2), otherwise false.
bool isPrime(int n);

#endif
```

**`mathutils.cpp`** (the source: how it is done)

```cpp
#include "mathutils.h"

int gcd(int a, int b) {
    while (b != 0) {
        int remainder = a % b;
        a = b;
        b = remainder;
    }
    return a;
}

bool isPrime(int n) {
    if (n < 2) {
        return false;
    }
    for (int divisor = 2; divisor * divisor <= n; divisor = divisor + 1) {
        if (n % divisor == 0) {
            return false;
        }
    }
    return true;
}
```

**`main.cpp`** (a program that uses the module)

```cpp
#include <iostream>
#include "mathutils.h"

int main() {
    std::cout << gcd(48, 18) << '\n';        // 6
    std::cout << isPrime(17) << '\n';        // 1
    std::cout << isPrime(18) << '\n';        // 0
    return 0;
}
```

Points to notice:

- Your own headers are included with **double quotes** (`"mathutils.h"`); standard headers use angle brackets.
- The source file includes its own header, so the compiler can check that the definitions match the declarations.
- `main.cpp` includes only the header. It does not need to see how `gcd` works.

## Include guards

The header begins with `#ifndef MATHUTILS_H` / `#define MATHUTILS_H` and ends with `#endif`. These are **include guards**. If a header is included twice in the same file, perhaps indirectly through another header, its declarations would otherwise be seen twice, which can be an error. The guard makes every inclusion after the first do nothing. Give each header a distinct guard name, conventionally derived from the file name.

## Compiling several files

Each source file is compiled separately, and the results are **linked** into one executable. With one command:

```
g++ -std=c++17 -Wall -Wextra main.cpp mathutils.cpp -o app
./app
```

The compiler compiles `main.cpp` and `mathutils.cpp` on their own, then the linker joins them, resolving the call to `gcd` in `main.cpp` to its definition in `mathutils.cpp`. Only `.cpp` files are listed; headers are pulled in by `#include`.

## Errors that point to a module problem

| Message of the general form | Cause |
|---|---|
| `undefined reference to 'gcd(int, int)'` (from the linker) | The declaration was found but no definition was linked: the `.cpp` file was left off the command, or the function was declared but never defined. |
| `multiple definition of ...` (from the linker) | The same function is defined in more than one compiled file, often because a function body was put in a header that two files include. |
| `'gcd' was not declared in this scope` (from the compiler) | The header was not included, or the name is misspelled. |
| `redefinition of ...` | A header without include guards was included twice. |

Notice that *undefined reference* comes from the **linker**, not the compiler, so it appears only after every file has compiled successfully.

## Habits that keep a module reusable

1. **Put declarations in the header and definitions in the source file.** Function bodies in a header cause multiple-definition errors when more than one file includes it.
2. **Keep the header small and self-explanatory**, with a comment for each function stating what it does, its inputs and what it returns.
3. **Do not write `using namespace std;` in a header.** Every file that includes it would silently inherit it. Use `std::` in headers.
4. **Group related functions** (one purpose per module) and give the module a clear name.
5. **Hide what callers do not need.** Anything used only inside the source file does not belong in the header.
6. **Do not rely on the order of the files** on the compile command for correctness.

## A note on larger projects

Real projects contain many files and use additional tools that decide what to recompile when a file changes. Those tools automate the same two steps shown here, compiling each source file and linking the results, so understanding them by hand first makes the tools easy to follow.

## Common misconceptions

- **"A header contains the code of the module."** It contains declarations; the code is in the source file.
- **"Including a header links the library."** It only makes the declarations visible; linking is a separate step that needs the definition.
- **"Include guards are optional decoration."** Without them a header included twice can cause errors.
- **"An undefined-reference error means a typo."** It usually means a source file was not compiled or linked, or a declared function has no definition.
- **"Each `.cpp` file is a separate program."** They are separately compiled parts of one program.

## Key points to remember

- A module is a header (declarations) plus a source file (definitions) for a group of related functions.
- Include your own headers with double quotes; guard every header with `#ifndef`, `#define`, `#endif`.
- Compile every source file and link them together, for example `g++ -std=c++17 -Wall -Wextra main.cpp mathutils.cpp -o app`.
- `undefined reference` is a linker error: a definition is missing from what was linked; `multiple definition` means a definition appears in more than one place.
- Keep definitions out of headers, and never use `using namespace std;` in a header.
