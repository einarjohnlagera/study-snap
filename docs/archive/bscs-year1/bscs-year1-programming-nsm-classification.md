# Programming 1 and 2 — Final N / S Classification (applied 2026-10-01)

> **Final (2026-10-01).** D-27, D-35 and D-36 are owner-approved and the titles, splits and additions below are APPLIED in `bscs-year1-shaping.tsv`. No production writes, no notes generated, no collections created.

**Rule.** **N** = teaches a concept with no syntax or mechanism to learn; every claim and assessment answer is correct in C++, C, Java and Python; **neutral title**. **S** = teaches how a construct or mechanism works in C++ (syntax, semantics, runtime, memory model, library, tooling); **the title names C++**. **M** = mixed; split only when both halves are meaningful independent units, otherwise resolve to one N or S note.

**Result.** 64 original notes became **71** after 7 splits (**31 N + 40 S + 0 M**), plus the 2 approved additions (S) = **73 notes in the TSV: Programming 1 = 42, Programming 2 = 31; N 31, S 42.**

| Plan | Section (original) | Original title | Class | Final title(s) as in the TSV | If M: split disposition | Why | Reference-language dependency |
|---|---|---|---|---|---|---|---|
| Computer Programming 1 | Problem Solving and Algorithms | What Programming Is and How Programs Run | N | What Programming Is and How Programs Run **[N]** | n/a | Concept of program, instruction, execution, input and output; true in every language. | None |
| Computer Programming 1 | Problem Solving and Algorithms | Problem-Solving Steps in Programming | N | Problem-Solving Steps in Programming **[N]** | n/a | Language-independent process (understand, plan, implement, test, refine). | None |
| Computer Programming 1 | Problem Solving and Algorithms | Algorithms and Their Properties | N | Algorithms and Their Properties **[N]** | n/a | Also carries sequence, selection and repetition as algorithm building blocks, which is why later syntax notes need no neutral twin. | None |
| Computer Programming 1 | Problem Solving and Algorithms | Pseudocode and Flowcharts | N | Pseudocode and Flowcharts **[N]** | n/a | Language-independent by definition. | None |
| Computer Programming 1 | Problem Solving and Algorithms | Compilers, Interpreters and the Program Development Cycle | M | Building and Running a C++ Program **[S]** | REMAIN ONE S NOTE. The neutral half (what compilers and interpreters do) is already the territory of Introduction to Computing's 'Programming Languages and Language Levels'; a separate N note would duplicate it. Curator verifies that Intro note's scope includes translators; if not, split. | The practical value here is the toolchain workflow: write, compile, link, run, read the result. | Toolchain (compiler, linker, build command) |
| Computer Programming 1 | Program Structure, Variables and Data Types | Structure of a Program | S | Structure of a C++ Program **[S]** | n/a | Entry point, includes, statements and comment syntax differ by language. | Syntax |
| Computer Programming 1 | Program Structure, Variables and Data Types | Variables, Constants and Identifiers | M | Variables, Assignment and Program State **[N]**<br>Declaring Variables and Constants in C++ **[S]** | SPLIT into N + S. Two independent units: the state model (a named value that changes; assignment; tracing state) that every later trace note depends on, versus C++ declaration, initialisation, const and naming rules. | The state model is universal and reusable by any journey; declaration mechanics are C++. | Declaration syntax, static typing, const (S half only) |
| Computer Programming 1 | Program Structure, Variables and Data Types | Primitive Data Types | S | Fundamental Data Types in C++ **[S]** | n/a | Types, sizes, ranges, char and bool differ by language. The neutral idea (types classify data) is covered by Introduction to Computing's information-representation notes. Includes minimal std::string use. | Type system, fixed-width types |
| Computer Programming 1 | Program Structure, Variables and Data Types | Type Conversion and Casting | S | Type Conversion and Casting in C++ **[S]** | n/a | Implicit conversion, narrowing and explicit casts are language rules. | Type system |
| Computer Programming 1 | Expressions and Operators | Arithmetic Operators and Expressions | S | Arithmetic Operators and Expressions in C++ **[S]** | n/a | Operator set and expression semantics are language-specific. | Syntax and semantics |
| Computer Programming 1 | Expressions and Operators | Operator Precedence and Order of Evaluation | S | Operator Precedence and Order of Evaluation in C++ **[S]** | n/a | Precedence tables differ and C++ leaves some evaluation order unspecified. | Language rules |
| Computer Programming 1 | Expressions and Operators | Relational and Logical Operators | S | Relational and Logical Operators in C++ **[S]** | n/a | Symbols, truthiness of non-Boolean values and the = versus == pitfall are language-specific. Logic itself is Discrete Structures I's job. | Syntax and semantics |
| Computer Programming 1 | Expressions and Operators | Integer Division, Modulo and Numeric Limits | S | Integer Division, Modulo and Numeric Limits in C++ **[S]** | n/a | -7 / 2 is -3 in C++, C and Java and -4 in Python; overflow and limits differ; the exact behaviour is the knowledge. Splitting would be artificial. | Numeric semantics (LANGUAGE-DRIVEN in part: numeric limits) |
| Computer Programming 1 | Input and Output | Console Input and Output | S | Console Input and Output in C++ **[S]** | n/a | Stream APIs differ entirely. | Standard library |
| Computer Programming 1 | Input and Output | Formatted Output | S | Formatted Output in C++ **[S]** | n/a | Manipulators, printf, format strings and f-strings differ. | Standard library |
| Computer Programming 1 | Input and Output | Input Validation | M | Input Validation: Checking Before Trusting Data **[N]**<br>Handling Invalid Console Input in C++ **[S]** | SPLIT into N + S. Validation strategy (range, type and format checks; loop until valid; sentinel) is reusable by IT and IS; stream failure-state handling is C++. | Two independent units of different reuse value. | Stream failure state (S half only) |
| Computer Programming 1 | Selection | If and If-Else Statements | S | If and If-Else Statements in C++ **[S]** | n/a | Teaches how to write selection; the neutral concept is already introduced in 'Algorithms and Their Properties'. A twin N note would fragment a tiny topic. | Syntax |
| Computer Programming 1 | Selection | Nested Selection and Switch Statements | S | Nested Selection and Switch Statements in C++ **[S]** | n/a | switch semantics (fall-through) differ or are absent. | Syntax and semantics |
| Computer Programming 1 | Selection | Boolean Conditions and Short-Circuit Evaluation | M | Boolean Conditions and Short-Circuit Evaluation **[N]** | REMAIN ONE N NOTE. Restricted to strictly Boolean operands, short-circuit semantics are identical in all four languages; truthiness of non-Boolean values belongs to 'Relational and Logical Operators in C++'. Curator checks overlap with Discrete Structures I logic notes. | Implementation detail is incidental once scoped to Boolean operands. | None (if scoped as stated) |
| Computer Programming 1 | Iteration | While and Do-While Loops | S | While and Do-While Loops in C++ **[S]** | n/a | Python has no do-while; syntax differs. | Syntax |
| Computer Programming 1 | Iteration | For Loops | S | For Loops in C++ **[S]** | n/a | Counting for versus for-each differs by language. | Syntax |
| Computer Programming 1 | Iteration | Nested Loops | N | Nested Loops **[N]** | n/a | Composition and tracing of iteration; semantics identical; no new syntax. | None |
| Computer Programming 1 | Iteration | Loop Control and Common Loop Patterns | N | Loop Control and Common Loop Patterns **[N]** | n/a | break, continue and the accumulator, counter, sentinel and search patterns exist and behave the same in all four (loop-else stays out). | None |
| Computer Programming 1 | Functions and Procedural Abstraction | Functions and Procedural Abstraction | M | Functions and Procedural Abstraction **[N]**<br>Defining and Calling Functions in C++ **[S]** | SPLIT into N + S. Abstraction and decomposition are concepts; prototypes, definitions and calls are C++ syntax. | Two independent units. | Syntax (S half only) |
| Computer Programming 1 | Functions and Procedural Abstraction | Parameters, Argument Passing and Return Values | M | Values, References and Aliasing **[N]**<br>Parameters, Argument Passing and Return Values in C++ **[S]** | SPLIT into N + S. The copy-versus-share idea (aliasing, why sharing changes what other names see) is concept-only and is needed BEFORE by-reference parameters; the passing mechanics are C++. The N note asserts no language-specific copy rules. | Order fix as well as a split: the aliasing concept must precede reference parameters. | Reference parameters (S half only) |
| Computer Programming 1 | Functions and Procedural Abstraction | Scope and Lifetime of Variables | S | Scope and Lifetime of Variables in C++ **[S]** | n/a | Block scope and storage duration are C++ rules; Python scoping differs. | Language rules |
| Computer Programming 1 | Functions and Procedural Abstraction | Modular Program Design | N | Modular Program Design **[N]** | n/a | Design principles (cohesion, decomposition, top-down design); curator checks overlap with 'Functions and Procedural Abstraction'. | None |
| Computer Programming 1 | Recursion | Recursion: Base Case and Recursive Case | N | Recursion: Base Case and Recursive Case **[N]** | n/a | Concept and termination reasoning are language-independent. | None |
| Computer Programming 1 | Recursion | Tracing Recursive Calls | N | Tracing Recursive Calls **[N]** | n/a | Call-stack tracing is language-independent. | None |
| Computer Programming 1 | Testing, Debugging and Code Quality | Tracing and Dry Runs | N | Tracing and Dry Runs **[N]** | n/a | Technique is language-independent. | None |
| Computer Programming 1 | Testing, Debugging and Code Quality | Syntax, Runtime and Logic Errors | M | Syntax, Runtime and Logic Errors **[N]**<br>Reading C++ Compiler Errors and Warnings **[S]** | SPLIT into N + S. The error categories are a concept; reading compiler, linker and warning messages is a distinct C++ skill. | Which errors a compiler catches differs by language, so the categories note stays definitional only. | Compiler diagnostics (S half only) |
| Computer Programming 1 | Testing, Debugging and Code Quality | Debugging Techniques | M | Debugging Techniques **[N]**<br>Debugging C++ Programs with a Debugger **[S]** | SPLIT into N + S. Hypothesis, reproduce, isolate and print-tracing are universal; breakpoints, stepping and watches are tooling. | Two independent units; supports D-05 (executable debugging). | Tooling (S half only) |
| Computer Programming 1 | Testing, Debugging and Code Quality | Designing Test Cases | N | Designing Test Cases **[N]** | n/a | Equivalence classes and boundary values are language-independent. | None |
| Computer Programming 1 | Testing, Debugging and Code Quality | Documentation, Comments and Coding Style | M | Documentation, Comments and Coding Style **[N]** | REMAIN ONE N NOTE. Commenting for intent, meaningful names and consistent formatting hold in every language; comment syntax lives in 'Structure of a C++ Program'. | Language style guides are incidental to the canonical knowledge. | None |
| Computer Programming 1 | Testing, Debugging and Code Quality | Reviewing Another Programmer's Code | N | Reviewing Another Programmer's Code **[N]** | n/a | Review practice is language-independent (CMO 25 LO2). | None |
| Computer Programming 2 | Arrays | One-Dimensional Arrays | S | One-Dimensional Arrays in C++ **[S]** | n/a | Fixed-size contiguous arrays (CMO 25 LO1); Python has no such type. | Language type (CMO-driven) |
| Computer Programming 2 | Arrays | Array Traversal and Common Operations | S | Array Traversal and Common Operations in C++ **[S]** | n/a | Bounds and off-by-one behaviour are language-specific; the traversal algorithms are covered by the searching and sorting notes. | Language semantics |
| Computer Programming 2 | Arrays | Multidimensional Arrays | S | Multidimensional Arrays in C++ **[S]** | n/a | Layout and declaration are language-specific. | Language type |
| Computer Programming 2 | Searching and Sorting Basics | Elementary Sorting: Bubble, Selection and Insertion | N | Elementary Sorting: Bubble, Selection and Insertion **[N]** | n/a | The algorithm is the knowledge unit; pseudocode plus trace. Not a CMO Intermediate Programming outcome (see progression audit). | None |
| Computer Programming 2 | Searching and Sorting Basics | Linear and Binary Search | N | Linear and Binary Search **[N]** | n/a | Algorithms are language-independent. | None |
| Computer Programming 2 | Searching and Sorting Basics | Comparing Algorithm Efficiency Informally | N | Comparing Algorithm Efficiency Informally **[N]** | n/a | Language-independent. | None |
| Computer Programming 2 | Strings and Text Processing | Strings as Character Sequences | S | Strings as Character Sequences in C++ **[S]** | n/a | std::string versus C-style arrays; Java and Python strings are immutable. | Standard library and type |
| Computer Programming 2 | Strings and Text Processing | String Operations and Library Functions | S | String Operations and Library Functions in C++ **[S]** | n/a | Library APIs differ. | Standard library |
| Computer Programming 2 | Strings and Text Processing | Text Parsing and Simple Pattern Matching | S | Text Parsing and Simple Pattern Matching in C++ **[S]** | n/a | The scanning idea is small; the string API dominates. Splitting would be artificial. | Standard library |
| Computer Programming 2 | Structures and Records | Structures and Records | S | Structures and Records in C++ **[S]** | n/a | No struct in Java or Python (CMO 25 LO1 'structures'). | Language type (CMO-driven) |
| Computer Programming 2 | Structures and Records | Arrays of Structures | S | Arrays of Structures in C++ **[S]** | n/a | Follows Structures and Records. | Language type |
| Computer Programming 2 | Memory, Pointers and References | Memory Layout: Stack and Heap | S | Memory Layout: Stack and Heap in C++ **[S]** | n/a | 'Where do locals live?' has different correct answers in C++, Java and Python. | Memory model (LANGUAGE-DRIVEN) |
| Computer Programming 2 | Memory, Pointers and References | Pointers and References | S | Pointers and References in C++ **[S]** | n/a | Builds on the neutral 'Values, References and Aliasing'. Pointers exist only in C/C++. | Memory model (LANGUAGE-DRIVEN) |
| Computer Programming 2 | Memory, Pointers and References | Dynamic Memory Allocation | S | Dynamic Memory Allocation in C++ **[S]** | n/a | Manual allocation is C/C++ mechanics; managed languages have no equivalent unit. | Memory model (LANGUAGE-DRIVEN) |
| Computer Programming 2 | Memory, Pointers and References | Common Memory Errors | S | Common Memory Errors in C++ **[S]** | n/a | Dangling pointers, leaks and overruns are C/C++ errors. | Memory model (LANGUAGE-DRIVEN) |
| Computer Programming 2 | Linked Structures | Linked Lists: Nodes and Links | M | Linked Lists: Nodes and Links **[N]** | REMAIN ONE N NOTE. Node, link, head, traversal idea and contrast with arrays hold in every language (CMO 25 LO1); the pointer implementation is incidental here and lives in the S note that follows. | Implementation detail is incidental to the concept. | None |
| Computer Programming 2 | Linked Structures | Linked List Operations | S | Linked List Operations in C++ **[S]** | n/a | Node definition, insertion, deletion, traversal and freeing by pointer manipulation. | Memory model |
| Computer Programming 2 | Linked Structures | Static versus Dynamic Data Structure Implementations | M | Static versus Dynamic Data Structure Implementations **[N]** | REMAIN ONE N NOTE (CMO 25 LO3). The trade-off (fixed size versus growable; contiguous versus linked) is language-independent; the C++ demonstrations live in the array, vector and linked-list S notes. | Implementation detail is incidental. | None |
| Computer Programming 2 | Recursion Revisited | Recursive Problem Solving | N | Recursive Problem Solving **[N]** | n/a | Language-independent. | None |
| Computer Programming 2 | Recursion Revisited | Recursion versus Iteration | N | Recursion versus Iteration **[N]** | n/a | Language-independent. | None |
| Computer Programming 2 | Files and Streams | Text File Input and Output | S | Text File Input and Output in C++ **[S]** | n/a | File stream APIs differ. | Standard library |
| Computer Programming 2 | Files and Streams | Reading and Writing Structured Records | S | Reading and Writing Structured Records in C++ **[S]** | n/a | Serialisation APIs differ. | Standard library |
| Computer Programming 2 | Exceptions and Robust Programming | Errors and Exception Handling | M | Error Handling Strategies: Return Codes, Exceptions and Failing Safely **[N]**<br>Exceptions in C++ **[S]** | SPLIT into N + S. Strategies for signalling and handling errors are language-independent; try, catch and throw are C++. | Two independent units; C has no exceptions, so the N note is the reusable part. | Language feature (S half only) |
| Computer Programming 2 | Exceptions and Robust Programming | Defensive Programming and Assertions | M | Defensive Programming and Assertions **[N]** | REMAIN ONE N NOTE. Preconditions, assertions versus error handling and failing fast hold in every language. Curator checks overlap with Discrete Structures I 'Proving Program Correctness: Assertions and Loop Invariants' (different treatment: practice versus proof). | Assertion syntax is incidental. | None |
| Computer Programming 2 | Standard Libraries and Modules | Using Standard Libraries | S | Using Standard Libraries in C++ **[S]** | n/a | Libraries differ entirely. | Standard library |
| Computer Programming 2 | Standard Libraries and Modules | Building Reusable Modules | S | Building Reusable Modules in C++ **[S]** | n/a | Headers, translation units and include guards; packages and modules differ elsewhere. | Compilation model (LANGUAGE-DRIVEN) |
| Computer Programming 2 | Program Quality | Unit Testing | S | Unit Testing in C++ **[S]** | n/a | The principle is covered by 'Designing Test Cases' (N); this note is executable testing with assert-based drivers. No standard framework exists in C or C++. | Tooling |
| Computer Programming 2 | Program Quality | Code Review and Refactoring | N | Code Review and Refactoring **[N]** | n/a | Language-independent practice. | None |
| Computer Programming 2 | Program Quality | Structured Problem Solving with Larger Programs | N | Structured Problem Solving with Larger Programs **[N]** | n/a | Language-independent process. | None |

