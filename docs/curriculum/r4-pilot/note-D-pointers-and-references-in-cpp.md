## Overview

Every variable in a running program lives somewhere in memory, and that location has an **address**. Pointers and references are the two C++ mechanisms that let code work with *where a value is stored* instead of only with the value itself. Using them is called **indirection**: reaching a value through something that refers to it.

This note introduces pointers and references as part of the computational and memory model, in standard C++17. It does not cover allocating memory dynamically or pointer arithmetic; those build on this foundation. Most everyday C++ is written with ordinary values and references, and raw pointers are one tool among several, used for specific purposes explained below.

## Variables and addresses

When you write `int x = 10;`, the program reserves some memory to hold an `int` and associates the name `x` with it. That memory has an address. The **address-of operator `&`** gives the address of a variable:

```cpp
int x = 10;
// &x is the address of x
```

Addresses are numbers determined when the program runs. You almost never care about the specific number; you care about the *relationship*: "this thing refers to that variable."

## Pointers

A **pointer** is a variable whose value is an address. Its type records what kind of value it points to.

```cpp
#include <iostream>

int main() {
    int x = 10;
    int* p = &x;            // p holds the address of x: "p points to x"

    std::cout << x << '\n';   // 10
    std::cout << *p << '\n';  // 10: the value that p points to

    *p = 25;                  // write to x through p
    std::cout << x << '\n';   // 25
    return 0;
}
```

Key points:

- `int* p` declares a pointer to `int`.
- `&x` produces the address that is stored in `p`.
- `*p` is **dereferencing**: it means "the object p points to". It can be read or assigned.
- Changing `*p` changes `x`, because they are the same object. `p` itself is a separate variable that happens to hold `x`'s address.

A pointer can be **re-pointed** to a different object:

```cpp
int y = 7;
p = &y;      // p now points to y; x is unchanged
```

### Null pointers

A pointer may refer to nothing. The value `nullptr` means "points to no object":

```cpp
int* q = nullptr;
if (q != nullptr) {
    std::cout << *q << '\n';   // only reached if q points to something
}
```

Dereferencing a null pointer has **undefined behaviour**: the language gives no guarantee about what happens, and in practice the program usually crashes. This is why pointers that might be null must be checked before use.

An **uninitialized** pointer is worse than a null one: it holds an arbitrary address. Always give a pointer a value when it is declared, `nullptr` if nothing else.

## References

A **reference** is another name for an existing object. It is written with `&` in a declaration:

```cpp
int x = 10;
int& r = x;     // r is an alias for x
r = 25;         // x is now 25
std::cout << x << '\n';   // 25
```

Properties of a reference:

- It **must be initialized** when declared, because it must refer to something.
- It **cannot be re-bound** to a different object afterwards; `r = y` would assign y's *value* to x, not make r refer to y.
- It **cannot be null**.
- You use it exactly like the original variable, with no `*`.

## Pointer or reference?

| | Pointer | Reference |
|---|---|---|
| Can be null | Yes (`nullptr`) | No |
| Can be re-pointed to another object | Yes | No |
| Must be initialized | Should be, but not enforced | Yes, always |
| Syntax to reach the object | `*p` | Just the name |
| Has its own address and storage | Yes | Behaves as an alias |

A reference is usually simpler and safer when the thing always exists and always refers to the same object. A pointer is used when "no object" is a valid state, or when the target must change.

## Why indirection exists

Indirection lets different parts of a program refer to the **same** object instead of copying it.

**Letting a function change the caller's variable.** By default, a function parameter receives a *copy*. A reference parameter or a pointer parameter lets the function change the original:

```cpp
#include <iostream>

void addOneRef(int& n) { n = n + 1; }

void addOnePtr(int* n) {
    if (n != nullptr) {
        *n = *n + 1;
    }
}

int main() {
    int a = 5;
    addOneRef(a);     // a is now 6
    addOnePtr(&a);    // a is now 7
    std::cout << a << '\n';   // 7
    return 0;
}
```

**Avoiding copies.** Passing a large object by reference avoids copying it.

**Optional relationships.** A pointer can represent "there may or may not be an object here."

**Linking data together.** Structures that connect pieces of data, such as chains of linked elements, are built by storing in each piece a pointer to the next. Building those structures, including how the pieces are created, comes later and rests on the ideas in this note.

## Lifetime: when a pointer or reference stops being valid

A pointer or reference is only useful while the object it refers to still exists. A local variable exists only until the end of the block or function where it is declared. If something still refers to it after that, it **dangles**:

```cpp
int* makeValue() {
    int local = 42;
    return &local;   // WRONG: local ceases to exist when the function returns
}
```

The returned pointer refers to memory that no longer holds `local`. Using it is undefined behaviour. Compilers with warnings enabled commonly flag this mistake, which is one reason to compile with `-Wall -Wextra`.

The rule to remember: **the object must outlive every pointer or reference that refers to it.**

## Using them wisely in modern C++

Raw pointers are part of the language, but they are not the default solution to everyday problems.

- Prefer **ordinary values** and **references** for parameters and local data.
- Prefer library types such as `std::string` and `std::vector` over manually managed memory, because they handle their own storage.
- Use a raw pointer mainly when an object may be absent, when a reference will not do because the target must change, or to understand how linked structures work.
- Modern C++ also provides *smart pointers*, library types that manage the lifetime of objects for you. They are the usual choice when ownership of dynamically created objects is involved, and are introduced separately.

Learning pointers is valuable because they reveal how data is arranged and shared in memory. It does not mean they should appear in every piece of code.

## Common misconceptions

- **"A pointer holds the value."** It holds an address; `*p` gives the value.
- **"The `*` always means the same thing."** In a declaration (`int* p`) it marks a pointer type; in an expression (`*p`) it dereferences; between two numbers (`a * b`) it multiplies.
- **"`int* a, b;` makes two pointers."** Only `a` is a pointer; `b` is an `int`. Declaring one variable per line avoids this.
- **"A reference can be reassigned to refer to something else."** Assigning to a reference changes the object it refers to.
- **"Printing a pointer prints the value it points to."** It prints the address (use `*p` for the value).
- **"A pointer that was valid stays valid."** Validity depends on the lifetime of the object it refers to.

## Key points to remember

- Every object has an address; `&x` gives it.
- A pointer stores an address, is dereferenced with `*`, can be `nullptr` and can be re-pointed.
- A reference is a permanent alias for an existing object, cannot be null and cannot be re-bound.
- Indirection lets code share and modify the same object instead of copying it, and is the basis for linked structures.
- Dereferencing a null or dangling pointer is undefined behaviour, so initialize pointers and make sure the object outlives them.
- In modern C++ prefer values, references and standard library types, and treat raw pointers as a specific tool.
