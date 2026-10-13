## Overview

Sometimes a program needs memory whose size or lifetime cannot be fixed in advance. Standard C++17 provides **dynamic memory allocation**: asking for heap memory while the program runs with `new`, and giving it back with `delete`. This note explains what those operators do, what responsibility they place on the programmer, and why that responsibility is exactly what modern C++ tries to avoid handing out by hand.

The purpose of learning this is to understand what is happening underneath library types such as `std::vector` and `std::string`, and to be able to read code that uses raw allocation. It is **not** a recommendation to write `new` and `delete` in ordinary programs. Section "Using it wisely" below explains the safer alternatives.

This note assumes you know what a pointer is and how stack and heap memory differ.

## Why allocate dynamically

Automatic (stack) variables have two limits: their size is fixed when the program is compiled, and they end when their block ends. Dynamic allocation lifts both:

- **Size known only at run time.** A program that reads a number `n` and then needs `n` values cannot declare an ordinary array of that size.
- **Lifetime longer than a function.** An object created in one function may need to be used after that function returns.
- **Structures that grow piece by piece**, such as chains of linked elements.

## `new` and `delete`

`new` requests memory for an object from the heap, constructs it, and returns a **pointer** to it. `delete` destroys the object and returns the memory.

```cpp
#include <iostream>

int main() {
    int* p = new int(42);        // allocate one int on the heap, initialized to 42
    std::cout << *p << '\n';     // 42

    *p = 100;
    std::cout << *p << '\n';     // 100

    delete p;                    // give the memory back
    p = nullptr;                 // p no longer points to anything valid
    return 0;
}
```

Three points deserve attention:

- The heap object has **no name**. The only way to reach it is through the pointer `p`.
- The object lives until `delete` is called. Leaving the block does not free it; only the pointer `p` disappears.
- After `delete p`, the pointer still holds the old address, but that memory is no longer yours. Setting it to `nullptr` makes accidental use easier to detect.

### Arrays: `new[]` and `delete[]`

To allocate several objects of the same type in one block, use the array forms, and release them with the matching array form.

```cpp
#include <iostream>

int main() {
    int n = 0;
    std::cout << "How many values? ";
    std::cin >> n;
    if (n <= 0) {
        return 1;
    }

    int* values = new int[n];            // size decided at run time
    for (int i = 0; i < n; i = i + 1) {
        values[i] = (i + 1) * 10;
    }

    int sum = 0;
    for (int i = 0; i < n; i = i + 1) {
        sum = sum + values[i];
    }
    std::cout << "Sum: " << sum << '\n';

    delete[] values;                     // new[] must be paired with delete[]
    values = nullptr;
    return 0;
}
```

The rule is a strict pairing: **`new` with `delete`, and `new[]` with `delete[]`.** Mixing them is a bug, because the program is not told how many elements were allocated.

## Ownership: who must call `delete`?

Every dynamically allocated object needs exactly one party responsible for releasing it. That responsibility is called **ownership**. With raw pointers the language does not track it; the programmer must.

Things that go wrong when ownership is unclear:

- **Never released**: a **memory leak**. The program keeps using more memory, though it still seems to work.
- **Released twice**: undefined behaviour.
- **Used after release**: a **dangling pointer**, also undefined behaviour.

Each of these is explained, with ways to detect them, in the separate treatment of common memory errors. They are the practical reason why hand-written `delete` is treated with caution.

## What can go wrong when allocation fails

If the system cannot provide the memory, `new` signals failure by throwing a `std::bad_alloc` exception. For the small examples here this is rare, but real programs that allocate large amounts of memory must be prepared for it.

## Using it wisely: the safer alternatives

Modern C++ is designed so that you rarely write `new` and `delete` yourself.

**Prefer a library type that owns its memory.** The array example above is better written as:

```cpp
#include <iostream>
#include <vector>

int main() {
    int n = 0;
    std::cout << "How many values? ";
    std::cin >> n;
    if (n <= 0) {
        return 1;
    }

    std::vector<int> values(n);          // n elements, allocated and released automatically
    for (int i = 0; i < n; i = i + 1) {
        values[i] = (i + 1) * 10;
    }

    int sum = 0;
    for (int i = 0; i < n; i = i + 1) {
        sum = sum + values[i];
    }
    std::cout << "Sum: " << sum << '\n';
    return 0;                            // no delete needed: the vector releases its memory
}
```

The vector allocates on the heap exactly as `new[]` did, but when it goes out of scope it releases the memory by itself. There is nothing to forget and nothing to release twice. The same applies to `std::string` for text.

**Tie the lifetime of heap memory to an ordinary object.** The general idea is called **RAII** (resource acquisition is initialization): an object that acquires a resource when created and releases it when destroyed. `std::vector` and `std::string` are examples. *Smart pointers*, library types such as `std::unique_ptr`, apply the same idea to a single dynamically created object and are the usual modern way to express ownership. They are a separate topic and are named here only so you know where raw `new` and `delete` fit in current practice.

**When raw allocation still appears.** You will meet it in older code, in code that implements a container or a linked structure, and in teaching, where writing it once shows what libraries do on your behalf.

## A short comparison

| Approach | Who releases the memory | Risk of leak or double release |
|---|---|---|
| Local variable | The compiler, at the end of the block | None |
| `std::vector`, `std::string` | The object itself, automatically | Very low |
| `new` / `delete` by hand | The programmer | High if ownership is unclear |

## Common misconceptions

- **"`new` is how you create objects in C++."** Objects are usually created as ordinary variables; `new` is only for objects that must outlive their scope or whose size is unknown.
- **"`delete` makes the pointer null."** It does not; the pointer keeps its old, now invalid, address.
- **"Leaving a function frees memory allocated with `new` inside it."** Only stack variables are freed; heap memory stays until released.
- **"`delete` and `delete[]` are interchangeable."** They are not; use the form that matches the allocation.
- **"Manual memory management is the right way to write fast C++."** It is a technique to understand; library types give the same performance with far fewer errors in everyday code.

## Key points to remember

- `new` allocates a heap object and returns a pointer; `delete` releases it. `new[]` pairs with `delete[]`.
- Heap memory lasts until released; leaving the block frees only the pointer, not the object.
- Someone must own every allocation; raw pointers do not track ownership, so leaks, double releases and dangling pointers become possible.
- Allocation can fail with `std::bad_alloc`.
- Prefer `std::vector`, `std::string` and, for single objects, smart pointers, which release memory automatically (RAII).
- Learning raw allocation is for understanding the model and reading existing code, not a default style.