## Reconciliation

| | Original | Original N / S / M | After splits | N | S | M | With approved additions | N | S |
|---|---|---|---|---|---|---|---|---|---|
| Computer Programming 1 | 35 | 12 / 14 / 9 | 41 | 20 | 21 | 0 | 42 | 20 | 22 |
| Computer Programming 2 | 29 | 7 / 18 / 4 | 30 | 11 | 19 | 0 | 31 | 11 | 20 |
| **Combined** | **64** | **19 / 32 / 13** | **71** | **31** | **40** | **0** | **73** | **31** | **42** |

The 7 splits: Variables (P1), Input Validation (P1), Functions (P1), Parameters (P1), Syntax/Runtime/Logic Errors (P1), Debugging Techniques (P1), Errors and Exception Handling (P2). Six M rows resolved without a split: Compilers and Interpreters, Boolean Conditions, Documentation and Style (P1); Linked Lists: Nodes and Links, Static versus Dynamic Implementations, Defensive Programming (P2).

## Difference from the earlier D-28 record

D-28 recorded P1 = 13 N / 17 S / 5 M and P2 = 7 N / 15 S / 7 M (combined 20 / 32 / 12). Two things moved the totals: a **stricter N rule** and **M meaning "an independent neutral unit plus an independent language unit"**. 12 of 64 rows changed class:

