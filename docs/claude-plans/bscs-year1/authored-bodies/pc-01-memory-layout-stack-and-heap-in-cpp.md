## Overview

A running C++ program keeps its data in several regions of memory, and *where* a piece of data lives determines **how long it lasts** and **who is responsible for it**. This note builds a working picture of that memory model in standard C++17: the call stack, where local variables live, the heap (also called the free store), and storage that lasts for the whole program. The picture is a teaching model. Real systems vary in the details, but the ideas below are what pointers, dynamic allocation and many common bugs rest on.

Most everyday C++ never touches the heap directly; library types such as `std::string` and `std::vector` use it for you. Understanding the model explains *why* those types exist and what they spare you from doing.

## The regions of memory

A useful simplification divides a program's memory into four areas:

| Region | What lives there | How long it lasts |
|---|---|---|
| **Code** | The machine instructions of your functions | The whole run |
| **Static storage** | Global variables and variables declared `static` | The whole run |
| **Stack** | Function parameters and local variables | Until the function or block ends |
| **Heap (free store)** | Objects created on request while the program runs | Until the program releases them |

The names *stack* and *heap* describe how each region is managed, not separate pieces of hardware.

## The call stack

Every time a function is called, the program reserves a block of memory for that call, called a **stack frame**. It holds the function's parameters, its local variables and the information needed to return to the caller. When the function returns, its frame is removed. Frames are added and removed in last-in, first-out order, which is why it is called a stack.

Consider:

```cpp
#include <iostream>

int square(int x) {
    int result = x * x;      // local to square
    return result;
}

int sumOfSquares(int a, int b) {
    int first = square(a);   // square is called; its frame appears, then disappears
    int second = square(b);
    return first + second;
}

int main() {
    int total = sumOfSquares(3, 4);
    std::cout << total << '\n';   // 25
    return 0;
}
```

While `square` runs, the stack holds frames for `main`, then `sumOfSquares`, then `square`. When `square` returns, its frame, including `x` and `result`, is gone, and the next call to `square` gets a fresh frame.

### What the stack is good at

- **Automatic lifetime.** Local variables are created when the function is entered and destroyed when it ends; you do not manage them.
- **Speed and simplicity.** Reserving and releasing a frame is a very cheap operation.

### Limits of the stack

- **Lifetime is tied to scope.** A local variable cannot outlive its function. Anything that still refers to it afterwards is invalid.
- **Size is limited.** The stack is of fixed, typically modest, size. Very deep recursion, or recursion that never reaches its base case, uses a frame per call and eventually causes a **stack overflow**, and the program crashes.

## Scope and lifetime are different ideas

**Scope** is where in the source code a name can be used. **Lifetime** is how long the object exists while the program runs. For ordinary local variables they coincide: the object lives exactly as long as its block runs. They can differ:

- A `static` local variable has block scope but **static storage**, so it exists for the whole program and keeps its value between calls.
- A reference or pointer can still *refer* to an object after the scope that named it has ended, which is the source of dangling references (covered in the separate treatment of common memory errors).

```cpp
#include <iostream>

int nextId() {
    static int counter = 0;   // created once, lasts the whole run
    counter = counter + 1;
    return counter;
}

int main() {
    std::cout << nextId() << '\n';   // 1
    std::cout << nextId() << '\n';   // 2
    return 0;
}
```

## The heap

Some data cannot live on the stack: its size may not be known until the program runs, or it must outlive the function that created it. The **heap** is memory the program requests while it is running, and it stays allocated until the program releases it. Unlike a stack frame, a heap object is not freed automatically when a block ends. The program decides, which is both its power and its risk. How to request and release heap memory directly is covered in a separate note.

An important detail is that **one object can live in two places at once**. A `std::vector<int>` variable declared inside a function is a small object on the stack, but the elements it stores are kept in heap memory that the vector manages. When the variable goes out of scope, the vector releases that heap memory itself.

```cpp
#include <iostream>
#include <vector>

int main() {
    std::vector<int> scores;          // the vector object: on the stack
    for (int i = 1; i <= 5; i = i + 1) {
        scores.push_back(i * 10);     // the elements: kept on the heap, managed by the vector
    }
    std::cout << scores.size() << '\n';   // 5
    return 0;                             // scores goes out of scope: its heap memory is released
}
```

This is the main reason modern C++ code usually relies on library types: they tie the heap memory's lifetime to an ordinary stack object, so nothing is forgotten.

## Seeing addresses

Addresses can be printed to see that different variables occupy different places. The exact numbers differ between runs and systems, so treat the output as illustration, not as something to rely on.

```cpp
#include <iostream>

void show(int depth) {
    int local = depth;
    std::cout << "depth " << depth << " local at "
              << static_cast<const void*>(&local) << '\n';
    if (depth < 3) {
        show(depth + 1);   // each call has its own frame, so its own local
    }
}

int main() {
    show(1);
    return 0;
}
```

Each recursive call prints a different address, because each call has its own `local` in its own stack frame. On many common systems the addresses decrease as the calls get deeper, but the language does not guarantee any particular direction.

## Why this matters

Almost every memory problem is a question of one of these:

- **How long does this object live?** (stack frame, whole program, until released)
- **Who is responsible for ending it?** (the compiler, the program, or a library type)
- **Can anything still refer to it after it is gone?**

The next ideas, pointers and references, dynamic allocation and common memory errors, are all answers to these three questions.

## Common misconceptions

- **"All variables are on the stack."** Locals are; globals and `static` variables are in static storage; allocated objects are on the heap.
- **"The heap is for big things and the stack for small things."** Placement is decided by lifetime and by how the object is created, not by size alone, although the stack's size limit does matter for very large local arrays.
- **"A variable disappears when its name goes out of scope."** For ordinary locals, yes; for `static` variables and heap objects, no.
- **"If a program prints the right result, its memory use was correct."** Memory mistakes can go unnoticed for a while and fail later or only on some inputs.
- **"The stack and heap are separate hardware."** They are two ways the same memory is managed.

## Key points to remember

- A C++ program's memory is usefully described as code, static storage, the stack and the heap.
- Each function call gets a stack frame for its parameters and locals; the frame ends when the function returns.
- The stack gives automatic, scope-bound lifetime but has limited size, so unbounded recursion overflows it.
- The heap holds objects whose lifetime the program, or a library type acting for it, controls.
- Scope (where a name works) and lifetime (how long an object exists) are different, and `static` locals show the difference.
- Library types such as `std::vector` keep their elements on the heap and release them automatically when the owning object ends.
