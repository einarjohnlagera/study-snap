## Overview

A **unit test** checks one small piece of a program, a *unit* such as a single function, in isolation, and reports whether it behaves as expected. Collections of unit tests let you change code with confidence: after every change you rerun them, and a test that used to pass but now fails tells you exactly what you broke. This note builds on the idea of a test driver using `assert` and develops it into a small, practical unit-testing structure in C++17: a separate test file for a module, independent test functions, and a runner that reports every failure instead of stopping at the first. The C++ standard library has no unit-testing framework, so the structure is built by hand; the final section names the established frameworks.

It assumes you can compile several files together and know what `assert` does.

## What makes a good unit test

| Property | Meaning |
|---|---|
| **Focused** | It checks one behaviour, so a failure points to one cause. |
| **Independent** | It does not depend on another test having run first or on leftover state. |
| **Repeatable** | It gives the same result every time it is run. |
| **Fast** | It runs in a fraction of a second, so it can be run after every change. |
| **Self-checking** | It decides pass or fail itself, without a person reading output. |

A useful shape for each test is **arrange, act, assert**: set up the inputs, call the unit, then compare the result with the expected value.

## Testing a module

Suppose a module declares and defines two functions.

**`stats.h`**

```cpp
#ifndef STATS_H
#define STATS_H

#include <vector>

// Returns the arithmetic mean of the values. The vector must not be empty.
double mean(const std::vector<double>& values);

// Returns the largest value. The vector must not be empty.
double largest(const std::vector<double>& values);

#endif
```

**`stats.cpp`**

```cpp
#include "stats.h"

double mean(const std::vector<double>& values) {
    double total = 0.0;
    for (double value : values) {
        total = total + value;
    }
    return total / static_cast<double>(values.size());
}

double largest(const std::vector<double>& values) {
    double best = values[0];
    for (double value : values) {
        if (value > best) {
            best = value;
        }
    }
    return best;
}
```

The tests live in a **separate file** that includes only the header, so they test the module the way any other code would use it.

## A small test runner

Using `assert` stops at the first failure. A unit-test run should instead *count* failures and keep going, so one run shows everything that is wrong. A tiny helper does this without any framework:

```cpp
#include <cmath>
#include <iostream>
#include <string>
#include <vector>
#include "stats.h"

static int checksRun = 0;
static int checksFailed = 0;

void checkTrue(bool condition, const std::string& description) {
    checksRun = checksRun + 1;
    if (!condition) {
        checksFailed = checksFailed + 1;
        std::cout << "FAIL: " << description << '\n';
    }
}

// Floating-point numbers should be compared with a tolerance, not with ==.
void checkClose(double actual, double expected, const std::string& description) {
    checkTrue(std::fabs(actual - expected) < 1e-9, description);
}

void testMeanOfSeveralValues() {
    std::vector<double> values = {2.0, 4.0, 6.0};          // arrange
    double result = mean(values);                          // act
    checkClose(result, 4.0, "mean of 2, 4, 6 is 4");       // assert
}

void testMeanOfOneValue() {
    std::vector<double> values = {5.0};
    checkClose(mean(values), 5.0, "mean of a single value is that value");
}

void testLargestWithNegatives() {
    std::vector<double> values = {-8.0, -2.0, -5.0};
    checkClose(largest(values), -2.0, "largest of all-negative values");
}

void testLargestWhenFirstIsLargest() {
    std::vector<double> values = {9.0, 1.0, 3.0};
    checkClose(largest(values), 9.0, "largest when it is the first element");
}

int main() {
    testMeanOfSeveralValues();
    testMeanOfOneValue();
    testLargestWithNegatives();
    testLargestWhenFirstIsLargest();

    std::cout << checksRun - checksFailed << " of " << checksRun << " checks passed\n";
    return checksFailed == 0 ? 0 : 1;
}
```

Build and run the module and its tests together:

```
g++ -std=c++17 -Wall -Wextra test_stats.cpp stats.cpp -o test_stats
./test_stats
```

The output ends with `4 of 4 checks passed`. If a check fails, it prints a `FAIL:` line with its description and the run continues. The program returns 0 when everything passes and 1 otherwise; that **exit status** lets scripts and tools tell whether the tests passed.

### Points about this structure

- **Each test is its own function** with a descriptive name and its own data, so tests are independent.
- **Descriptions state the behaviour** being checked, so a failure message is readable without opening the code.
- **Floating-point comparison uses a tolerance.** Computed `double` values often differ in the last digits, so `==` is unreliable.
- **Edge cases are tests too**: a single value, all-negative values, the maximum in first position.

## Testing that an error is reported

Some units are *supposed* to fail on bad input. A test can check that by catching the exception the code is documented to throw (exceptions are covered separately):

```cpp
#include <iostream>
#include <stdexcept>
#include <vector>

double safeMean(const std::vector<double>& values) {
    if (values.empty()) {
        throw std::invalid_argument("mean of an empty list");
    }
    double total = 0.0;
    for (double value : values) {
        total = total + value;
    }
    return total / static_cast<double>(values.size());
}

int main() {
    bool reported = false;
    try {
        std::vector<double> none;
        safeMean(none);
    } catch (const std::invalid_argument&) {
        reported = true;
    }
    std::cout << (reported ? "PASS" : "FAIL") << ": empty input is reported\n";
    return 0;
}
```

## What tests can and cannot do

- **Regression protection.** After a change, rerunning the tests shows whether earlier behaviour still works.
- **A specification that runs.** A test records, precisely, what a unit is supposed to do.
- **Not a proof.** A passing suite shows that the tested cases work; behaviours nobody tested are unchecked.
- **The tests can be wrong too.** An expected value worked out incorrectly will pass wrong code and fail correct code; derive expected values independently of the code.

## Frameworks

Larger projects use established unit-testing frameworks, such as Catch2, doctest and GoogleTest. They provide the same ideas as the hand-built runner above, namely named tests, assertions that continue after failure, and a summary, together with extra features. They are not part of the C++ standard, so a project must add them. Learning the structure by hand makes any framework easy to read.

## Common misconceptions

- **"A unit test checks the whole program."** It checks one small unit, which is what makes failures easy to locate.
- **"Tests are finished when they pass."** They are kept and rerun after every change.
- **"Use `==` on `double` results."** Use a tolerance.
- **"Tests can share variables to save effort."** Shared state makes tests depend on each other and on their order.
- **"More tests means better tests."** Each test should target a distinct behaviour or edge case.

## Key points to remember

- A unit test checks one small unit in isolation; good tests are focused, independent, repeatable, fast and self-checking.
- Arrange, act, assert is a clear shape for each test.
- Put tests in a separate file that includes the module's header, and compile it with the module's source file.
- A hand-built runner that counts failures, continues after one, prints a summary and returns a non-zero exit status when any check fails is enough for small projects.
- Compare floating-point values with a tolerance, test edge cases, and test that errors are reported when the unit is meant to report them.
- Passing tests demonstrate tested behaviour only; frameworks such as Catch2, doctest and GoogleTest build on the same ideas.
