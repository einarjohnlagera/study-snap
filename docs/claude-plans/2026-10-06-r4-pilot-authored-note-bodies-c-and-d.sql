-- ============================================================================
-- R4 pilot: replace generated Note bodies with the curator-authored bodies
-- Prepared 2026-10-06 by Claude. NOT RUN BY CLAUDE. THE OWNER EXECUTES THIS.
-- (CLAUDE.md: production writes are owner-only; routing them through a tool does not change that.)
--
-- WHAT IT DOES
--   Updates notes.content (and updated_at) on 2 existing Note row(s). Nothing else:
--   no Subject, Domain Context, Applicable Programs, Authored Depth, visibility, tags,
--   status, Study Pack or collection is touched.
--
-- WHY PLAIN TEXT
--   The Full Notes view is PLAIN TEXT (whitespace-pre-wrap, not markdown; see
--   frontend/components/notes/private-note-detail-page-client.tsx). So these bodies are a plain-text
--   rendition of the authored markdown: headings as capitalised lines, tables as bullet lines, code
--   indented by four spaces. Pasting the .md files would show raw ## and | symbols.
--
-- GUARDS (re-read from production on 2026-10-06, after your Applicable Programs fix)
--   Each UPDATE matches only if the id, title, Domain Context, the OLD body length AND the exact current
--   updated_at all still match. If anything changed since, the statement updates 0 rows instead of
--   overwriting something unexpected.
--
-- HOW TO RUN (in a transaction)
--   1. Run STEP 0 (read-only). 0a must return guard_matches = 2. Save the output of 0b: it is your
--      rollback copy of the old bodies.
--   2. Run STEP 1 inside BEGIN ... check each UPDATE returned exactly 1 row.
--      If ANY statement returned 0 rows, or a length differs from the expected one: ROLLBACK, do not COMMIT,
--      and tell Claude what step 0 showed.
--   3. COMMIT, then run STEP 2 (read-only) to verify.
--
-- AFTER THE SQL (in the application, not SQL)
--   * Open each updated Note and generate its Study Pack ("Generate Study Pack"; it reads notes.content).
--     This needs your explicit confirmation as usual and updates the existing pack in place.
--   * DO NOT use "Regenerate note" or "Bulk regenerate" on these Notes: that regenerates the BODY from
--     the title and would overwrite the authored text.
--   * Do not edit the generated pack. Tell Claude "bodies replaced and packs regenerated" and it will
--     re-read and re-score with SELECT only.
--
-- NOT COVERED
--   Applicable Programs are not changed here. They were read on 2026-10-06 and already match the shaping
--   TSV (Computer Science, Information Systems, Information Technology; Note E: Computer Science only).
-- ============================================================================

-- ============================== STEP 0 (read-only) ==============================
-- 0a. Do the guards match? Expected: guard_matches = 2
SELECT count(*) AS guard_matches
FROM notes
WHERE (id = 'f79dc811-f532-453e-a68f-f0f96f3f209c'::uuid AND title = 'Pointers and References in C++' AND domain_context = 'COMPUTING' AND length(content) = 2343 AND updated_at = timestamptz '2026-10-06 01:01:16.099421+00')
   OR (id = 'bb34133d-7427-46ca-a8de-b0364cc2236a'::uuid AND title = 'Building and Running a C++ Program' AND domain_context = 'COMPUTING' AND length(content) = 1777 AND updated_at = timestamptz '2026-10-06 01:00:46.045014+00');

-- 0b. SAVE THIS OUTPUT: it is the current (generated) body of each Note, your rollback copy.
SELECT id::text AS id, title, length(content) AS length, updated_at, content
FROM notes WHERE id IN ('f79dc811-f532-453e-a68f-f0f96f3f209c','bb34133d-7427-46ca-a8de-b0364cc2236a') ORDER BY title;

-- ============================== STEP 1 (WRITES: owner only) ==============================
BEGIN;

-- Note D: "Pointers and References in C++"
-- Expected: exactly 1 row. Old body 2343 characters; new body 7893 characters.
UPDATE notes
SET content = $note_body$Pointers and References in C++

OVERVIEW

Every variable in a running program lives somewhere in memory, and that location has an address. Pointers and references are the two C++ mechanisms that let code work with where a value is stored instead of only with the value itself. Using them is called indirection: reaching a value through something that refers to it.

This note introduces pointers and references as part of the computational and memory model, in standard C++17. It does not cover allocating memory dynamically or pointer arithmetic; those build on this foundation. Most everyday C++ is written with ordinary values and references, and raw pointers are one tool among several, used for specific purposes explained below.