| Note | Was | Now | Why |
|---|---|---|---|
| Compilers, Interpreters and the Program Development Cycle | S | M | S to M: the neutral half is already owned by Introduction to Computing's language-levels note, so it resolves to one S note rather than a split. |
| Variables, Constants and Identifiers | S | M | S to M: the state model is an independent neutral unit that later trace notes depend on. |
| Input Validation | S | M | S to M: validation strategy is independently reusable by IT and IS; only stream failure-state handling is C++. |
| If and If-Else Statements | N | S | N to S: under the stricter rule a note that teaches how to write a construct is S. |
| Loop Control and Common Loop Patterns | M | N | M to N: break, continue and the common patterns are identical in all four languages once loop-else is excluded. |
| Functions and Procedural Abstraction | N | M | N to M: abstraction and decomposition are a concept; prototypes, definitions and calls are syntax. Split. |
| Parameters, Argument Passing and Return Values | S | M | S to M: the aliasing concept is independent and must precede by-reference parameters. Split. |
| Array Traversal and Common Operations | M | S | M to S: splitting would be artificial. |
| Text Parsing and Simple Pattern Matching | M | S | M to S: the scanning idea is small and the string API dominates. |
| Memory Layout: Stack and Heap | M | S | M to S: the correct answer to "where do locals live?" differs by language. |
| Linked List Operations | M | S | M to S: pointer manipulation is the content. |
| Errors and Exception Handling | S | M | S to M: error-handling strategies are language-independent; try, catch and throw are C++. Split. |

## Approved additions (D-36), now in the TSV

| Plan | Section | Title | Class | Placement |
|---|---|---|---|---|
| Computer Programming 1 | Testing, Debugging and Code Quality | Testing a C++ Program with assert and Test Drivers | S | after "Designing Test Cases" |
| Computer Programming 2 | Arrays | Growable Arrays with std::vector in C++ | S | after "Multidimensional Arrays in C++" |

Approval of `std::vector` is not permission to turn Programming 2 into an STL survey.

## Ordering changes applied (real prerequisite problems only)

1. **Input Validation (both notes)** moved after Iteration into its own section ("Input Validation"). It needs selection and loops but sat in Input and Output, before both.
2. **Tracing and Dry Runs** moved next to the variable-state notes. "Tracing Recursive Calls" and every loop trace need it; it sat at the end of the course.

Everything else keeps its original order. A deterministic check of 101 prerequisite edges now reports 0 violations (see the readiness document).
