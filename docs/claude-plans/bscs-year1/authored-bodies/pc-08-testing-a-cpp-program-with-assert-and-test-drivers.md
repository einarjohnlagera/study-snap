## Overview

Running a program once and seeing plausible output does not show that it is correct. **Testing** means checking a program's behaviour against results you decided in advance. This note introduces the simplest executable way to do that in C++17: a **test driver**, a small `main` function whose only job is to call the code under test with chosen inputs, and `assert`, a standard facility that stops the program when a condition is false. Choosing good test inputs is a separate skill from writing the driver; here the focus is on turning chosen inputs into checks that run automatically.

## The function under test

Suppose we write a function to decide whether a year is a leap year. In the Gregorian calendar a year is a leap year if it is divisible by 4, except that years divisible by 100 are not leap years unless they are also divisible by 400.

```cpp
bool isLeapYear(int year) {
    if (year % 400 == 0) {
        return true;
    }
    if (year % 100 == 0) {
        return false;
    }
    return year % 4 == 0;
}
```

How do we know it is right? We decide what the answers *should* be and check them.

## `assert`

`assert` comes from the header `<cassert>`. It takes a condition:

```cpp
#include <cassert>

assert(2 + 2 == 4);     // true: nothing happens
assert(2 + 2 == 5);     // false: the program stops with a message
```

If the condition is true, execution continues. If it is false, the program prints a message that names the failed expression, the file and the line, and then terminates abnormally. The exact wording differs between compilers, but it always identifies where the check failed.

## A test driver

A test driver collects checks, each stating an input and the expected output.

```cpp
#include <cassert>
#include <iostream>

bool isLeapYear(int year) {
    if (year % 400 == 0) {
        return true;
    }
    if (year % 100 == 0) {
        return false;
    }
    return year % 4 == 0;
}

int main() {
    assert(isLeapYear(2024) == true);     // divisible by 4
    assert(isLeapYear(2023) == false);    // not divisible by 4
    assert(isLeapYear(1900) == false);    // divisible by 100 but not 400
    assert(isLeapYear(2000) == true);     // divisible by 400

    std::cout << "All tests passed\n";
    return 0;
}
```

Compile and run it as usual:

```
g++ -std=c++17 -Wall -Wextra test_leap.cpp -o test_leap
./test_leap
```

If every `assert` holds, the program prints `All tests passed`. If one fails, the program stops at that line and reports it; the final message is never printed. **A test that fails is doing its job**: it has found a disagreement between what you expected and what the code does.

## Choosing the checks

A driver is only as good as its inputs. Draw them from the same categories used when designing test cases:

| Kind of input | Example for `isLeapYear` |
|---|---|
| A typical case | 2024 |
| A case that should give the opposite answer | 2023 |
| A boundary or special rule | 1900 (century), 2000 (divisible by 400) |
| An extreme or unusual value | 0, a negative year, if the problem allows them |

Cover each *rule* in the problem at least once. The four checks above correspond to the four branches of the leap-year rule; leaving out the century case would let a wrong version of the function pass.

## Seeing a failing test

Imagine the first version of the function forgot the century rule:

```cpp
// ERROR EXAMPLE: contains a deliberate mistake
bool isLeapYearWrong(int year) {
    return year % 4 == 0;
}
```

The driver's check `assert(isLeapYear(1900) == false)` would fail, because this version returns `true` for 1900. The message points at that line, and you know which case to investigate. Without the test, the mistake could remain unnoticed.

## Limits of `assert`

`assert` is a developer's tool, with some limits to be aware of:

- **The first failure stops the program**, so later checks do not run. Fix the failure and run again.
- **It reports a failure but not a summary** of how many checks passed or failed. A fuller test runner is covered in the separate treatment of unit testing.
- **It can be turned off.** Compiling with `-DNDEBUG` removes every `assert`, which is why checks must not contain code that the program depends on (for example `assert(readValue() > 0)` would stop calling `readValue()`).
- **It is not for handling bad user input.** User errors should be handled with ordinary `if` statements and messages; `assert` is for conditions that should be impossible if the program is correct.

## Habits for useful tests

1. **State the expected result before running** the code, so the check is independent of the code.
2. **One idea per check**, so a failure points at one rule.
3. **Test the edge and special cases**, not only typical ones.
4. **Keep the driver separate** from the program's normal `main`, or in its own file, so tests can be run on demand.
5. **Run all tests after every change**; a change that fixes one case can break another.
6. **Never change a check to match wrong output.** If the expected value was reasoned out correctly, the code is what needs to change.

## Common misconceptions

- **"If the program runs without crashing, it works."** Absence of a crash says nothing about correctness.
- **"More tests of the same kind make it safer."** Variety matters more than quantity: each rule and boundary needs a check.
- **"A passing test suite proves the program is correct."** It shows the tested cases work; untested cases may not.
- **"`assert` is how to validate user input."** It is for internal assumptions, and it can be disabled.
- **"A failing assert is a nuisance."** It is the intended signal that something is wrong.

## Key points to remember

- A test driver is a small program that calls the code under test with chosen inputs and compares the results with expected values.
- `assert(condition)` from `<cassert>` does nothing when the condition is true and stops the program with a message naming the file and line when it is false.
- Choose checks to cover each rule, typical and special cases, and boundaries.
- `assert` stops at the first failure, gives no summary, can be disabled with `-DNDEBUG`, and is not for user-input errors.
- Decide expected results first, test edge cases, and rerun all tests after every change.