VARIABLES AND ADDRESSES

When you write `int x = 10;`, the program reserves some memory to hold an `int` and associates the name `x` with it. That memory has an address. The address-of operator `&` gives the address of a variable:

    int x = 10;
    // &x is the address of x

Addresses are numbers determined when the program runs. You almost never care about the specific number; you care about the relationship: "this thing refers to that variable."

POINTERS

A pointer is a variable whose value is an address. Its type records what kind of value it points to.

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

Key points:

- `int* p` declares a pointer to `int`.
- `&x` produces the address that is stored in `p`.
- `*p` is dereferencing: it means "the object p points to". It can be read or assigned.
- Changing `*p` changes `x`, because they are the same object. `p` itself is a separate variable that happens to hold `x`'s address.

A pointer can be re-pointed to a different object:

    int y = 7;
    p = &y;      // p now points to y; x is unchanged

Null pointers

A pointer may refer to nothing. The value `nullptr` means "points to no object":

    int* q = nullptr;
    if (q != nullptr) {
        std::cout << *q << '\n';   // only reached if q points to something
    }

Dereferencing a null pointer has undefined behaviour: the language gives no guarantee about what happens, and in practice the program usually crashes. This is why pointers that might be null must be checked before use.

An uninitialized pointer is worse than a null one: it holds an arbitrary address. Always give a pointer a value when it is declared, `nullptr` if nothing else.

REFERENCES

A reference is another name for an existing object. It is written with `&` in a declaration:

    int x = 10;
    int& r = x;     // r is an alias for x
    r = 25;         // x is now 25
    std::cout << x << '\n';   // 25

Properties of a reference:

- It must be initialized when declared, because it must refer to something.
- It cannot be re-bound to a different object afterwards; `r = y` would assign y's value to x, not make r refer to y.
- It cannot be null.
- You use it exactly like the original variable, with no `*`.

POINTER OR REFERENCE?

- Can be null (Pointer: Yes (`nullptr`); Reference: No)
- Can be re-pointed to another object (Pointer: Yes; Reference: No)
- Must be initialized (Pointer: Should be, but not enforced; Reference: Yes, always)
- Syntax to reach the object (Pointer: `*p`; Reference: Just the name)
- Has its own address and storage (Pointer: Yes; Reference: Behaves as an alias)

A reference is usually simpler and safer when the thing always exists and always refers to the same object. A pointer is used when "no object" is a valid state, or when the target must change.

WHY INDIRECTION EXISTS

Indirection lets different parts of a program refer to the same object instead of copying it.

Letting a function change the caller's variable. By default, a function parameter receives a copy. A reference parameter or a pointer parameter lets the function change the original:

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

Avoiding copies. Passing a large object by reference avoids copying it.

Optional relationships. A pointer can represent "there may or may not be an object here."

Linking data together. Structures that connect pieces of data, such as chains of linked elements, are built by storing in each piece a pointer to the next. Building those structures, including how the pieces are created, comes later and rests on the ideas in this note.

LIFETIME: WHEN A POINTER OR REFERENCE STOPS BEING VALID

A pointer or reference is only useful while the object it refers to still exists. A local variable exists only until the end of the block or function where it is declared. If something still refers to it after that, it dangles:

    int* makeValue() {
        int local = 42;
        return &local;   // WRONG: local ceases to exist when the function returns
    }

The returned pointer refers to memory that no longer holds `local`. Using it is undefined behaviour. Compilers with warnings enabled commonly flag this mistake, which is one reason to compile with `-Wall -Wextra`.

The rule to remember: the object must outlive every pointer or reference that refers to it.

USING THEM WISELY IN MODERN C++

Raw pointers are part of the language, but they are not the default solution to everyday problems.

- Prefer ordinary values and references for parameters and local data.
- Prefer library types such as `std::string` and `std::vector` over manually managed memory, because they handle their own storage.
- Use a raw pointer mainly when an object may be absent, when a reference will not do because the target must change, or to understand how linked structures work.
- Modern C++ also provides smart pointers, library types that manage the lifetime of objects for you. They are the usual choice when ownership of dynamically created objects is involved, and are introduced separately.

Learning pointers is valuable because they reveal how data is arranged and shared in memory. It does not mean they should appear in every piece of code.

COMMON MISCONCEPTIONS

