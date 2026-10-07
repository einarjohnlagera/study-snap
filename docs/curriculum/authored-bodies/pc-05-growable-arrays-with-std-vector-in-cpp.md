## Overview

A built-in C++ array has a size fixed when the program is written, and it does not remember its own length. Many programs need a collection that can **grow and shrink while the program runs**. The standard library provides `std::vector` for this: a sequence of values of one type, stored one after another, that manages its own memory. This note introduces the parts of `std::vector` needed for everyday work in C++17 and contrasts it with a fixed-size array. It is a focused introduction to one container, not a tour of the standard library.

## Fixed-size arrays and their limits

```cpp
int scores[5] = {70, 85, 90, 60, 75};   // exactly 5 elements, decided at compile time
```

A fixed-size array has these characteristics:

- Its size must be a constant known when the program is compiled.
- It cannot grow; adding a sixth element is not possible.
- It does not carry its own length, so the programmer must track it separately.
- Indexing outside its range is not checked and causes undefined behaviour.

These limits are a good fit for data whose size never changes. They are awkward whenever the number of items depends on input.

## `std::vector`

A `std::vector` fixes these limits. To use it, include its header and give the type of the elements in angle brackets:

```cpp
#include <iostream>
#include <vector>

int main() {
    std::vector<int> scores;          // an empty vector of int

    scores.push_back(70);             // add to the end
    scores.push_back(85);
    scores.push_back(90);

    std::cout << scores.size() << '\n';   // 3
    std::cout << scores[0] << '\n';       // 70
    std::cout << scores[2] << '\n';       // 90
    return 0;
}
```

Key operations:

| Operation | Meaning |
|---|---|
| `std::vector<int> v;` | an empty vector |
| `std::vector<int> v(5, 0);` | five elements, each 0 |
| `v.push_back(x)` | add `x` at the end; the vector grows |
| `v.size()` | the number of elements |
| `v[i]` | the element at index `i`, **not** range checked |
| `v.at(i)` | the element at index `i`, **checked**: throws `std::out_of_range` if `i` is invalid |
| `v.empty()` | true if there are no elements |
| `v.pop_back()` | remove the last element |
| `v.clear()` | remove all elements |

Indexes start at 0, as with arrays.

## Looping over a vector

```cpp
#include <iostream>
#include <vector>

int main() {
    std::vector<int> scores = {70, 85, 90, 60};

    int total = 0;
    for (std::size_t i = 0; i < scores.size(); i = i + 1) {
        total = total + scores[i];
    }
    std::cout << "Total: " << total << '\n';          // Total: 305

    int largest = scores[0];
    for (int value : scores) {                        // range-based for: each element in turn
        if (value > largest) {
            largest = value;
        }
    }
    std::cout << "Largest: " << largest << '\n';       // Largest: 90
    return 0;
}
```

Two forms appear above:

- the **indexed loop**, useful when the position matters;
- the **range-based `for`**, which reads each element in order and cannot go out of range.

`size()` returns an unsigned type, `std::size_t`. Mixing it with a signed `int` index in a comparison can trigger a compiler warning (`-Wall -Wextra` reports it), which is why the indexed loop above uses `std::size_t`.

## Growing from input

The size does not need to be known in advance, which is exactly the situation a fixed array handles badly:

```cpp
#include <iostream>
#include <vector>

int main() {
    std::vector<int> values;
    int number = 0;
    std::cout << "Enter integers, then a non-number to stop: ";
    while (std::cin >> number) {
        values.push_back(number);
    }
    std::cout << "You entered " << values.size() << " values\n";
    return 0;
}
```

## Passing a vector to a function

A vector can be large, so pass it by **reference** to avoid copying it. Use `const` when the function should only read it:

```cpp
#include <iostream>
#include <vector>

int sum(const std::vector<int>& values) {      // read-only, no copy
    int total = 0;
    for (int value : values) {
        total = total + value;
    }
    return total;
}

void addBonus(std::vector<int>& values, int bonus) {   // may modify the caller's vector
    for (std::size_t i = 0; i < values.size(); i = i + 1) {
        values[i] = values[i] + bonus;
    }
}

int main() {
    std::vector<int> scores = {70, 85, 90};
    addBonus(scores, 5);
    std::cout << sum(scores) << '\n';          // 260
    return 0;
}
```

A function that takes a vector by value gets its own copy of every element.

## How it manages memory

A `std::vector` keeps its elements together in one block of memory on the **heap**. When you add an element and the block is full, the vector allocates a larger block, moves the existing elements into it, and releases the old block, all without your involvement. The vector object itself is an ordinary variable; when it goes out of scope, it releases its block automatically. This is the idea behind safer C++: tie the lifetime of heap memory to an ordinary object so nothing needs to be released by hand.

Two consequences are worth knowing:

- **Adding to the end is usually cheap**, because the extra room is added in larger steps rather than one element at a time.
- **References and pointers to elements can become invalid** when the vector grows, because the elements may be moved to a new block. Do not keep a reference to an element across a `push_back`.

## Cost summary

| Operation | Typical cost |
|---|---|
| Reach element `i` | constant |
| Add at the end | usually constant |
| Insert or erase in the middle or at the front | grows with the number of elements that must shift |
| Search for a value (unsorted) | grows with the number of elements |

## Vector versus fixed-size array

| | Fixed-size array | `std::vector` |
|---|---|---|
| Size | fixed at compile time | grows and shrinks at run time |
| Knows its own size | no | yes (`size()`) |
| Checked access | no | `at()` |
| Memory released | automatically (it is a local) | automatically |
| Use when | the size is a fixed constant | the size depends on the program's data |

## Common misconceptions

- **"A vector is slower than an array for everything."** For ordinary use the difference is usually small, and the safety and convenience are large.
- **"`v[i]` checks the index."** It does not; use `at(i)` when you want a checked access.
- **"Pushing back into a vector makes a copy of the whole vector."** Only the new element is added, although the vector occasionally reorganizes its storage.
- **"`size()` returns an `int`."** It returns an unsigned type; comparing it directly with a negative or signed value can be wrong.
- **"A vector's elements are scattered like a linked list."** They are stored contiguously.

## Key points to remember

- `std::vector<T>` is a growable sequence of `T` that manages its own memory; include `<vector>`.
- Use `push_back` to add, `size()` for the count, `[i]` for fast access and `at(i)` for checked access.
- A range-based `for` visits every element without an index.
- Pass vectors by reference, and by `const` reference when the function only reads them.
- Elements are stored together on the heap and released automatically when the vector goes out of scope.
- References to elements can be invalidated when the vector grows.
