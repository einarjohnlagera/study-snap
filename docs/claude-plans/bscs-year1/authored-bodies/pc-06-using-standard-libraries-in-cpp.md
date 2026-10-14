## Overview

Much of a C++ program's power comes from code you did not write: the **standard library**, a collection of ready-made types and functions that every conforming compiler provides. Knowing how to find, include and use a library correctly is a core skill, and so is knowing when not to reuse it. This note explains what a library is, how headers and the `std::` namespace work, how to read what a library function promises, and shows a small number of commonly used pieces in C++17. It is **not** a tour of the standard library: the aim is the habit of using a library correctly, illustrated with a few examples.

## What a library is

A **library** is a collection of already-written, already-tested code that your program can call. The C++ standard library provides, among other things:

- input and output (`<iostream>`, `<fstream>`);
- text (`<string>`);
- containers such as `std::vector` (`<vector>`);
- common algorithms such as sorting and searching (`<algorithm>`);
- mathematical functions (`<cmath>`);
- assertions (`<cassert>`).

Using a library saves effort, and the library code has usually been tested and tuned far more than a quick hand-written version would be.

## Headers and `#include`

Your source file must be told which parts of the library it uses. A **header** declares what a library provides, and `#include` brings those declarations into your file during preprocessing.

```cpp
#include <iostream>   // std::cout, std::cin
#include <string>     // std::string
#include <cmath>      // std::sqrt, std::pow
```

Angle brackets mean "a system or standard header". If you use something from the library and forget its header, compilation fails with an error about an undeclared name, even though the name is spelled correctly.

## The `std::` namespace

Everything in the standard library lives in the namespace `std`. A **namespace** is a named scope that keeps names from colliding. You refer to library names with the prefix `std::`:

```cpp
std::string name = "Ada";
std::cout << name << '\n';
```

You may see `using namespace std;`, which lets you drop the prefix. It is convenient in tiny programs but it brings every library name into scope at once, which can cause clashes in larger code, and it must **never be placed in a header file**. Writing `std::` explicitly is the safe habit.

## Using a library function

```cpp
#include <cmath>
#include <iostream>

int main() {
    double side = 7.0;
    double diagonal = std::sqrt(side * side + side * side);
    std::cout << diagonal << '\n';          // about 9.89949
    std::cout << std::pow(2.0, 10.0) << '\n';   // 1024
    return 0;
}
```

### Reading what a function promises

Before using a library function, find out three things from its documentation:

1. **Its signature**: the name, the parameter types and the return type, for example `double sqrt(double x)`.
2. **Its preconditions**: what must be true of the arguments. `std::sqrt` of a negative number does not give a real result; the function documents what happens instead.
3. **What it returns or changes**: a new value, or a modification of an argument.

A widely used reference for the standard library is *cppreference.com*. Reading the signature and the stated behaviour takes a minute and prevents a large class of mistakes.

## Three small, common examples

### Searching and sorting a vector

Hand-written searching and sorting are valuable for learning. In real programs the library versions are preferable.

```cpp
#include <algorithm>
#include <iostream>
#include <vector>

int main() {
    std::vector<int> values = {42, 7, 19, 3, 25};

    std::sort(values.begin(), values.end());          // sorts in ascending order
    for (int v : values) {
        std::cout << v << ' ';                        // 3 7 19 25 42
    }
    std::cout << '\n';

    if (std::find(values.begin(), values.end(), 19) != values.end()) {   // search for 19
        std::cout << "found 19\n";
    }
    return 0;
}
```

`begin()` and `end()` mark the start of the sequence and the position just past its last element. `std::find` returns `end()` when the value is not present, so comparing its result with `end()` is how "not found" is detected.

### Working with text

```cpp
#include <iostream>
#include <string>

int main() {
    std::string sentence = "learning C++ step by step";

    std::cout << sentence.size() << '\n';              // 25
    std::size_t where = sentence.find("C++");
    if (where != std::string::npos) {                  // npos means "not found"
        std::cout << "C++ starts at index " << where << '\n';   // index 9
    }
    std::string shout = sentence.substr(0, 8);          // "learning"
    std::cout << shout << '\n';
    return 0;
}
```

### Checking an assumption

```cpp
#include <cassert>

int main() {
    int count = 3;
    assert(count > 0);        // stops the program with a message if this is false
    return 0;
}
```

## When to use a library and when to write your own

| Situation | Better choice |
|---|---|
| A standard facility already does the job (sorting, searching, strings, containers) | Use the library |
| You are learning how an algorithm works | Write it yourself once, then compare with the library |
| The library function's behaviour is not what the problem needs | Write your own, and test it |
| You are unsure what the library function guarantees | Read its documentation before relying on it |

Reusing tested code is good practice, but using it blindly is not: a library function does exactly what it documents, not what you assumed.

## Common mistakes

- **Forgetting the header.** The error is about an unknown name, not a missing file.
- **Forgetting `std::`** (or relying on `using namespace std;` and meeting a name clash later).
- **Using a function without reading its preconditions**, such as an invalid argument or an empty sequence.
- **Misreading a "not found" result.** `std::find` returns `end()`, and `std::string::find` returns `npos`; neither is an index you can use.
- **Rewriting a library facility from scratch in production code** and introducing bugs the library already avoids.

## Common misconceptions

- **"The library is part of the language, so no `#include` is needed."** Standard names come from headers you must include.
- **"Library code cannot be wrong."** It is well tested but only promises what it documents; calling it incorrectly is still a bug.
- **"`using namespace std;` is required."** Writing `std::` is always valid and safer.
- **"Using the library means I do not need to understand the algorithm."** Understanding what it does, and what it costs, is still your job.

## Key points to remember

- The standard library provides tested types and functions for input and output, text, containers, algorithms, mathematics and assertions.
- A header declares what a library provides; `#include` brings it into your file.
- Library names live in `std`; write `std::` explicitly and never put `using namespace std;` in a header.
- Before using a function, read its signature, preconditions and what it returns or changes.
- `std::sort` and `std::find` illustrate library use, and "not found" is signalled by `end()` or `npos`, not by an index.
- Prefer the library for standard tasks; write your own to learn, or when the library does not fit.