- "A pointer holds the value." It holds an address; `*p` gives the value.
- "The `*` always means the same thing." In a declaration (`int* p`) it marks a pointer type; in an expression (`*p`) it dereferences; between two numbers (`a * b`) it multiplies.
- "`int* a, b;` makes two pointers." Only `a` is a pointer; `b` is an `int`. Declaring one variable per line avoids this.
- "A reference can be reassigned to refer to something else." Assigning to a reference changes the object it refers to.
- "Printing a pointer prints the value it points to." It prints the address (use `*p` for the value).
- "A pointer that was valid stays valid." Validity depends on the lifetime of the object it refers to.

KEY POINTS TO REMEMBER

- Every object has an address; `&x` gives it.
- A pointer stores an address, is dereferenced with `*`, can be `nullptr` and can be re-pointed.
- A reference is a permanent alias for an existing object, cannot be null and cannot be re-bound.
- Indirection lets code share and modify the same object instead of copying it, and is the basis for linked structures.
- Dereferencing a null or dangling pointer is undefined behaviour, so initialize pointers and make sure the object outlives them.
- In modern C++ prefer values, references and standard library types, and treat raw pointers as a specific tool.$note_body$,
    updated_at = now()
WHERE id = 'f79dc811-f532-453e-a68f-f0f96f3f209c'::uuid
  AND title = 'Pointers and References in C++'
  AND domain_context = 'COMPUTING'
  AND length(content) = 2343                                -- still the untouched generated body
  AND updated_at = timestamptz '2026-10-06 01:01:16.099421+00'     -- unchanged since your Applicable Programs fix (2026-10-06)
RETURNING id::text AS id, title, length(content) AS new_length;

-- Note C: "Building and Running a C++ Program"
-- Expected: exactly 1 row. Old body 1777 characters; new body 6958 characters.
UPDATE notes
SET content = $note_body$Building and Running a C++ Program

OVERVIEW

A C++ program you write is plain text. A computer cannot run that text directly, so it must first be translated into a form the machine can execute. This note follows the path from a C++ source file to a running program, using standard C++17 and a command-line compiler such as `g++` or `clang++`. Understanding this path helps you read error messages, avoid common mistakes and know what each step is doing for you. Integrated development environments (IDEs) automate these steps with buttons, but they perform the same steps underneath.

FROM SOURCE FILE TO RUNNING PROGRAM

C++ is a compiled language. The text you write is called the source code, usually stored in a file ending in `.cpp`. A tool called the compiler translates it into machine code, producing an executable file that the operating system can run.

The translation happens in a few stages, which the compiler tool normally performs in one command:

1. Preprocessing. Lines beginning with `#`, such as `#include <iostream>`, are handled first. An `#include` inserts the declarations of a library into your file so your code can use it.
2. Compiling. Your C++ is checked against the language rules and translated into machine code for each source file. Syntax and type errors are reported here.
3. Linking. The compiled code is combined with the library code it uses (for example the input/output library) to form one executable.

After these stages succeed, you run the executable. Compiling and running are separate actions: compiling creates the program; running executes it.

A FIRST PROGRAM

Save this as `hello.cpp`:

    #include <iostream>

    int main() {
        std::cout << "Hello, world!\n";
        return 0;
    }

What each part does:

- `#include <iostream>` makes the standard input/output facilities available.
- `int main()` defines the function where execution begins. Every C++ program has exactly one `main`.
- `std::cout << ...` sends text to standard output. `std::` shows that `cout` belongs to the standard library.
- `return 0;` ends `main` and reports success to the operating system. (In `main`, falling off the end also returns 0, but writing it is clear.)

COMPILING AND RUNNING FROM THE COMMAND LINE

On a Unix-like system (Linux, macOS) with `g++`:

    g++ -std=c++17 -Wall -Wextra hello.cpp -o hello
    ./hello

The first command compiles and links `hello.cpp` and writes an executable named `hello`. The second runs it from the current directory and prints `Hello, world!`.

The options mean:

- `-std=c++17`: Use the C++17 version of the language. Compilers have a default version, which may differ, so state it explicitly.
- `-Wall -Wextra`: Turn on a broad set of warnings that point out suspicious code.
- `-o hello`: Name the output executable `hello`. Without it, many compilers choose a default name such as `a.out`.

On Windows, the executable is typically named with `.exe` (for example `hello.exe`) and is run as `hello.exe` or `.\hello.exe`, depending on the shell. The ideas are identical.

THE EDIT–COMPILE–RUN CYCLE

Programming is rarely one pass. You repeat a cycle:

1. Edit the source file and save it.
2. Compile it.
3. Run the result and check the behaviour.
4. If something is wrong, go back to step 1.

A key point: after you change the source, the old executable does not change. You must compile again, or you will be running the old program. Forgetting this is one of the most common beginner confusions.

