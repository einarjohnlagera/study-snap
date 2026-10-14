## Overview

Standard C++17 trusts the programmer with memory. When a program breaks the rules for using memory, the language does not promise an error message or a crash. It promises nothing at all: the behaviour is **undefined**. This note describes the most common memory errors, how each one arises from the memory model, how they show up, how to detect them with tools you already have, and how modern C++ avoids most of them.

The goal is to recognize these mistakes, not to fear pointers. Almost every error below disappears when ordinary values and library types such as `std::vector` and `std::string` are used instead of raw pointers and manual allocation.

> The programs in this note that contain errors are marked `ERROR EXAMPLE`. They are for study and should be run only under the checking tools described below.

## Undefined behaviour

**Undefined behaviour** means the C++ standard places no requirements on what the program does. Possible outcomes include a crash, a wrong answer, an apparently correct answer, or a problem that appears only later or only on another machine. This is what makes memory errors difficult: a program can seem to work while being wrong, and a change that looks unrelated can make it fail.

## The common errors

### 1. Using an uninitialized pointer

A pointer that has not been given a value holds an arbitrary address.

```cpp
// ERROR EXAMPLE
int main() {
    int* p;          // never initialized: points somewhere unknown
    *p = 5;          // writes to an arbitrary address
    return 0;
}
```

**Prevention:** always initialize a pointer, with a valid address or with `nullptr`.

### 2. Dereferencing a null pointer

`nullptr` means "points to nothing", so reading or writing through it is invalid.

```cpp
// ERROR EXAMPLE
int main() {
    int* p = nullptr;
    *p = 5;          // there is no object to write to
    return 0;
}
```

The program usually crashes, but the language does not guarantee that. **Prevention:** check a pointer that might be null before using it (`if (p != nullptr)`), or use a reference where "no object" is not a valid state.

### 3. Dangling pointers and references

A pointer or reference **dangles** when the object it refers to no longer exists.

```cpp
// ERROR EXAMPLE
int* makeValue() {
    int local = 42;
    return &local;           // local ends when the function returns
}

int main() {
    int* p = makeValue();
    return *p;               // reads memory that no longer holds local
}
```

A second common form is using memory after it has been released:

```cpp
// ERROR EXAMPLE
int main() {
    int* p = new int(7);
    delete p;
    return *p;               // use after delete
}
```

**Prevention:** make sure an object outlives every pointer and reference to it; never return the address of a local variable; after `delete`, set the pointer to `nullptr`; better, avoid raw ownership altogether.

### 4. Memory leaks

A **leak** is heap memory that is never released and can no longer be reached.

```cpp
// ERROR EXAMPLE
void leaky() {
    int* data = new int[1000];   // allocated
    data[0] = 1;
}                                // data goes out of scope; the memory is never released

int main() {
    for (int i = 0; i < 3; i = i + 1) {
        leaky();                 // each call leaks 1000 ints
    }
    return 0;
}
```

The program may run correctly but uses ever more memory, which matters for long-running programs. **Prevention:** pair every `new[]` with `delete[]`, or use `std::vector`, which releases its memory itself.

### 5. Releasing memory twice

```cpp
// ERROR EXAMPLE
int main() {
    int* p = new int(1);
    delete p;
    delete p;                // second release of the same memory
    return 0;
}
```

**Prevention:** one clear owner per allocation, and set the pointer to `nullptr` after releasing it (deleting a null pointer is safe).

### 6. Mismatching the allocation and release forms

`new` pairs with `delete`; `new[]` pairs with `delete[]`. Using the wrong one is undefined behaviour.

```cpp
// ERROR EXAMPLE
int main() {
    int* values = new int[10];
    delete values;           // should be delete[] values
    return 0;
}
```

### 7. Going past the end of an array

Raw arrays and `operator[]` on a vector do **not** check the index.

```cpp
// ERROR EXAMPLE
int main() {
    int values[3] = {10, 20, 30};
    values[3] = 40;          // valid indexes are 0, 1, 2
    return 0;
}
```

This **buffer overrun** can overwrite neighbouring data. Reading past the end can return garbage. **Prevention:** keep every index within `0` to `size - 1`; with `std::vector`, `v.at(i)` checks the index and throws an exception instead of corrupting memory.

## How these errors show up

| Symptom | Possible cause |
|---|---|
| Crash ("segmentation fault" on many systems) | null or dangling pointer, overrun |
| Wrong values that change between runs | uninitialized pointer, dangling pointer |
| Program works but memory use keeps growing | leak |
| Crash or odd behaviour at a different place from the mistake | an earlier overrun or double release corrupted memory |

The last row is the reason these bugs are hard: **the place where the program fails is often not the place where the mistake was made.**

## Finding them with tools you already have

1. **Compiler warnings.** Compile with `-Wall -Wextra`. Compilers flag some of these directly, for example returning the address of a local variable.
2. **The address sanitizer.** Both `g++` and `clang++` can build a program that checks memory use as it runs:

   ```
   g++ -std=c++17 -g -fsanitize=address -o program program.cpp
   ./program
   ```

   When the program misuses memory, the run stops with a report that names the kind of error (for example *heap-use-after-free* or *heap-buffer-overflow*) and the lines involved. This makes the error point to the mistake rather than to a later symptom. The checks slow the program down, so they are for development, not production.
3. **A debugger**, to stop at a crash and inspect the call stack.

## Avoiding most of them by design

| Instead of | Prefer | Errors avoided |
|---|---|---|
| Raw array with manual size | `std::vector` | overrun with `at()`, leaks, mismatched release |
| `char` arrays for text | `std::string` | overruns, leaks |
| `new` / `delete` | a local value, or a library type | leaks, double release, use after release |
| Pointer when an object always exists | a reference | null dereference |
| Raw owning pointer | a smart pointer (a library type that releases automatically; introduced separately) | leaks, double release |

The earlier ideas still matter: understanding stack and heap and ownership is what lets you *read* a crash report and see why the library types are built the way they are.

## Common misconceptions

- **"If it did not crash, it was fine."** Undefined behaviour can look correct and still be wrong.
- **"A memory error always happens where the crash is reported."** The cause is frequently earlier.
- **"Setting a pointer to `nullptr` after `delete` fixes ownership."** It only makes accidental use fail more predictably.
- **"Leaks are harmless because the program ends."** In a long-running program a leak eventually exhausts memory.
- **"These errors are the price of C++."** They are mostly the price of using raw pointers and manual allocation, which everyday C++ avoids.

## Key points to remember

- Misusing memory is undefined behaviour: anything can happen, including appearing to work.
- The common errors are uninitialized pointers, null dereference, dangling pointers and references, leaks, double release, mismatched `new`/`delete` forms, and out-of-range indexing.
- A failure often appears far from its cause.
- Warnings (`-Wall -Wextra`) and the address sanitizer catch many of these early.
- Using values, `std::vector`, `std::string`, references and smart pointers avoids most memory errors by design.
