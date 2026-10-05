## Overview

Logic is the discipline of precise reasoning, and **propositional logic** is its simplest and most widely used form. It studies statements that are either true or false and the ways such statements can be combined. In computing it underlies conditions in programs, the design of digital circuits, database queries, search filters and the specification of what software must do. This note introduces propositions and the basic logical connectives that build compound statements from simpler ones.

## Propositions

A **proposition** is a declarative statement that is either **true** or **false**, but not both. Whether it is true or false is called its **truth value**.

| Statement | Proposition? | Reason |
|---|---|---|
| "7 is an odd number." | Yes (true) | Declarative with a definite truth value |
| "2 + 3 = 6" | Yes (false) | False statements are still propositions |
| "A triangle has four sides." | Yes (false) | Definite truth value |
| "Is the server running?" | No | A question is not a statement |
| "Restart the server." | No | A command is not a statement |
| "x + 1 = 5" | Not yet | True or false depends on the value of x; it becomes a proposition once x is given a value |
| "This statement is false." | No | It cannot consistently be assigned either truth value |

What matters is that a truth value *exists*, not that we already know it. "The number of prime numbers less than a million is even" is a proposition even if you do not yet know whether it is true.

## Propositional variables and compound propositions

To reason about the structure of statements without caring about their content, we use letters such as **p, q, r** as **propositional variables**, each standing for some proposition. For example:

- p: "The sensor reading is above the limit."
- q: "The alarm is enabled."

A proposition with no internal logical structure, like p or q, is **atomic**. Atomic propositions can be combined with **logical connectives** to form **compound propositions**, whose truth value depends only on the truth values of the parts and on the connectives used.

## The logical connectives

Because each proposition is true (T) or false (F), the meaning of a connective is fully described by a **truth table** listing its value for every combination of inputs.

### Negation (NOT), written ¬p

¬p is true exactly when p is false.

| p | ¬p |
|---|---|
| T | F |
| F | T |

If p is "The file is saved," then ¬p is "The file is not saved."

### Conjunction (AND), written p ∧ q

p ∧ q is true only when **both** p and q are true.

| p | q | p ∧ q |
|---|---|---|
| T | T | T |
| T | F | F |
| F | T | F |
| F | F | F |

### Disjunction (OR), written p ∨ q

p ∨ q is true when **at least one** of p, q is true. This is the **inclusive** or.

| p | q | p ∨ q |
|---|---|---|
| T | T | T |
| T | F | T |
| F | T | T |
| F | F | F |

### Exclusive or, written p ⊕ q

p ⊕ q is true when **exactly one** of p, q is true.

| p | q | p ⊕ q |
|---|---|---|
| T | T | F |
| T | F | T |
| F | T | T |
| F | F | F |

Ordinary English "or" is sometimes inclusive ("You need a password or a key card" may allow both) and sometimes exclusive ("You may have soup or salad"). In logic, ∨ always means the inclusive meaning, and ⊕ is used when exclusive is intended.

### Conditional (IF ... THEN), written p → q

p → q ("if p then q") is **false only when p is true and q is false**; otherwise it is true.

| p | q | p → q |
|---|---|---|
| T | T | T |
| T | F | F |
| F | T | T |
| F | F | T |

The surprising rows are those where p is false. A promise "if it rains, I will bring an umbrella" is not broken on a day when it does not rain, so the conditional counts as true. The conditional states a *guarantee*, and it is only violated when the condition holds and the result fails.

### Biconditional (IF AND ONLY IF), written p ↔ q

p ↔ q is true when p and q have the **same** truth value.

| p | q | p ↔ q |
|---|---|---|
| T | T | T |
| T | F | F |
| F | T | F |
| F | F | T |

## Building and evaluating compound propositions

Connectives can be combined and nested. To evaluate a compound proposition, work from the inside out. A compound proposition with n distinct variables has 2ⁿ rows in its truth table.

**Worked example.** Build the truth table for (p ∨ q) ∧ ¬(p ∧ q).

| p | q | p ∨ q | p ∧ q | ¬(p ∧ q) | (p ∨ q) ∧ ¬(p ∧ q) |
|---|---|---|---|---|---|
| T | T | T | T | F | F |
| T | F | T | F | T | T |
| F | T | T | F | T | T |
| F | F | F | F | T | F |

The final column is true exactly when one of p, q is true but not both, which is the exclusive or. The table shows that two differently written compound propositions can have the same meaning.

### Order of evaluation

To avoid ambiguity, connectives are evaluated in a conventional order of precedence: ¬ first, then ∧, then ∨, then →, then ↔. For example, ¬p ∨ q means (¬p) ∨ q, not ¬(p ∨ q). **Use parentheses whenever the intended grouping might be unclear**; they cost nothing and prevent mistakes.

## Translating between English and logic

Careful translation is a key skill, because natural language is often ambiguous. Let p be "the user is logged in" and q be "the page is public."

| English | Symbolic form |
|---|---|
| The user is logged in and the page is public. | p ∧ q |
| The user is not logged in. | ¬p |
| The user is logged in or the page is public. | p ∨ q |
| If the user is logged in then the page is public. | p → q |
| The page is public only if the user is logged in. | q → p |
| The user is logged in if and only if the page is public. | p ↔ q |
| The user is logged in but the page is not public. | p ∧ ¬q |

Points to watch:

- "**but**" usually means "and" logically: it adds contrast, not a different truth condition.
- "**p only if q**" means p → q, not q → p.
- "**p if q**" means q → p.
- "**unless**" is typically read as "if not": "p unless q" is ¬q → p.
- Check whether "or" is intended to be inclusive or exclusive.

## Connection to computing

Most programming languages provide operators for AND, OR and NOT, used to build the conditions that control decisions and repetition: "if the input is valid **and** the account is active, continue." Digital circuits are built from physical components that implement these same connectives. Reasoning about a condition as a compound proposition and checking it with a truth table is therefore a practical way to confirm that a rule does what you intend before it is ever written as code.

## Common misconceptions

- **"A proposition must be true."** It only needs a truth value; false statements are propositions.
- **"p → q means p causes q."** It is only a statement about truth values, not about causation.
- **"p → q is false when p is false."** It is true whenever p is false.
- **"p → q and q → p mean the same."** They do not; reversing a conditional changes its meaning.
- **"∨ means one or the other but not both."** That is ⊕; ∨ includes the case where both are true.
- **"A truth table row can be skipped if it seems unlikely."** A truth table must list every combination.

## Key points to remember

- A proposition is a declarative statement that is true or false, not both.
- Compound propositions combine propositions using ¬, ∧, ∨, ⊕, → and ↔, and their truth values are defined by truth tables.
- ∨ is inclusive; ⊕ is exclusive; p → q is false only when p is true and q is false.
- A compound proposition with n variables has 2ⁿ truth table rows.
- Precedence is ¬, ∧, ∨, →, ↔; use parentheses for clarity.
- Translating English into symbols requires care with words such as "only if," "unless" and "or."