A PROGRAM THAT USES INPUT

    #include <iostream>

    int main() {
        int a = 0;
        int b = 0;

        std::cout << "Enter two integers: ";
        std::cin >> a >> b;

        std::cout << "Sum: " << (a + b) << '\n';
        return 0;
    }

Compile it the same way, run it, and type `3 4` followed by Enter. It prints `Sum: 7`. The program was compiled once; it can be run any number of times with different input without recompiling.

THREE KINDS OF PROBLEMS

It helps to know when a problem appears, because it tells you where to look.

- Compile-time error (When it appears: While compiling; no executable is produced; Example: A missing semicolon, an undeclared name, a type mismatch)
- Warning (When it appears: While compiling; an executable is produced; Example: A variable that is declared but never used)
- Runtime or logic problem (When it appears: After the program starts; Example: Wrong output, or a crash on certain input)

Reading compiler messages

Compiler messages usually name the file, the line number and a description, for example of the general form:

    hello.cpp:5:35: error: expected ';' before 'return'

Practical habits:

- Fix the first error first. One mistake can cause many later messages, which often disappear when the first is fixed.
- Look at the reported line and the line before it. A missing semicolon is often reported at the start of the next statement.
- Treat warnings seriously. A warning such as `unused variable 'x'` often marks a real mistake. With `-Wall -Wextra`, many logic slips are caught at compile time.

COMMON MISTAKES

- Running the source file instead of the executable, or trying to run the program before compiling it.
- Not recompiling after editing, and so running the old executable.
- Forgetting `-std=c++17`, then seeing errors for features the compiler's default version does not support.
- Misspelling or omitting a header, such as using `std::cout` without `#include <iostream>`.
- Giving the output the same name as the source file, which can overwrite the source and lose your work.
- Ignoring warnings until a real bug appears.
- Running from the wrong folder, so the shell cannot find the executable.

CHECKING THAT A PROGRAM SUCCEEDED

When a program finishes, it returns an exit status to the operating system. By convention 0 means success and a non-zero value signals a problem. On Unix-like shells you can see the last status with `echo $?`. This is how scripts and tools know whether a program worked, and it is why `main` returns an `int`.

LARGER PROGRAMS

Everything above uses one source file. Larger programs are split across many files and built with additional tools that automate compiling and linking. The underlying steps are the same: each source file is compiled, and the results are linked into one executable.

KEY POINTS TO REMEMBER

- C++ source code is translated by a compiler into an executable; compiling and running are separate steps.
- The translation includes preprocessing, compiling and linking, usually performed by one command.
- A typical command is `g++ -std=c++17 -Wall -Wextra file.cpp -o program`, followed by `./program`.
- Editing the source does nothing to the existing executable: recompile after every change.
- Compile-time errors stop the build, warnings do not but should be heeded, and runtime problems appear only when the program runs.
- Read compiler messages from the first error, and use the reported line number as a starting point.$note_body$,
    updated_at = now()
WHERE id = 'bb34133d-7427-46ca-a8de-b0364cc2236a'::uuid
  AND title = 'Building and Running a C++ Program'
  AND domain_context = 'COMPUTING'
  AND length(content) = 1777                                -- still the untouched generated body
  AND updated_at = timestamptz '2026-10-06 01:00:46.045014+00'     -- unchanged since your Applicable Programs fix (2026-10-06)
RETURNING id::text AS id, title, length(content) AS new_length;

-- Each UPDATE above must have returned exactly 1 row. If so:
COMMIT;
-- Otherwise:
-- ROLLBACK;


-- ============================== STEP 2 (read-only verification) ==============================
-- Expected new_length: Pointers and References in C++ = 7893, Building and Running a C++ Program = 6958
-- Expected first_line = the Note title; every other column (subject, domain_context, learner_level, visibility, status) unchanged.
SELECT id::text AS id, title, subject, domain_context, learner_level, visibility, status,
       length(content) AS new_length, split_part(content, E'\n', 1) AS first_line, updated_at
FROM notes WHERE id IN ('f79dc811-f532-453e-a68f-f0f96f3f209c','bb34133d-7427-46ca-a8de-b0364cc2236a') ORDER BY title;

-- The Study Packs are untouched until you regenerate them: their source_text still reflects the OLD body.
SELECT sp.note_id::text AS note_id, sp.id::text AS pack_id, sp.created_at, length(sp.source_text) AS pack_source_text_length
FROM study_packs sp WHERE sp.note_id IN ('f79dc811-f532-453e-a68f-f0f96f3f209c','bb34133d-7427-46ca-a8de-b0364cc2236a') ORDER BY sp.created_at;
