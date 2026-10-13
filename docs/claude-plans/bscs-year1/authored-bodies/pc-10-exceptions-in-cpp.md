## Overview

Programs meet situations they cannot handle at the point where they occur: a file that does not exist, text that is not a number, an argument that makes no sense. C++ provides **exceptions** as one way to report such a failure and handle it somewhere better placed to respond. This note explains `throw`, `try` and `catch` in C++17, uses the standard exception types, and says when exceptions are the right tool and when they are not. It limits itself to the standard library's ready-made exception types and does not define new ones.

## The problem exceptions solve

A function often detects a problem it cannot fix. Consider a function that must divide two numbers:

```cpp
int divide(int a, int b) {
    return a / b;       // b == 0 is a serious problem
}
```

The function does not know what to do if `b` is zero: print a message? return a special value? stop? The code that *called* it may know. An exception lets the function say "this failed" and transfer control to code that can decide.

## `throw`, `try` and `catch`

```cpp
#include <iostream>
#include <stdexcept>

int divide(int a, int b) {
    if (b == 0) {
        throw std::invalid_argument("divide: divisor is zero");   // report the failure
    }
    return a / b;
}

int main() {
    try {
        std::cout << divide(10, 2) << '\n';    // 5
        std::cout << divide(10, 0) << '\n';    // throws; the next line is skipped
        std::cout << "not reached\n";
    } catch (const std::invalid_argument& error) {
        std::cout << "Error: " << error.what() << '\n';
    }
    std::cout << "program continues\n";
    return 0;
}
```

The output is:

```
5
Error: divide: divisor is zero
program continues
```

How it works:

- **`throw`** creates an exception object and abandons the current function. Execution does not continue after the `throw`.
- **`try`** marks a block of code in which exceptions are watched for.
- **`catch`** names the kind of exception it handles and holds the recovery code. Control passes to it when a matching exception is thrown inside the `try`.
- After the `catch` block finishes, the program continues normally after the whole `try`/`catch` statement.
- **`what()`** returns the descriptive message the exception carries.

## Standard exception types

Include `<stdexcept>` to use these ready-made types, each of which takes a message:

| Type | Typical meaning |
|---|---|
| `std::invalid_argument` | an argument has an unacceptable value |
| `std::out_of_range` | an index or value outside the allowed range |
| `std::runtime_error` | a problem that can only be detected while running |
| `std::logic_error` | a mistake in the program's logic |

All derive from `std::exception`, so a single `catch` for `std::exception` can catch any of them.

Library functions throw them too. `std::stoi` converts text to an integer and throws `std::invalid_argument` if the text is not a number:

```cpp
#include <iostream>
#include <stdexcept>
#include <string>

int main() {
    std::string text = "abc";
    try {
        int value = std::stoi(text);
        std::cout << value << '\n';
    } catch (const std::invalid_argument& error) {
        std::cout << "Not a number: " << text << '\n';
    }
    return 0;
}
```

The output is `Not a number: abc`. Be careful with partial input: `std::stoi("12x")` does not throw; it reads the leading `12`, stops at the `x`, and returns 12. What a library function does with unexpected input is exactly the kind of detail to confirm in its documentation rather than assume.

## Where the exception goes: unwinding

When an exception is thrown, C++ looks for a matching `catch` in the current function, then in the function that called it, then in *its* caller, and so on up the chain of calls. This is called **stack unwinding**. Each function left along the way is exited, and local objects in it are destroyed properly, which is why library types such as `std::vector` and `std::string` release their memory even when an exception passes through. If no `catch` is found anywhere, the program terminates.

```cpp
#include <iostream>
#include <stdexcept>

void level3() {
    throw std::runtime_error("failure in level3");
}

void level2() {
    level3();                       // no catch here, so the exception continues upward
}

int main() {
    try {
        level2();
    } catch (const std::exception& error) {
        std::cout << "caught: " << error.what() << '\n';
    }
    return 0;
}
```

The exception thrown in `level3` is handled in `main`, the first place that is prepared to deal with it.

## Writing `catch` blocks well

- **Catch by `const` reference**: `catch (const std::invalid_argument& error)`. This avoids copying and keeps the exact type.
- **Order matters.** Catch specific types before general ones; the first matching `catch` is used.
- **Catch only what you can handle.** A `catch` that ignores the problem hides it. If you cannot recover, let the exception continue upward.
- **`catch (...)`** catches anything, but you then know nothing about it; it is rarely the right choice in ordinary code.

```cpp
try {
    // work that may fail
} catch (const std::invalid_argument& error) {
    // a specific, expected problem
} catch (const std::exception& error) {
    // any other standard exception
}
```

## When to use exceptions and when not to

| Situation | Better choice |
|---|---|
| A function cannot do its job and the caller is better placed to respond | Throw an exception |
| A failure is expected and ordinary, such as a user typing a wrong choice | Check and handle it with an ordinary `if` |
| A function may legitimately have "no result" | Return a value that says so (for example a boolean or a special return) |
| A mistake in the program itself that should never happen | Use `assert` during development |
| Resource cleanup must happen however the function exits | Use objects that clean up for themselves (such as `std::vector`) |

The general idea is that exceptions are for **exceptional** conditions, where the problem is detected far from the code that can respond. Ordinary, frequent outcomes belong in normal control flow.

## Common misconceptions

- **"A thrown exception returns a value."** It abandons the function; the code after `throw` never runs.
- **"Exceptions are just another way to return a value."** Throwing is relatively expensive compared with a normal return, which is another reason to keep exceptions for exceptional cases.
- **"Catching everything makes the program safe."** Swallowing errors hides bugs.
- **"Exceptions replace checking input."** Predictable bad input is better handled with an ordinary check and a message.
- **"An exception leaks memory held by objects on the way."** Properly built objects such as `std::vector` are cleaned up during unwinding; hand-managed raw allocations are the risk.

## Key points to remember

- `throw` reports a failure and leaves the current function; `try` watches a block; `catch` handles a named type and recovery continues after the `try`/`catch` statement.
- The standard library supplies exception types in `<stdexcept>` (`std::invalid_argument`, `std::out_of_range`, `std::runtime_error`, `std::logic_error`), all derived from `std::exception`; `what()` gives the message.
- The exception travels up the chain of callers until a matching `catch` is found, and the program terminates if none is.
- Catch by `const` reference, put specific handlers before general ones, and catch only what you can handle.
- Use exceptions for exceptional conditions detected far from where they can be handled; use ordinary checks for expected, frequent outcomes.
