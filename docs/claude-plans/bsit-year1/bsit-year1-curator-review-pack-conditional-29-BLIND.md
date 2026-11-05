Status: BLIND review pack for an independent reviewer, 2026-11-05. READ-ONLY EXTRACT of production state. Contains NO prior opinions and NO dispositions. NOT implementation-ready.

# BSIT Year 1: blind review pack for the 29 conditional Discrete Mathematics Notes

Each entry gives the Note's metadata, the **full production body** and its **Study Pack** (summary, key concepts, all five quiz items with choices, correct answer and explanation). Apply the rubric (A canonical equivalence, B prerequisites and depth, C assessment treatment, D disposition, E record). Record your result per Note in `bsit-year1-curator-verification-manifest-conditional-29-BLIND.tsv` as a **recommended** disposition.

- Source: production `notes` and `study_packs`, read 2026-11-05, exactly as stored. Each Note has one Study Pack with 5 quiz items.
- Nothing was modified. No Information Technology tag may be added to any of these before individual curator approval; your result is a recommendation.
- The question for every Note: could this exact Note, with its existing Study Pack, unchanged, serve a first-year BS Information Technology learner in a one-semester Discrete Mathematics course?


---

## 1. Propositions and Logical Connectives

| Field | Value |
|---|---|
| Note id | `edeb5f50-6a28-4dd9-a622-8682d15cdae4` |
| Section in the proposed plan | Propositional Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Propositions, Logical Connectives, and Truth Tables

📘 Overview
Propositional logic represents statements with definite truth values and combines them using logical connectives. A truth table evaluates a compound proposition under every possible assignment; $n$ distinct proposition variables require $2^n$ rows.

🧠 Key Idea
The truth value of a compound proposition depends on its component truth values and the connectives joining them. An implication is false only when its antecedent is true and its consequent is false.

⚔️ Core Details:
- A proposition is a declarative statement that is either true or false, such as “The process has terminated.”
- Negation $\neg p$ reverses the truth value of proposition $p$.
- Conjunction $p \land q$ is true exactly when both propositions are true.
- Inclusive disjunction $p \lor q$ is true when at least one proposition is true.
- Implication $p \to q$ is false exactly when $p$ is true and $q$ is false.
- Biconditional $p \leftrightarrow q$ is true exactly when both propositions have the same truth value.

🎯 Why It Matters:
- Boolean expressions define branching conditions, loop guards, and filters in software.
- Truth tables can verify whether two Boolean expressions behave identically before a program transformation.
- Distinguishing implication from its converse prevents errors when reasoning about software requirements.

🧠 Quick Recall:
- Truth assignment — A mapping of each proposition variable to true or false.
- Material implication — $p \to q \equiv \neg p \lor q$; $\equiv$ denotes identical truth values under every assignment.
- Converse and contrapositive — For $p \to q$, the converse is $q \to p$; the equivalent contrapositive is $\neg q \to \neg p$.
- Tautology and contradiction — A tautology is true under every truth assignment; a contradiction is false under every truth assignment.
- De Morgan’s laws — $\neg(p \land q) \equiv \neg p \lor \neg q$ and $\neg(p \lor q) \equiv \neg p \land \neg q$.
```

### Study Pack

**Summary:** Propositional logic models declarative statements with definite truth values. In computing, these statements combine into Boolean expressions used in branching conditions, loop guards, and filters. A truth assignment maps each proposition variable to true or false.

Negation $\neg p$ reverses the truth value of $p$. Other connectives combine propositions as follows:

| Connective | Expression | True when |
|---|---|---|
| Conjunction | $p \land q$ | Both propositions are true |
| Inclusive disjunction | $p \lor q$ | At least one proposition is true |
| Biconditional | $p \leftrightarrow q$ | Both propositions have the same truth value |

Implication $p \to q$ is false only when $p$ is true and $q$ is false. Material implication satisfies $p \to q \equiv \neg p \lor q$, where $\equiv$ indicates identical truth values under every assignment.

A truth table evaluates every possible assignment. With $n$ distinct proposition variables, it requires $2^n$ rows. Truth tables can verify whether Boolean expressions behave identically before a program transformation. A tautology is true under every assignment; a contradiction is false under every assignment.

De Morgan’s laws describe how negation distributes across conjunction and disjunction: $\neg(p \land q) \equiv \neg p \lor \neg q$ and $\neg(p \lor q) \equiv \neg p \land \neg q$.

**Common Misconceptions**: Inclusive disjunction permits both propositions to be true. An implication is not generally equivalent to its converse $q \to p$; it is equivalent to its contrapositive $\neg q \to \neg p$.

**Key concepts:** Propositions; Truth Assignments; Negation; Conjunction; Inclusive Disjunction; Material Implication; Biconditional; Contrapositive; De Morgan’s Laws; Tautologies and Contradictions

**Q1** (MCQ, CONCEPTUAL): Which best describes a proposition in reasoning about software?

- A. An instruction directing a process to terminate
- B. A question asking whether a process has terminated
- C. A declarative statement with a definite truth value ✅
- D. A variable whose value has not been assigned any meaning

_Explanation:_ Propositional logic requires statements that can be evaluated as true or false, such as a claim about whether a process has terminated.

**Q2** (MCQ, CONCEPTUAL): Which statement about material implication is NOT correct?

- A. It is true when its antecedent and consequent are both true.
- B. It is false whenever its antecedent is false. ✅
- C. It is false when its antecedent is true and its consequent is false.
- D. It is equivalent to the disjunction of the negated antecedent and the consequent.

_Explanation:_ Material implication has only one falsifying assignment: a true antecedent paired with a false consequent. A false antecedent therefore cannot make the implication false.

**Q3** (MCQ, COMPUTATIONAL): A Boolean expression contains $3$ distinct proposition variables. How many rows are required in its complete truth table?

- A. $3$
- B. $6$
- C. $8$ ✅
- D. $9$

_Explanation:_ Each variable has two possible truth values. Apply the row-count formula $2^n$, substitute $n = 3$, and evaluate $2^3 = 8$. The required number of rows is $8$.

**Q4** (MCQ, CONCEPTUAL): Statement 1: An implication is logically equivalent to its contrapositive. Statement 2: An implication is always logically equivalent to its converse. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Reversing the direction and negating both components preserves the original implication’s truth values. Reversing the direction alone does not generally preserve them.

**Q5** (MCQ, CONCEPTUAL): A program condition states, “The process has terminated and the output is available.” According to De Morgan’s laws, how should its negation be expressed?

- A. The process has not terminated and the output is not available.
- B. The process has terminated or the output is available.
- C. The process has not terminated and the output is available.
- D. The process has not terminated or the output is not available. ✅

_Explanation:_ Negating a conjunction changes the connective to inclusive disjunction and negates each component. The original condition fails whenever at least one component is false.

---

## 2. Truth Tables and Compound Propositions

| Field | Value |
|---|---|
| Note id | `39de3b6f-f300-42d9-aa30-eed0f50f6259` |
| Section in the proposed plan | Propositional Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Truth Tables and Compound Propositions

📘 Overview
A truth table determines a compound proposition’s truth value for every possible assignment of truth values to its component propositions. Logical connectives combine Boolean conditions used in program guards, specifications, and digital logic.

🧠 Key Idea
A compound proposition’s truth value follows entirely from its component truth values and the meanings of its connectives. Exhaustively checking all assignments establishes whether a statement is always true, sometimes true, or equivalent to another statement.

⚔️ Core Details:
- Negation $\neg p$ reverses the truth value of the proposition $p$.
- Conjunction $p \land q$ is true exactly when both $p$ and $q$ are true.
- Inclusive disjunction $p \lor q$ is true when at least one of $p$ and $q$ is true, including when both are true.
- Implication $p \to q$ is false exactly when the antecedent $p$ is true and the consequent $q$ is false.
- Biconditional $p \leftrightarrow q$ is true exactly when $p$ and $q$ have the same truth value.
- A complete truth table for $n$ distinct propositional variables contains $2^n$ rows, one for each possible truth assignment.

🎯 Why It Matters:
- Exhaustive Boolean analysis exposes overlooked cases in program conditions, such as a guard allowing access under an unintended combination of permissions.
- Logical equivalence supports rewriting Boolean expressions without changing their results.
- Truth tables verify whether digital circuits or formal specifications produce the required outputs for every input combination.

🧠 Quick Recall:
- Tautology — A proposition that is true under every truth assignment.
- Contradiction — A proposition that is false under every truth assignment.
- Contingency — A proposition that is true under some truth assignments and false under others.
- Logical equivalence — $P \equiv Q$ means that compound propositions $P$ and $Q$ have identical truth values under every assignment.
- Implication equivalence — $p \to q \equiv \neg p \lor q$; an implication is true whenever its antecedent is false.
```

### Study Pack

**Summary:** Truth tables evaluate compound propositions for every possible assignment of truth values to their component propositions. In computing, they support analysis of program guards, Boolean expressions, digital circuits, and formal specifications.

Negation $\neg p$ reverses a truth value. Conjunction $p \land q$ is true only when both components are true. Inclusive disjunction $p \lor q$ is true when at least one component is true, including when both are true. Implication $p \to q$ is false only when $p$ is true and $q$ is false. Biconditional $p \leftrightarrow q$ is true when the components have matching truth values.

A complete truth table with $n$ distinct propositional variables has $2^n$ rows.

| Classification | Truth-table pattern |
|---|---|
| Tautology | True in every row |
| Contradiction | False in every row |
| Contingency | True in some rows and false in others |

Logical equivalence $P \equiv Q$ means that two propositions have identical truth values under every assignment. Equivalent expressions can replace one another without changing program-condition results. In particular, $p \to q \equiv \neg p \lor q$.

**Common Misconceptions**: Inclusive disjunction permits both components to be true. An implication with a false antecedent is true. Logical equivalence requires agreement under every assignment, not just one.

**Key concepts:** Truth assignments; Negation; Conjunction; Inclusive disjunction; Implication; Biconditional; Tautology; Contradiction; Contingency; Logical equivalence

**Q1** (MCQ, CONCEPTUAL): When does a specification expressed as $p \to q$ evaluate to false?

- A. When both $p$ and $q$ are false
- B. When $p$ is true and $q$ is false ✅
- C. When $p$ is false and $q$ is true
- D. When both $p$ and $q$ are true

_Explanation:_ An implication fails only when its antecedent holds but its required consequent does not. A false antecedent does not make an implication false.

**Q2** (MCQ, CONCEPTUAL): Which claim about checking or rewriting Boolean program conditions is NOT correct?

- A. Negation reverses the truth value of a condition.
- B. Conjunction requires both component conditions to be true.
- C. Agreement on a single truth assignment establishes logical equivalence. ✅
- D. Equivalent expressions can replace one another without changing their truth values.

_Explanation:_ Establishing equivalence requires checking every possible assignment. Agreement on only part of the truth table leaves open the possibility that the expressions behave differently elsewhere.

**Q3** (MCQ, CONCEPTUAL): A program guard evaluates to true for some input assignments and false for others. Which classification best describes its proposition?

- A. Tautology
- B. Contradiction
- C. Logical equivalence
- D. Contingency ✅

_Explanation:_ The guard's output depends on its inputs: its truth-table result column contains both truth values rather than a uniform result.

**Q4** (MCQ, CONCEPTUAL): Statement 1: $p \lor q$ is true when both $p$ and $q$ are true. Statement 2: $p \leftrightarrow q$ is true when both $p$ and $q$ are false. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Inclusive disjunction permits simultaneous truth of its components. A biconditional checks whether the component truth values match, including a match where each is false.

**Q5** (MCQ, COMPUTATIONAL): How many rows are required in a complete truth table for a program condition containing $3$ distinct Boolean variables?

- A. $3$
- B. $6$
- C. $8$ ✅
- D. $9$

_Explanation:_ Use $2^n$ rows for $n$ distinct variables. Substituting $n = 3$ gives $2^3 = 8$, so the table requires $8$ rows.

---

## 3. Logical Equivalence and the Laws of Logic

| Field | Value |
|---|---|
| Note id | `45341aa4-ba24-4b65-a209-4b684c909a64` |
| Section in the proposed plan | Propositional Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Logical Equivalence and the Laws of Logic

📘 Overview
Logical equivalence means that two propositional expressions have identical truth values under every assignment of their variables. Truth tables verify equivalence by comparing final columns; laws of logic establish equivalence through valid symbolic rewrites.

🧠 Key Idea
The notation $P \equiv Q$ means that $P$ and $Q$ express the same truth conditions. Replacing an expression with a logically equivalent expression preserves the truth value of the entire formula.

⚔️ Core Details:
- De Morgan’s laws: $\neg(p \land q) \equiv \neg p \lor \neg q$ and $\neg(p \lor q) \equiv \neg p \land \neg q$.
- Implication law: $p \to q \equiv \neg p \lor q$.
- Commutative laws: $p \land q \equiv q \land p$ and $p \lor q \equiv q \lor p$.
- Associative laws: $(p \land q) \land r \equiv p \land (q \land r)$ and $(p \lor q) \lor r \equiv p \lor (q \lor r)$.
- Distributive laws: $p \land (q \lor r) \equiv (p \land q) \lor (p \land r)$ and $p \lor (q \land r) \equiv (p \lor q) \land (p \lor r)$.
- Absorption laws: $p \lor (p \land q) \equiv p$ and $p \land (p \lor q) \equiv p$.

🎯 Why It Matters:
- Equivalent rewrites simplify Boolean conditions in programs without changing their logical meaning.
- De Morgan’s laws support correct negation of compound conditions in assertions and test specifications.
- Symbolic equivalence supports proofs of correctness for Boolean circuit transformations.
- Truth tables can expose invalid rewrites, such as treating an implication as equivalent to its converse.

🧠 Quick Recall:
- Equivalence test — $P \equiv Q$ exactly when $P \leftrightarrow Q$ is a tautology.
- Tautology and contradiction — $\top$ is always true; $\bot$ is always false; $p \lor \neg p \equiv \top$ and $p \land \neg p \equiv \bot$.
- Double negation — $\neg\neg p \equiv p$.
- Identity laws — $p \land \top \equiv p$ and $p \lor \bot \equiv p$.
- Contraposition — $p \to q \equiv \neg q \to \neg p$.
```

### Study Pack

**Summary:** Logical equivalence means that two propositional expressions have identical truth values under every assignment of their variables. The notation $P \equiv Q$ holds exactly when $P \leftrightarrow Q$ is a tautology. Truth tables verify equivalence by comparing final columns, while logical laws establish it through valid symbolic rewrites.

Equivalent substitution preserves a formula’s truth value, supporting Boolean condition simplification, assertion negation, and proofs of correctness for Boolean circuit transformations.

| Law | What changes | What is preserved |
|---|---|---|
| Commutative | Order of operands | Logical meaning |
| Associative | Grouping of repeated conjunctions or disjunctions | Logical meaning |
| Distributive | Structure of conjunctions and disjunctions | Logical meaning |

De Morgan’s laws negate compound conditions: $\neg(p \land q) \equiv \neg p \lor \neg q$ and $\neg(p \lor q) \equiv \neg p \land \neg q$. Implication can be rewritten as $p \to q \equiv \neg p \lor q$ and is equivalent to its contrapositive, $\neg q \to \neg p$.

Absorption removes redundant structure: $p \lor (p \land q) \equiv p$ and $p \land (p \lor q) \equiv p$. Double negation gives $\neg\neg p \equiv p$. Identity laws give $p \land \top \equiv p$ and $p \lor \bot \equiv p$. A tautology is always true, while a contradiction is always false: $p \lor \neg p \equiv \top$ and $p \land \neg p \equiv \bot$.

**Common Misconceptions**: Agreement on some truth assignments does not establish equivalence. An implication is not generally equivalent to its converse. Negating a conjunction or disjunction requires changing the connective as well as negating its operands.

**Key concepts:** Logical equivalence; Truth tables; De Morgan’s laws; Implication law; Commutative laws; Associative laws; Distributive laws; Absorption laws; Contraposition; Tautologies and contradictions

**Q1** (MCQ, CONCEPTUAL): When checking two Boolean conditions with a truth table, what establishes their logical equivalence?

- A. Their final columns agree on at least one assignment.
- B. Their final columns agree on every assignment. ✅
- C. Each final column contains at least one true result.
- D. Their final columns contain equal numbers of true results.

_Explanation:_ Equivalence requires the conditions to produce the same result for every possible combination of input truth values. Partial agreement is insufficient.

**Q2** (MCQ, COMPUTATIONAL): Simplify the Boolean expression $\neg\neg(p \lor (p \land q))$ using double negation and absorption.

- A. $q$
- B. $\neg p$
- C. $p \land q$
- D. $p$ ✅

_Explanation:_ First remove the two outer negations. Then apply absorption: the conjunction nested inside the disjunction adds no additional cases in which the expression is true.

**Q3** (MCQ, CONCEPTUAL): Which claim about rewriting an implication in a Boolean specification is NOT generally correct?

- A. Replacing it with its converse preserves logical meaning. ✅
- B. Replacing it with its contrapositive preserves logical meaning.
- C. It can be expressed as a disjunction with the antecedent negated.
- D. A truth table can check whether a proposed rewrite preserves logical meaning.

_Explanation:_ Reversing the direction of an implication can change its truth value for some assignments. A valid equivalence must preserve the result for every assignment.

**Q4** (MCQ, CONCEPTUAL): Which best describes how De Morgan’s laws support the negation of a compound program condition?

- A. They reorder operands while retaining the original connective.
- B. They remove a repeated condition from a nested expression.
- C. They negate each operand and interchange conjunction and disjunction. ✅
- D. They regroup repeated connectives without changing operand order.

_Explanation:_ The outer negation must be distributed to both component conditions, and the connective must change so that the transformed condition has the required opposite truth value.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A tautology evaluates to true under every truth assignment. Statement 2: A contradiction evaluates to false under every truth assignment. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ These definitions classify expressions by their behavior across the entire truth table: one has uniformly true results, while the other has uniformly false results.

---

## 4. Conditional, Biconditional, Converse and Contrapositive

| Field | Value |
|---|---|
| Note id | `e8db8246-dc06-4e4e-9af6-ad19d6b3cf5b` |
| Section in the proposed plan | Propositional Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Conditional, Biconditional, Converse and Contrapositive

📘 Overview
A conditional asserts that one proposition implies another; a biconditional asserts implication in both directions. For propositions $p$ and $q$, reversing an implication produces its converse, while reversing and negating both propositions produces its logically equivalent contrapositive.

🧠 Key Idea
The conditional $p \to q$ is equivalent to its contrapositive $\neg q \to \neg p$, but not generally to its converse $q \to p$. A biconditional $p \leftrightarrow q$ requires both the conditional and its converse to hold.

⚔️ Core Details:
- The conditional $p \to q$ is false only when $p$ is true and $q$ is false.
- Material implication satisfies $p \to q \equiv \neg p \lor q$.
- The biconditional satisfies $p \leftrightarrow q \equiv (p \to q) \land (q \to p)$.
- The converse of $p \to q$ is $q \to p$.
- Contraposition gives the equivalence $(p \to q) \equiv (\neg q \to \neg p)$.
- Negating a conditional gives $\neg(p \to q) \equiv p \land \neg q$.

🎯 Why It Matters:
- Program specifications distinguish one-way guarantees from exact characterizations: an implication states a guarantee, while a biconditional also excludes other possibilities.
- Contraposition supports correctness proofs when showing that failure of a required result rules out the precondition is easier than proving the implication directly.
- Testing an implication requires searching for a true antecedent paired with a false consequent, rather than treating a false antecedent as a failure.
- Confusing a converse with the original implication can produce invalid reasoning about software behavior, such as assuming every sorted array must have been produced by a particular sorting algorithm.

🧠 Quick Recall:
- Antecedent and consequent — In $p \to q$, $p$ is the antecedent and $q$ is the consequent; $p$ is sufficient for $q$, and $q$ is necessary for $p$.
- Biconditional — $p \leftrightarrow q$ means “$p$ if and only if $q$”; it is true exactly when both propositions have the same truth value.
- Converse — $q \to p$; it is not generally equivalent to $p \to q$.
- Contrapositive — $\neg q \to \neg p$; it is always logically equivalent to $p \to q$.
- Vacuous truth — A material conditional is true whenever its antecedent is false, regardless of its consequent.
```

### Study Pack

**Summary:** Propositional logic distinguishes one-way guarantees from exact characterizations in program specifications. In $p \to q$, $p$ is the antecedent and $q$ is the consequent: $p$ is sufficient for $q$, while $q$ is necessary for $p$.

| Form | Expression | Meaning |
|---|---|---|
| Conditional | $p \to q$ | False only when $p$ is true and $q$ is false |
| Biconditional | $p \leftrightarrow q$ | True exactly when both propositions have the same truth value |
| Converse | $q \to p$ | Reverses the original implication |
| Contrapositive | $\neg q \to \neg p$ | Reverses and negates both propositions; equivalent to the original implication |

Material implication satisfies $p \to q \equiv \neg p \lor q$. A false antecedent makes the conditional true regardless of the consequent; this is vacuous truth. Negating a conditional gives $\neg(p \to q) \equiv p \land \neg q$, identifying exactly the counterexample a test must find.

A biconditional requires both directions: $p \leftrightarrow q \equiv (p \to q) \land (q \to p)$. Contraposition supports correctness proofs by showing that failure of the required result rules out the precondition.

**Common Misconceptions**: A converse is not generally equivalent to the original conditional. A false antecedent does not falsify a material implication. A one-way program guarantee does not establish an exact characterization.

**Key concepts:** Material implication; Antecedent and consequent; Necessary conditions; Sufficient conditions; Biconditional; Converse; Contrapositive; Vacuous truth; Conditional negation

**Q1** (MCQ, CONCEPTUAL): A program specification states: If the input satisfies the precondition, the output satisfies the postcondition. Which test result falsifies this guarantee?

- A. The precondition holds and the postcondition holds.
- B. The precondition fails and the postcondition holds.
- C. The precondition holds and the postcondition fails. ✅
- D. The precondition fails and the postcondition fails.

_Explanation:_ A conditional is violated only when its triggering requirement is met but the promised result is absent. Tests outside the precondition do not refute this implication.

**Q2** (MCQ, CONCEPTUAL): Which best describes a biconditional in a program specification?

- A. It establishes implications in both directions between two conditions. ✅
- B. It guarantees a result without requiring the reverse implication.
- C. It requires the two conditions to have different truth values.
- D. It establishes only that the two conditions can both be true.

_Explanation:_ An exact characterization requires each condition to be necessary and sufficient for the other. Consequently, the propositions must agree in truth value.

**Q3** (MCQ, CONCEPTUAL): A sorting routine guarantees that if it successfully processes an array, the resulting array is sorted. Which conclusion is NOT justified by this guarantee alone?

- A. Successful processing is sufficient for the resulting array to be sorted.
- B. A sorted result is necessary for successful processing.
- C. An unsorted result rules out successful processing.
- D. Every sorted array must have been successfully processed by this routine. ✅

_Explanation:_ Reversing a one-way guarantee introduces an additional claim that has not been established. Other routines or an already sorted input could account for the observed property.

**Q4** (MCQ, CONCEPTUAL): Statement 1: A conditional is logically equivalent to its contrapositive. Statement 2: A material conditional is true whenever its antecedent is false. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Reversing and negating both propositions preserves the implication's truth conditions. Also, implication excludes only the case of a true antecedent paired with a false consequent, so a false antecedent cannot violate it.

**Q5** (MCQ, CONCEPTUAL): What does the negation of the claim “If the precondition holds, the program produces the required result” assert?

- A. The precondition fails but the required result is produced.
- B. The precondition holds but the required result is not produced. ✅
- C. The precondition fails and the required result is not produced.
- D. The precondition holds and the required result is produced.

_Explanation:_ Negation identifies precisely the situation excluded by the original guarantee. In logical notation, $\neg(p \to q) \equiv p \land \neg q$.

---

## 5. Predicates and Quantifiers

| Field | Value |
|---|---|
| Note id | `6621091a-1f26-460b-81f5-054c50f47963` |
| Section in the proposed plan | Predicate Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Predicates and Quantifiers

📘 Overview
Predicates describe properties or relationships whose truth depends on their inputs. Quantifiers turn these conditions into claims about all or some elements of a specified domain, forming the basis of precise software specifications and logical reasoning.

🧠 Key Idea
A quantified statement is determined by its predicate, domain, and quantifier scope. Changing the domain or the order of different quantifiers can change the statement's truth.

⚔️ Core Details:
- A predicate such as $P(x)$ becomes a proposition when its free variables receive values or are bound by quantifiers.
- Universal quantification, $\forall x \in D\, P(x)$, asserts that every element of the domain $D$ satisfies $P$.
- Existential quantification, $\exists x \in D\, P(x)$, asserts that at least one element of the domain $D$ satisfies $P$.
- A variable occurrence is bound when it lies within the scope of a quantifier for that variable.
- A closed formula has no free variables.
- In $\forall x\,\exists y\,R(x,y)$, the choice of $y$ may depend on $x$.

🎯 Why It Matters:
- Software specifications use quantifiers to express requirements such as every request receiving a response or some execution reaching an error state.
- A single counterexample refutes a universal claim; a single witness establishes an existential claim.
- Quantifier order distinguishes a separate valid output for each input from one output that works for every input.
- Explicit domains prevent ambiguity about whether claims concern users, graph vertices, program states, or other computing objects.

🧠 Quick Recall:
- Universal negation — $\neg(\forall x\,P(x)) \equiv \exists x\,\neg P(x)$.
- Existential negation — $\neg(\exists x\,P(x)) \equiv \forall x\,\neg P(x)$.
- Uniform witness — $\exists y\,\forall x\,R(x,y)$ requires one fixed $y$ that works for every $x$.
- Restricted universal claim — $\forall x\,(A(x)\rightarrow B(x))$ means every object satisfying $A$ also satisfies $B$.
- Restricted existential claim — $\exists x\,(A(x)\land B(x))$ means some object satisfies both $A$ and $B$.
```

### Study Pack

**Summary:** Predicates express properties or relationships whose truth depends on their inputs. A predicate becomes a proposition when its free variables receive values or are bound by quantifiers. Quantified statements depend on the predicate, the specified domain, and quantifier scope.

| Quantifier | Meaning | Evidence |
|---|---|---|
| Universal: $\forall x\in D\,P(x)$ | Every domain element satisfies the predicate | One counterexample refutes it |
| Existential: $\exists x\in D\,P(x)$ | At least one domain element satisfies the predicate | One witness establishes it |

A variable occurrence is bound when it is within the scope of a quantifier for that variable. A closed formula contains no free variables. Explicit domains clarify whether statements concern requests, users, program states, or other computing objects.

Quantifier order determines dependencies. In $\forall x\,\exists y\,R(x,y)$, the choice of $y$ may depend on $x$. In $\exists y\,\forall x\,R(x,y)$, one fixed $y$ must work for every $x$.

Negation switches the quantifier and negates the predicate: $\neg(\forall x\,P(x))\equiv\exists x\,\neg P(x)$ and $\neg(\exists x\,P(x))\equiv\forall x\,\neg P(x)$. Restricted universal claims use implication, $\forall x\,(A(x)\rightarrow B(x))$, while restricted existential claims use conjunction, $\exists x\,(A(x)\land B(x))$.

**Common Misconceptions**: Different quantifiers cannot generally be reordered without changing meaning. Refuting a universal claim requires only one counterexample, not failure for every element. Restricted universal and existential claims use different logical connectives.

**Key concepts:** Predicates; Universal Quantification; Existential Quantification; Free Variables; Bound Variables; Closed Formulas; Quantifier Order; Quantifier Negation; Restricted Quantification

**Q1** (MCQ, CONCEPTUAL): What makes a logical formula closed?

- A. It contains no free variables. ✅
- B. It contains only universal quantifiers.
- C. It is true for every program state.
- D. It contains exactly one predicate.

_Explanation:_ Every variable occurrence must be bound for the formula to express a complete claim without an unassigned input.

**Q2** (MCQ, CONCEPTUAL): A specification claims that every request receives a response. What evidence is sufficient to refute it?

- A. One request that receives a response
- B. One request that receives no response ✅
- C. One execution in which all requests receive responses
- D. One response shared by several requests

_Explanation:_ A universal requirement fails as soon as a domain element violates its predicate; checking every remaining request is unnecessary.

**Q3** (MCQ, CONCEPTUAL): Let $R(x,y)$ mean that output $y$ is valid for input $x$. Which best describes $\exists y\,\forall x\,R(x,y)$?

- A. Each input may require a different valid output.
- B. Some input has at least one valid output.
- C. Every output is valid for at least one input.
- D. One fixed output is valid for every input. ✅

_Explanation:_ The existential quantifier comes before the universal quantifier, so its witness cannot be chosen separately for each input.

**Q4** (MCQ, CONCEPTUAL): Which statement about negating quantified software requirements is NOT correct?

- A. Negating a universal claim produces an existential claim about failure.
- B. Negating an existential claim produces a universal claim about failure.
- C. Negating a quantified claim always preserves its quantifier. ✅
- D. The negation of every request receiving a response asserts that some request receives none.

_Explanation:_ Moving negation through a quantifier exchanges universal and existential quantification while negating the predicate.

**Q5** (MCQ, CONCEPTUAL): Let $A(x)$ mean that $x$ is an authenticated user and $B(x)$ mean that $x$ has access. Statement 1: $\forall x\,(A(x)\rightarrow B(x))$ says every authenticated user has access. Statement 2: $\exists x\,(A(x)\land B(x))$ says some authenticated user has access. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Implication restricts the universal requirement to objects satisfying the qualifying predicate. Conjunction requires an existential witness to satisfy both predicates.

---

## 6. Nested Quantifiers and Negation

| Field | Value |
|---|---|
| Note id | `a5ca3860-6aad-4bd8-a34b-67f0d2c42b56` |
| Section in the proposed plan | Predicate Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Nested Quantifiers and Negation

📘 Overview
Nested quantifiers express claims involving multiple variables, and their order determines which values may depend on earlier choices. Negating a quantified claim reverses every quantifier crossed and negates the predicate.

🧠 Key Idea
A universal quantifier followed by an existential quantifier allows a different witness for each input; reversing them requires one witness that works for every input. Negation exchanges universal and existential quantifiers without changing their order.

⚔️ Core Details:
- Let $U$ be users, $S$ be servers, and $P(u,s)$ mean that user $u$ can access server $s$.
- $\forall u\in U\,\exists s\in S\,P(u,s)$ means every user can access some server, potentially a different server for each user.
- $\exists s\in S\,\forall u\in U\,P(u,s)$ means one server is accessible to every user.
- Quantifiers of the same kind commute: $\forall x\,\forall y\,Q(x,y)\equiv\forall y\,\forall x\,Q(x,y)$, where $Q$ is a predicate; the analogous rule holds for two existential quantifiers.
- Negation moves inward by switching each crossed quantifier and preserving variable domains.
- $\neg(\forall u\in U\,\exists s\in S\,P(u,s))\equiv\exists u\in U\,\forall s\in S\,\neg P(u,s)$: some user cannot access any server.

🎯 Why It Matters:
- Quantifier order distinguishes per-input solutions from a single solution that must work for every input in algorithm specifications.
- Correct negation identifies the precise counterexample needed to refute a software requirement.
- Logical transformations support formal verification and the interpretation of database queries involving every, some, and none.

🧠 Quick Recall:
- Universal negation — $\neg(\forall x\in D\,P(x))\equiv\exists x\in D\,\neg P(x)$, where $D$ is the domain and $P$ is a predicate.
- Existential negation — $\neg(\exists x\in D\,P(x))\equiv\forall x\in D\,\neg P(x)$, where $D$ is the domain and $P$ is a predicate.
- Witness dependence — In $\forall x\,\exists y\,Q(x,y)$, the witness $y$ may depend on $x$; $Q$ is a predicate.
- Uniform witness — In $\exists y\,\forall x\,Q(x,y)$, one fixed $y$ must satisfy the predicate $Q$ for every $x$.
- Quantifier scope — The subformula governed by a quantifier; occurrences of its variable within that scope are bound unless a nested quantifier rebinds them.
```

### Study Pack

**Summary:** Nested quantifiers express requirements involving multiple variables. Their order determines whether a selected value may depend on an earlier input or must work uniformly for all inputs.

Let $U$ be users, $S$ be servers, and $P(u,s)$ mean that user $u$ can access server $s$.

| Quantified requirement | Interpretation |
|---|---|
| $\forall u\in U\,\exists s\in S\,P(u,s)$ | Every user can access some server; the server may vary by user. |
| $\exists s\in S\,\forall u\in U\,P(u,s)$ | One fixed server is accessible to every user. |

Consecutive quantifiers of the same kind commute. Mixed universal and existential quantifiers generally cannot be exchanged without changing meaning.

To move negation inward, switch every crossed universal quantifier to existential and every crossed existential quantifier to universal. Preserve quantifier order and variable domains, and negate the predicate. Thus, $\neg(\forall u\in U\,\exists s\in S\,P(u,s))\equiv\exists u\in U\,\forall s\in S\,\neg P(u,s)$. A counterexample to the original requirement is a user who cannot access any server.

A quantifier's scope is the subformula it governs. Its variable occurrences within that scope are bound unless a nested quantifier rebinds them. These ideas support algorithm specifications, formal verification, and database query interpretation.

**Common Misconceptions**: A server for each user does not necessarily mean one shared server. Negation switches quantifier kinds, not their order. Refuting a universal requirement requires a counterexample, not failure for every user.

**Key concepts:** Nested quantifiers; Quantifier order; Witness dependence; Uniform witness; Quantifier commutation; Quantifier negation; Counterexamples; Quantifier scope; Bound variables

**Q1** (MCQ, CONCEPTUAL): A software requirement states that every user can access some server. What does this allow?

- A. The selected server may vary from user to user. ✅
- B. A single selected server must serve every user.
- C. Every user must access every server.
- D. Only one user needs access to a server.

_Explanation:_ The existential selection occurs within the universal requirement, so it can depend on the user currently being considered.

**Q2** (MCQ, CONCEPTUAL): Which best describes a uniform witness in a server-access requirement?

- A. A server selected separately after each user is considered.
- B. A user who cannot access any available server.
- C. A fixed server selected to satisfy the requirement for every user. ✅
- D. A predicate whose truth is independent of server access.

_Explanation:_ Placing the existential quantifier before the universal quantifier makes its selection independent of which user is later considered.

**Q3** (MCQ, CONCEPTUAL): Which transformation is NOT guaranteed to preserve a quantified software requirement's meaning?

- A. Exchanging two consecutive universal quantifiers.
- B. Exchanging two consecutive existential quantifiers.
- C. Replacing a negated universal claim with an existential claim about the negated predicate.
- D. Exchanging adjacent universal and existential quantifiers. ✅

_Explanation:_ Mixed quantifier order controls whether a witness can depend on an earlier input. Changing that dependency can change the requirement.

**Q4** (MCQ, CONCEPTUAL): What counterexample refutes the requirement that every user can access at least one server?

- A. A user who cannot access one particular server but can access another.
- B. A user who cannot access any server. ✅
- C. A server that is inaccessible to one user but accessible to another.
- D. Two users who access different servers.

_Explanation:_ Failure of a universal claim requires one failing input. For that input, the existential access condition must fail across the entire server domain.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A quantifier's scope is the subformula it governs. Statement 2: Moving negation inward through nested quantifiers reverses their order. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Scope determines where variable binding applies. Negation switches each crossed quantifier's kind while preserving its position and variable domain.

---

## 7. Translating Between English and Logic

| Field | Value |
|---|---|
| Note id | `768897ae-28e9-4304-8f64-734852e2b5f8` |
| Section in the proposed plan | Predicate Logic |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Translating Between English and Logic

📘 Overview
Accurate translation preserves a statement's truth conditions, especially implication direction and quantifier scope. Propositional logic connects complete statements, while predicate logic expresses properties and relationships over a specified domain.

🧠 Key Idea
Translate the logical structure rather than the English word order. Conditional statements distinguish sufficient from necessary conditions, and quantified statements depend on both their domain and their scope.

⚔️ Core Details:
- “If the request is authorized, access is granted” becomes $A \to G$, where $A$ means authorized and $G$ means access granted.
- “Access is granted only if the request is authorized” becomes $G \to A$: authorization is necessary for access.
- “Access is granted if and only if the request is authorized” becomes $G \leftrightarrow A$, expressing both conditional directions.
- “Every file is encrypted” becomes $\forall x\,(F(x) \to E(x))$, where $F(x)$ means that $x$ is a file and $E(x)$ means that $x$ is encrypted.
- “Some file is encrypted” becomes $\exists x\,(F(x) \land E(x))$, where $F(x)$ means that $x$ is a file and $E(x)$ means that $x$ is encrypted.
- “Not every process terminates” becomes $\exists x\,\neg T(x)$ over the domain of processes, where $T(x)$ means that $x$ terminates.

🎯 Why It Matters:
- Precise translations expose reversed implications in access-control policies and software requirements.
- Formal statements support automated verification of program behavior and system specifications.
- Correct quantifier scope distinguishes one shared resource from separate resources assigned to individual processes.

🧠 Quick Recall:
- Basic connectives — $P \land Q$ means both statements hold; $P \lor Q$ means at least one holds, possibly both; $\neg P$ means that $P$ is false.
- Material implication — $P \to Q$ is equivalent to $\neg P \lor Q$; it is false only when $P$ is true and $Q$ is false.
- Unless — “The request fails unless it is authenticated” becomes $\neg A \to F$, where $A$ means authenticated and $F$ means failure; this does not guarantee authenticated requests succeed.
- Quantifier negation — $\neg\forall x\,P(x) \equiv \exists x\,\neg P(x)$ and $\neg\exists x\,P(x) \equiv \forall x\,\neg P(x)$, where $P$ is a predicate.
- Quantifier order — $\forall p\exists r\,U(p,r)$ permits different resources per process; $\exists r\forall p\,U(p,r)$ requires a shared resource. Here $U(p,r)$ means process $p$ uses resource $r$.
```

### Study Pack

**Summary:** Accurate translation preserves truth conditions, implication direction, and quantifier scope rather than English word order. Propositional logic connects complete statements; predicate logic describes properties and relationships over a specified domain. These translations help formalize access-control policies, software requirements, and program verification.

Let $A$ mean a request is authorized and $G$ mean access is granted.

| English statement | Logical form | Meaning |
|---|---|---|
| If authorized, access is granted | $A \to G$ | Authorization is sufficient for access. |
| Access is granted only if authorized | $G \to A$ | Authorization is necessary for access. |
| Access is granted if and only if authorized | $G \leftrightarrow A$ | Both conditional directions hold. |

Conjunction requires both statements; inclusive disjunction requires at least one, possibly both. Material implication $P \to Q$ is equivalent to $\neg P \lor Q$ and is false only when $P$ is true and $Q$ is false.

With $F(x)$ meaning file and $E(x)$ meaning encrypted, “Every file is encrypted” becomes $\forall x\,(F(x) \to E(x))$, while “Some file is encrypted” becomes $\exists x\,(F(x) \land E(x))$.

Quantifier negation switches the quantifier and negates the predicate: $\neg\forall x\,P(x) \equiv \exists x\,\neg P(x)$ and $\neg\exists x\,P(x) \equiv \forall x\,\neg P(x)$. Over processes, “Not every process terminates” means $\exists x\,\neg T(x)$.

Quantifier order matters: $\forall p\exists r\,U(p,r)$ permits different resources for different processes; $\exists r\forall p\,U(p,r)$ requires a resource used by every process.

**Common Misconceptions**: “Only if” introduces a necessary condition, not a sufficient one. “Fails unless authenticated” does not guarantee authenticated requests succeed. “Not every” means at least one counterexample, not that none satisfy the predicate.

**Key concepts:** Truth Conditions; Necessary Conditions; Sufficient Conditions; Material Implication; Biconditional; Universal Quantification; Existential Quantification; Quantifier Negation; Quantifier Order; Unless Translation

**Q1** (MCQ, CONCEPTUAL): A policy states, “Access is granted only if the request is authorized.” What role does authorization play?

- A. A sufficient condition for access
- B. A necessary condition for access ✅
- C. A guarantee that access is denied
- D. A condition unrelated to access

_Explanation:_ The policy rules out granting access to an unauthorized request. It does not promise that every authorized request receives access.

**Q2** (MCQ, CONCEPTUAL): A requirement states, “The request fails unless it is authenticated.” Which conclusion is NOT justified?

- A. An unauthenticated request fails.
- B. A request that does not fail must be authenticated.
- C. An authenticated request is guaranteed to succeed. ✅
- D. Authentication and failure can occur together.

_Explanation:_ The requirement specifies what happens when authentication is absent. It leaves the outcome of an authenticated request unrestricted.

**Q3** (MCQ, CONCEPTUAL): Which best describes the difference between “Every process uses some resource” and “Some resource is used by every process”?

- A. The first requires one shared resource; the second permits separate resources.
- B. Both require all processes to use every resource.
- C. Both permit only separate resources for individual processes.
- D. The first permits separate resources; the second requires at least one shared resource. ✅

_Explanation:_ When resource selection falls inside the scope of each process, the selection can vary. When a resource is selected before considering all processes, that same resource must serve them all.

**Q4** (MCQ, CONCEPTUAL): Over the domain of processes, consider these statements. Statement 1: “Not every process terminates” means at least one process does not terminate. Statement 2: “No process terminates” means every process does not terminate. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Negating a universal claim produces an existential counterexample. Negating an existential claim produces a universal claim with the predicate negated.

**Q5** (MCQ, CONCEPTUAL): A specification states, “If the request is authorized, access is granted.” Which situation makes this implication false?

- A. The request is unauthorized and access is denied.
- B. The request is unauthorized and access is granted.
- C. The request is authorized and access is denied. ✅
- D. The request is authorized and access is granted.

_Explanation:_ A material implication fails precisely when its antecedent holds but its consequent does not. That combination violates the promise made by the specification.

---

## 8. Valid Arguments and Rules of Inference

| Field | Value |
|---|---|
| Note id | `e2861c9f-c355-42e9-87fa-bd9a324ebac6` |
| Section in the proposed plan | Rules of Inference and Arguments |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Valid Arguments and Rules of Inference

📘 Overview
An argument is valid when no truth assignment makes all its premises true and its conclusion false. Rules of inference provide truth-preserving steps for constructing proofs, including proofs used in program verification and logical reasoning systems.

🧠 Key Idea
Validity concerns whether the conclusion necessarily follows from the premises, not whether the premises are actually true. A formal proof establishes validity by deriving the conclusion through permitted inference rules.

⚔️ Core Details:
- Modus ponens: from $p$ and $p \to q$, infer $q$.
- Modus tollens: from $p \to q$ and $\neg q$, infer $\neg p$.
- Hypothetical syllogism: from $p \to q$ and $q \to r$, infer $p \to r$.
- Disjunctive syllogism: from $p \lor q$ and $\neg p$, infer $q$.
- Conjunction introduction: from $p$ and $q$, infer $p \land q$.
- Resolution: from $p \lor q$ and $\neg p \lor r$, infer $q \lor r$.

🎯 Why It Matters:
- Program verification uses inference rules to derive guarantees from explicitly stated assumptions.
- Resolution supports automated theorem proving and clause-based logical reasoning.
- Counterexamples expose invalid deductions in software specifications and computing-related arguments.
- Formal proofs make dependencies between assumptions and conclusions explicit and checkable.

🧠 Quick Recall:
- Validity test — An argument with premises $p_1,\ldots,p_n$ and conclusion $c$ is valid exactly when $(p_1 \land \cdots \land p_n) \to c$ is a tautology.
- Sound argument — A valid argument whose premises are all true.
- Counterexample to validity — A truth assignment making every premise true and the conclusion false.
- Simplification — From $p \land q$, infer $p$; either conjunct may be extracted.
- Conditional fallacies — Affirming the consequent infers $p$ from $p \to q$ and $q$; denying the antecedent infers $\neg q$ from $p \to q$ and $\neg p$. Neither inference is valid in general.
```

### Study Pack

**Summary:** Logical arguments support program verification, automated theorem proving, and checkable reasoning about software specifications. Validity concerns whether a conclusion necessarily follows from the premises, not whether those premises are actually true.

| Concept | Defining condition |
|---|---|
| Valid argument | No truth assignment makes every premise true and the conclusion false. |
| Sound argument | The argument is valid and every premise is true. |
| Counterexample to validity | A truth assignment makes every premise true and the conclusion false. |

An argument with premises $p_1,\ldots,p_n$ and conclusion $c$ is valid exactly when $(p_1 \land \cdots \land p_n) \to c$ is a tautology. Formal proofs derive conclusions through truth-preserving inference rules.

Modus ponens derives $q$ from $p$ and $p \to q$. Modus tollens derives $\neg p$ from $p \to q$ and $\neg q$. Hypothetical syllogism chains $p \to q$ and $q \to r$ to obtain $p \to r$. Disjunctive syllogism derives $q$ from $p \lor q$ and $\neg p$.

Conjunction introduction combines $p$ and $q$ into $p \land q$; simplification extracts either conjunct. Resolution derives $q \lor r$ from $p \lor q$ and $\neg p \lor r$, supporting clause-based automated reasoning.

**Common Misconceptions**: Validity does not guarantee true premises. Affirming the consequent and denying the antecedent are not valid inference rules.

**Key concepts:** Argument Validity; Sound Arguments; Counterexamples; Modus Ponens; Modus Tollens; Hypothetical Syllogism; Disjunctive Syllogism; Conjunction Introduction; Resolution; Conditional Fallacies

**Q1** (MCQ, CONCEPTUAL): Which best describes a valid argument used in reasoning about a software specification?

- A. Its premises are true, regardless of its conclusion.
- B. Its conclusion is true under at least one truth assignment.
- C. Its conclusion cannot be false when every premise is true. ✅
- D. Its conclusion is true independently of its premises.

_Explanation:_ Validity tests the logical connection between assumptions and a conclusion. A single assignment satisfying all assumptions while falsifying the conclusion would disprove validity.

**Q2** (MCQ, CONCEPTUAL): Assume that if a parser accepts an input, the input is well formed. The input is not well formed. Which rule justifies concluding that the parser does not accept it?

- A. Modus ponens
- B. Modus tollens ✅
- C. Hypothetical syllogism
- D. Conjunction introduction

_Explanation:_ The reasoning denies the consequent of a conditional and therefore denies its antecedent. This preserves truth whenever the stated premises hold.

**Q3** (MCQ, CONCEPTUAL): In a proof about software conditions, which deduction is NOT valid in general?

- A. From $p \land q$, infer $p$.
- B. From $p \lor q$ and $\neg p$, infer $q$.
- C. From $p \to q$ and $q \to r$, infer $p \to r$.
- D. From $p \to q$ and $q$, infer $p$. ✅

_Explanation:_ A conditional guarantees its consequent when its antecedent holds, but does not establish the reverse direction. The consequent can hold even when the antecedent is false.

**Q4** (MCQ, CONCEPTUAL): An automated theorem prover has clauses $p \lor q$ and $\neg p \lor r$. What does resolution infer by eliminating the complementary literals involving $p$?

- A. $q \lor r$ ✅
- B. $q \land r$
- C. $p \lor r$
- D. $\neg q \lor \neg r$

_Explanation:_ The complementary literals are removed, and the remaining literals are joined disjunctively. At least one remaining literal must hold for both original clauses to be satisfied.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A sound argument about a program is valid and has only true premises. Statement 2: A valid argument about a program must have only true premises. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Soundness adds the requirement of true premises to logical validity. Validity alone concerns whether the conclusion follows from the assumptions, even if those assumptions are not actually true.

---

## 9. Inference with Quantified Statements

| Field | Value |
|---|---|
| Note id | `021b67ab-d365-4ce3-9604-15537ed018b2` |
| Section in the proposed plan | Rules of Inference and Arguments |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Inference with Quantified Statements

📘 Overview
Inference with quantified statements derives conclusions about all objects or some objects in a specified domain. Valid reasoning depends on quantifier scope and restrictions on arbitrary objects and existential witnesses.

🧠 Key Idea
Universal statements apply to any object in the domain, while existential statements guarantee only a particular witness. A conclusion about every object requires an arbitrary object, not a witness chosen from an existential premise.

⚔️ Core Details:
- Universal instantiation: From $\forall x\,P(x)$, infer $P(t)$ for any domain term $t$ whose substitution does not cause variable capture.
- Universal generalization: From $P(c)$, infer $\forall x\,P(x)$ only when $c$ represents an arbitrary object and occurs in no undischarged assumption.
- Existential elimination: From $\exists x\,P(x)$, assume $P(c)$ locally for a fresh witness $c$. Any conclusion exported from this local proof must not mention $c$.
- Existential generalization: From $P(t)$, infer $\exists x\,P(x)$, where $t$ denotes an object in the domain.
- Quantified modus ponens: From $\forall x\,(P(x)\rightarrow Q(x))$ and $P(a)$, infer $Q(a)$ by universal instantiation followed by modus ponens.
- Quantifier negation: $\neg\forall x\,P(x)\equiv\exists x\,\neg P(x)$ and $\neg\exists x\,P(x)\equiv\forall x\,\neg P(x)$.

🎯 Why It Matters:
- Program verification uses universal premises to establish properties of particular inputs, states, and data-structure elements.
- Fresh-witness restrictions prevent proofs from incorrectly treating separate existence claims as claims about the same object.
- Negating a universal specification exposes the counterexample needed to refute it.
- Quantifier scope distinguishes one output that works for every input from outputs that may depend on individual inputs.

🧠 Quick Recall:
- Predicate — A statement containing variables that becomes true or false once its free variables receive values.
- Variable capture — Accidental binding of a previously free variable during substitution; valid substitution must avoid it.
- Arbitrary object — An object chosen without special assumptions; its properties can support universal generalization.
- Witness — An object satisfying an existential claim; it need not satisfy properties beyond those established by the premises.
- Quantifier order — $\forall x\exists y\,R(x,y)$ allows a different $y$ for each $x$; $\exists y\forall x\,R(x,y)$ requires one shared $y$.
```

### Study Pack

**Summary:** Quantified inference derives conclusions about all objects or some objects in a domain. In program verification, these rules support reasoning about inputs, states, and data-structure elements.

| Rule | Permitted inference | Essential restriction |
|---|---|---|
| Universal instantiation | Apply a universal property to a domain term | Substitution must avoid variable capture |
| Universal generalization | Extend a property of an arbitrary object to every object | The object must occur in no undischarged assumption |
| Existential elimination | Reason locally with a witness to an existence claim | The witness must be fresh and absent from the exported conclusion |
| Existential generalization | Infer existence from a property of a domain object | The term must denote an object in the domain |

Quantified modus ponens combines universal instantiation with modus ponens: from $\forall x\,(P(x)\rightarrow Q(x))$ and $P(a)$, infer $Q(a)$.

Quantifier negation gives $\neg\forall x\,P(x)\equiv\exists x\,\neg P(x)$ and $\neg\exists x\,P(x)\equiv\forall x\,\neg P(x)$. Thus, refuting a universal program specification requires a counterexample.

Quantifier order controls dependency: $\forall x\exists y\,R(x,y)$ permits an output to depend on its input, whereas $\exists y\forall x\,R(x,y)$ requires one output that works for every input.

**Common Misconceptions**: An existential witness is not an arbitrary object suitable for universal generalization. Separate existence claims need not share a witness. Reversing quantifier order can change a specification's meaning.

**Key concepts:** Universal instantiation; Universal generalization; Existential elimination; Existential generalization; Quantified modus ponens; Quantifier negation; Variable capture; Arbitrary objects; Existential witnesses; Quantifier order

**Q1** (MCQ, CONCEPTUAL): A verifier knows $\forall x\,(P(x)\rightarrow Q(x))$ and $P(a)$ for a particular program state $a$. What follows directly by quantified modus ponens?

- A. $\forall x\,Q(x)$
- B. $Q(a)$ ✅
- C. $\forall x\,P(x)$
- D. $\neg Q(a)$

_Explanation:_ Instantiate the universal implication at the specified state, then apply modus ponens using the established antecedent. This establishes the consequent for that state without extending it to every state.

**Q2** (MCQ, CONCEPTUAL): Which situation does NOT justify inferring $\forall x\,P(x)$ from $P(c)$ by universal generalization?

- A. The object was chosen without special assumptions.
- B. The object occurs in no undischarged assumption.
- C. The object was introduced solely as a witness to an existential premise. ✅
- D. The proof establishes the property without relying on special features of the object.

_Explanation:_ A proof of a universal claim must concern an arbitrary object. Knowing that some object satisfies a property does not establish that every object satisfies it.

**Q3** (MCQ, CONCEPTUAL): Statement 1: Existential elimination introduces a fresh witness in a local proof. Statement 2: A conclusion exported from that local proof may mention the witness. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The temporary name represents an unspecified object satisfying the existential premise. Keeping that name out of the exported conclusion prevents the proof from making an unsupported claim about a particular named object.

**Q4** (MCQ, CONCEPTUAL): For a program relation $R(x,y)$ between inputs and outputs, which best describes $\forall x\exists y\,R(x,y)$?

- A. One shared output must work for every input.
- B. At least one input must work with every output.
- C. Every input must work with every output.
- D. Each input has a suitable output, which may depend on that input. ✅

_Explanation:_ Because the existential quantifier lies within the universal quantifier's scope, its witness can be selected after the input is fixed. The specification does not require a single shared witness.

**Q5** (MCQ, CONCEPTUAL): A program specification states $\forall x\,P(x)$. Which statement expresses its negation?

- A. $\exists x\,\neg P(x)$ ✅
- B. $\forall x\,\neg P(x)$
- C. $\exists x\,P(x)$
- D. $\neg\exists x\,P(x)$

_Explanation:_ A universal specification fails when at least one input violates its required property. Refuting it requires a counterexample, not failure on every input.

---

## 10. Common Logical Fallacies

| Field | Value |
|---|---|
| Note id | `c908c48d-f0fb-4e13-804a-5d78fa033f1c` |
| Section in the proposed plan | Rules of Inference and Arguments |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Common Logical Fallacies

📘 Overview
A logical fallacy is an invalid inference: its premises can be true while its conclusion is false. In propositional logic, $p$ and $q$ represent propositions; in predicate logic, $P(x)$ represents a statement about an element $x$.

🧠 Key Idea
Logical validity depends on whether the premises guarantee the conclusion, not whether the conclusion happens to be true. A single counterexample with true premises and a false conclusion disproves an inference's validity.

⚔️ Core Details:
- Affirming the consequent is invalid: $p \to q,\ q\;\therefore p$.
- Denying the antecedent is invalid: $p \to q,\ \neg p\;\therefore \neg q$.
- Affirming a disjunct is invalid for inclusive disjunction: $p \lor q,\ p\;\therefore \neg q$.
- Reversing quantifier order is generally invalid: $\forall x\exists y\,R(x,y)$ does not imply $\exists y\forall x\,R(x,y)$; $R$ is a relation on the quantified domains.
- Negating a universal statement requires an existential counterexample: $\neg\forall x\,P(x) \equiv \exists x\,\neg P(x)$.
- Circular reasoning uses the conclusion, or an equivalent claim, as support for itself.

🎯 Why It Matters:
- Program verification requires valid inference from preconditions to postconditions; a plausible result does not establish correctness.
- Authentication success does not establish that a password was used when tokens or other credentials can also produce success.
- Quantifier order distinguishes a response chosen separately for each input from one response that must work for every input.

🧠 Quick Recall:
- Counterexample to validity — An assignment or interpretation making every premise true and the conclusion false.
- Modus ponens — Valid inference: $p \to q,\ p\;\therefore q$.
- Modus tollens — Valid inference: $p \to q,\ \neg q\;\therefore \neg p$.
- Converse and inverse — The converse $q \to p$ and inverse $\neg p \to \neg q$ are not generally equivalent to the original implication $p \to q$.
- Contrapositive — $\neg q \to \neg p$ is logically equivalent to $p \to q$.
```

### Study Pack

**Summary:** An inference is valid only when its premises guarantee its conclusion. A single assignment or interpretation with true premises and a false conclusion disproves validity. In program verification, a plausible postcondition does not establish that the reasoning from preconditions is valid.

Three common invalid inference patterns are:

| Fallacy | Invalid pattern |
|---|---|
| Affirming the consequent | $p \to q,\ q\;\therefore p$ |
| Denying the antecedent | $p \to q,\ \neg p\;\therefore \neg q$ |
| Affirming a disjunct | $p \lor q,\ p\;\therefore \neg q$ |

The third pattern is invalid for inclusive disjunction because both propositions may be true. By contrast, modus ponens and modus tollens are valid. An implication is equivalent to its contrapositive, but not generally to its converse or inverse.

Quantifier order matters: $\forall x\exists y\,R(x,y)$ allows a response to depend on each input, whereas $\exists y\forall x\,R(x,y)$ requires one response that works for every input. The former does not generally imply the latter. Negating a universal statement requires an existential counterexample: $\neg\forall x\,P(x) \equiv \exists x\,\neg P(x)$.

Circular reasoning uses the conclusion, or an equivalent claim, to support itself.

**Common Misconceptions**: A true conclusion does not establish a valid inference; inclusive disjunction permits both propositions to be true; a response for each input need not be one shared response for all inputs.

**Key concepts:** Inference validity; Counterexamples; Affirming the consequent; Denying the antecedent; Inclusive disjunction; Quantifier order; Universal negation; Circular reasoning; Contraposition; Valid inference rules

**Q1** (MCQ, CONCEPTUAL): A system accepts a valid password and then authenticates the user. A developer observes successful authentication and concludes that a password was used, although tokens can also authenticate users. Which best describes this reasoning?

- A. Modus ponens
- B. Affirming the consequent ✅
- C. Modus tollens
- D. Denying the antecedent

_Explanation:_ The developer infers the antecedent from the observed consequent. Because another credential can produce the same result, the premises do not guarantee the conclusion.

**Q2** (MCQ, CONCEPTUAL): What must a counterexample demonstrate to disprove an inference's validity?

- A. Every premise is true and the conclusion is false. ✅
- B. Every premise and the conclusion are true.
- C. At least one premise is false and the conclusion is true.
- D. Every premise and the conclusion are false.

_Explanation:_ Validity requires the conclusion to hold whenever all premises hold. One case that violates this guarantee is enough to refute validity.

**Q3** (MCQ, CONCEPTUAL): For inclusive disjunction, which claim about $p \lor q$ is NOT correct?

- A. It permits both propositions to be true.
- B. It holds whenever at least one proposition is true.
- C. Together with $p$, it guarantees $\neg q$. ✅
- D. It is false when both propositions are false.

_Explanation:_ Inclusive disjunction does not exclude simultaneous truth. Knowing that one disjunct holds therefore gives no basis for denying the other.

**Q4** (MCQ, CONCEPTUAL): Statement 1: $\forall x\exists y\,R(x,y)$ allows the response to depend on the input. Statement 2: This guarantees $\exists y\forall x\,R(x,y)$. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ A response may be selected separately for each input without any single response working for every input. Moving the existential quantifier ahead of the universal quantifier introduces a stronger requirement.

**Q5** (MCQ, CONCEPTUAL): A verification claim states that every input satisfies $P(x)$. What establishes the negation of this claim?

- A. Showing that every input fails to satisfy $P(x)$ is necessary.
- B. Finding an input that satisfies $P(x)$ is sufficient.
- C. Showing that the verification claim implies itself is sufficient.
- D. Finding an input that fails to satisfy $P(x)$ is sufficient. ✅

_Explanation:_ A universal claim requires the property to hold throughout its domain. A single counterexample defeats that requirement; it is unnecessary for the property to fail everywhere.

---

## 11. Sets and Set Notation

| Field | Value |
|---|---|
| Note id | `4e991264-1dd9-4770-bd06-ca15d69612d2` |
| Section in the proposed plan | Sets |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Sets and Set Notation

📘 Overview
A set is an unordered collection of distinct elements. In computing, sets represent collections such as valid inputs, database query results, and reachable program states.

🧠 Key Idea
Set notation expresses which objects belong to a collection and how collections relate or combine. Membership relates an element to a set; inclusion relates one set to another.

⚔️ Core Details:
- Roster notation lists elements explicitly: $A = \{1,2,3\}$; order and repetition do not affect the set.
- Set-builder notation specifies a membership condition: $E = \{n \in \mathbb{Z} \mid n \text{ is even}\}$, where $\mathbb{Z}$ is the set of integers.
- Subset notation: $A \subseteq B$ means every element of $A$ belongs to $B$.
- Union: $A \cup B = \{x \mid x \in A \text{ or } x \in B\}$, with inclusive “or.”
- Intersection: $A \cap B = \{x \mid x \in A \text{ and } x \in B\}$.
- Complement: $A^c = \{x \in U \mid x \notin A\}$, where $U$ is the specified universal set.

🎯 Why It Matters:
- Database queries use set operations to combine results, find shared records, and exclude unwanted records.
- Membership and subset distinctions prevent specification errors when describing valid inputs or permitted program states.
- Sets support precise reasoning about software behavior without depending on element order or duplicate occurrences.

🧠 Quick Recall:
- Membership — $x \in A$ means $x$ is an element of $A$; $x \notin A$ means it is not.
- Empty set and cardinality — $\varnothing$ contains no elements; $|A|$ denotes the number of elements in a finite set $A$.
- Equality and proper subset — $A = B$ means identical elements; $A \subsetneq B$ means $A \subseteq B$ and $A \ne B$.
- Power set — $\mathcal{P}(A) = \{B \mid B \subseteq A\}$; for finite $A$, $|\mathcal{P}(A)| = 2^{|A|}$.
- Set difference — $A \setminus B = \{x \mid x \in A \text{ and } x \notin B\}$.
```

### Study Pack

**Summary:** A set is an unordered collection of distinct elements. In computing, sets describe valid inputs, database query results, and reachable program states. Element order and repeated occurrences do not change a set.

Roster notation lists elements explicitly, as in $A = \{1,2,3\}$. Set-builder notation specifies a membership condition, as in $E = \{n \in \mathbb{Z} \mid n \text{ is even}\}$.

Membership, $x \in A$, relates an element to a set. Inclusion, $A \subseteq B$, means every element of $A$ belongs to $B$. Equal sets contain identical elements; a proper subset, $A \subsetneq B$, is included in another set but is not equal to it.

| Operation | Membership condition | Computing use |
|---|---|---|
| Union $A \cup B$ | In either set, including both | Combine query results |
| Intersection $A \cap B$ | In both sets | Find shared records |
| Difference $A \setminus B$ | In $A$ but not in $B$ | Exclude selected records |
| Complement $A^c$ | In the specified universe $U$ but not in $A$ | Identify excluded inputs within a domain |

The empty set, $\varnothing$, contains no elements. For a finite set, $|A|$ denotes its number of elements. The power set, $\mathcal{P}(A)$, contains every subset of $A$, and $|\mathcal{P}(A)| = 2^{|A|}$.

**Common Misconceptions** Membership and inclusion express different relationships. Union uses inclusive “or,” so shared elements are retained. A complement depends on the specified universal set.

**Key concepts:** Distinct Elements; Roster Notation; Set Builder Notation; Membership and Inclusion; Set Equality; Union and Intersection; Complement and Difference; Empty Set; Cardinality; Power Set

**Q1** (MCQ, CONCEPTUAL): A program specification uses $x \in A$. Which best describes this relationship?

- A. Every element of the set $x$ belongs to $A$.
- B. $x$ is an element of the set $A$. ✅
- C. $x$ and $A$ contain identical elements.
- D. $x$ is the collection of all subsets of $A$.

_Explanation:_ The symbol $\in$ expresses membership: it connects an individual object to a collection containing that object. Inclusion between sets uses $\subseteq$ instead.

**Q2** (MCQ, CONCEPTUAL): Which statement about a set of valid program inputs is NOT correct?

- A. Its elements can be listed using roster notation.
- B. Its elements can be specified using a membership condition.
- C. Reordering its listed elements produces a different set. ✅
- D. Repeating a listed element leaves the set unchanged.

_Explanation:_ A set is determined by which distinct objects it contains, not by their arrangement. This lets a specification describe permitted inputs without imposing an order.

**Q3** (MULTI_SELECT, CONCEPTUAL): Let $A$ and $B$ be sets of records returned by two database queries. Select the TWO correct descriptions.

- A. $A \cup B$ contains records returned by at least one query. ✅
- B. $A \cap B$ contains records returned by exactly one query.
- C. $A \setminus B$ contains records in $B$ but not in $A$.
- D. $A \cap B$ contains records returned by both queries. ✅

_Explanation:_ Union applies inclusive “or” to membership, while intersection requires simultaneous membership. These operations support combining query results and identifying their shared records.

**Q4** (MCQ, CONCEPTUAL): Statement 1: $A = B$ means the sets contain identical elements.
Statement 2: $A \subsetneq B$ permits $A = B$.
Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Set equality requires the same elements. Proper inclusion additionally requires inequality, so the smaller set cannot be identical to the containing set.

**Q5** (MCQ, COMPUTATIONAL): A set $A$ contains $3$ distinct permitted program states. How many subsets belong to $\mathcal{P}(A)$?

- A. $3$
- B. $6$
- C. $8$ ✅
- D. $9$

_Explanation:_ Use the finite power-set rule, $|\mathcal{P}(A)| = 2^{|A|}$. Substitute $|A| = 3$, then evaluate $2^3$ to obtain $8$.

---

## 12. Set Operations and Venn Diagrams

| Field | Value |
|---|---|
| Note id | `6c231725-6719-4274-84d9-9e6a228704b4` |
| Section in the proposed plan | Sets |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Set Operations and Venn Diagrams

📘 Overview
Set operations construct sets by combining or excluding elements according to membership rules. A Venn diagram represents sets as overlapping regions inside a universal set $U$; shading identifies the elements included in an operation's result.

🧠 Key Idea
An element belongs to a resulting set exactly when it satisfies the operation's membership condition. Union, intersection, and complement correspond to the Boolean operations OR, AND, and NOT.

⚔️ Core Details:
- Union: $A \cup B = \{x \mid x \in A \text{ or } x \in B\}$ uses inclusive OR.
- Intersection: $A \cap B = \{x \mid x \in A \text{ and } x \in B\}$ contains only shared elements.
- Difference: $A \setminus B = \{x \mid x \in A \text{ and } x \notin B\}$ retains elements of the first set outside the second.
- Complement: $A^c = U \setminus A$ contains all elements of the universal set $U$ outside $A$.
- Symmetric difference: $A \triangle B = (A \setminus B) \cup (B \setminus A)$ contains elements belonging to exactly one of the two sets.
- De Morgan's laws: $(A \cup B)^c = A^c \cap B^c$ and $(A \cap B)^c = A^c \cup B^c$ describe how complement exchanges union and intersection.

🎯 Why It Matters:
- Database UNION, INTERSECT, and EXCEPT implement set-style combinations of query results; duplicate handling depends on the operator and its options.
- Permission systems combine allowed users or resources through unions and restrict access through intersections and differences.
- De Morgan's laws support equivalent rewrites of Boolean conditions in programs and query filters.
- Venn diagrams expose omitted cases when checking membership conditions or comparing set expressions.

🧠 Quick Recall:
- Subset — $A \subseteq B$ means every element of $A$ belongs to $B$.
- Disjoint sets — $A \cap B = \varnothing$, where $\varnothing$ is the empty set.
- Venn shading — Union shades either set; intersection shades only the overlap; difference shades the first set outside the second.
- Difference and complement — $A \setminus B = A \cap B^c$, with the complement taken relative to $U$.
- Finite union size — $|A \cup B| = |A| + |B| - |A \cap B|$, where $|S|$ denotes the number of elements in a finite set $S$.
```

### Study Pack

**Summary:** Set operations combine or exclude elements according to membership conditions. Venn diagrams represent sets as regions inside a universal set $U$; shading shows which elements satisfy an operation.

| Operation | Membership condition | Venn shading |
|---|---|---|
| Union $A \cup B$ | In either set, including both | Both complete regions |
| Intersection $A \cap B$ | In both sets | Overlap only |
| Difference $A \setminus B$ | In $A$ but not $B$ | First region outside the second |
| Symmetric difference $A \triangle B$ | In exactly one set | Both regions excluding overlap |

The complement $A^c = U \setminus A$ contains elements of the universal set outside $A$. Union, intersection, and complement correspond to Boolean OR, AND, and NOT. Difference can also be written as $A \setminus B = A \cap B^c$.

De Morgan's laws exchange union and intersection under complementation: $(A \cup B)^c = A^c \cap B^c$ and $(A \cap B)^c = A^c \cup B^c$.

A subset satisfies $A \subseteq B$ when every element of $A$ belongs to $B$. Disjoint sets satisfy $A \cap B = \varnothing$. For finite sets, $|A \cup B| = |A| + |B| - |A \cap B|$ prevents double-counting shared elements.

In computing, these operations support database result combinations, permission rules, and equivalent Boolean filter rewrites. Database duplicate handling depends on the operator and its options.

**Common Misconceptions**: Union uses inclusive OR, so shared elements remain included. Complements depend on the chosen universal set. Reversing the operands of a difference can change the result.

**Key concepts:** Union; Intersection; Set difference; Complement; Symmetric difference; De Morgan's laws; Subsets; Disjoint sets; Finite set cardinality

**Q1** (MCQ, CONCEPTUAL): Two query-result sets are represented by $A$ and $B$. Which best describes membership in $A \cup B$?

- A. A record must occur in both result sets.
- B. A record must occur in at least one result set. ✅
- C. A record must occur in exactly one result set.
- D. A record must occur outside both result sets.

_Explanation:_ Union corresponds to inclusive Boolean OR. A record qualifies even when it is shared by the two sets.

**Q2** (MCQ, CONCEPTUAL): For arbitrary sets $A$ and $B$ within $U$, which statement about $A \setminus B$ is NOT generally correct?

- A. Every retained element belongs to $A$.
- B. No retained element belongs to $B$.
- C. It equals $A \cap B^c$.
- D. It equals $B \setminus A$. ✅

_Explanation:_ Difference is order-sensitive: it retains elements from the first operand while excluding members of the second. Reversing the operands can therefore change the result.

**Q3** (MCQ, CONCEPTUAL): Statement 1: Complementing a union gives the intersection of the complements. Statement 2: Complementing an intersection gives the intersection of the complements. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ De Morgan's laws swap AND and OR when negation is distributed. Thus, $(A \cup B)^c = A^c \cap B^c$, whereas $(A \cap B)^c = A^c \cup B^c$.

**Q4** (MCQ, CONCEPTUAL): An access audit must identify users belonging to exactly one of two permission groups $A$ and $B$. Which set expression selects these users?

- A. $A \triangle B$ ✅
- B. $A \cap B$
- C. $A \cup B$
- D. $(A \cup B)^c$

_Explanation:_ The required membership rule includes either group's exclusive members and excludes their overlap. This is represented by $(A \setminus B) \cup (B \setminus A)$.

**Q5** (MCQ, COMPUTATIONAL): Two sets of user IDs satisfy $|A| = 18$, $|B| = 12$, and $|A \cap B| = 5$. How many distinct user IDs belong to $A \cup B$?

- A. $30$
- B. $35$
- C. $25$ ✅
- D. $13$

_Explanation:_ Add the two set sizes, then subtract the overlap because shared IDs were counted twice: $18 + 12 = 30$, then $30 - 5 = 25$. The number of distinct IDs is $25$.

---

## 13. Set Identities and Membership Proofs

| Field | Value |
|---|---|
| Note id | `c4a020e3-82cc-405d-a732-4a3013c7a194` |
| Section in the proposed plan | Sets |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Set Identities and Membership Proofs

📘 Overview
Set identities describe equal sets using different combinations of union, intersection, and complement. Membership proofs establish these identities by translating set membership into logical statements about an arbitrary element.

🧠 Key Idea
Two sets are equal exactly when every element belongs to both or to neither. A membership proof therefore reduces set equality to a logical equivalence.

⚔️ Core Details:
- Equality criterion: $A=B \iff \forall x\,(x\in A\iff x\in B)$.
- Union membership: $x\in A\cup B\iff (x\in A\lor x\in B)$.
- Intersection membership: $x\in A\cap B\iff (x\in A\land x\in B)$.
- Complement membership relative to a fixed universal set $U$: $x\in A^c\iff (x\in U\land x\notin A)$.
- Membership proof of De Morgan's law, for arbitrary $x\in U$: $x\in(A\cup B)^c\iff\neg(x\in A\lor x\in B)\iff(x\notin A\land x\notin B)\iff x\in A^c\cap B^c$.
- Distributive law: $A\cap(B\cup C)=(A\cap B)\cup(A\cap C)$.

🎯 Why It Matters:
- Equivalent set expressions support the simplification of database queries and search filters without changing their results.
- Membership reasoning provides rigorous correctness arguments for programs that manipulate collections or access-control groups.
- Complement-based transformations require an explicit universe, such as all records in a database or all users in a system.

🧠 Quick Recall:
- De Morgan's other law — $(A\cap B)^c=A^c\cup B^c$.
- Absorption law — $A\cup(A\cap B)=A$.
- Set difference — $A\setminus B=A\cap B^c$.
- Double complement law — $(A^c)^c=A$.
- Subset criterion — $A\subseteq B\iff\forall x\,(x\in A\Rightarrow x\in B)$.
```

### Study Pack

**Summary:** Set identities express the same collection of elements in different ways. A membership proof establishes equality by choosing an arbitrary element and showing that membership in one expression is logically equivalent to membership in the other: $A=B\iff\forall x\,(x\in A\iff x\in B)$.

| Set operation | Membership condition |
|---|---|
| Union $A\cup B$ | $x\in A\lor x\in B$ |
| Intersection $A\cap B$ | $x\in A\land x\in B$ |
| Complement $A^c$ | $x\in U\land x\notin A$ |

With a fixed universe $U$, De Morgan's laws are $(A\cup B)^c=A^c\cap B^c$ and $(A\cap B)^c=A^c\cup B^c$. They follow by translating membership into logical statements and applying negation rules.

Other useful identities include distributivity, $A\cap(B\cup C)=(A\cap B)\cup(A\cap C)$; absorption, $A\cup(A\cap B)=A$; set difference, $A\setminus B=A\cap B^c$; and double complement, $(A^c)^c=A$. Subset inclusion requires only the implication $\forall x\,(x\in A\Rightarrow x\in B)$.

These transformations help simplify database queries, search filters, and collection-processing code without changing results. Membership arguments also support correctness reasoning for access-control groups.

**Common Misconceptions**: Union uses inclusive OR, not exclusive OR. Complements depend on a fixed universe. A one-way membership implication establishes inclusion, not necessarily equality.

**Key concepts:** Set Equality; Membership Proofs; Union; Intersection; Universal Set; De Morgan Laws; Distributive Law; Absorption Law; Set Difference; Subset Criterion

**Q1** (MCQ, CONCEPTUAL): Which best describes a membership proof that two set expressions are equal?

- A. Show that at least one element belongs to both expressions.
- B. Show that membership in either expression implies membership in the other for an arbitrary element. ✅
- C. Show that every element of the first expression belongs to the second, without checking the reverse.
- D. Show that both expressions use the same set-operation symbols.

_Explanation:_ Equality requires identical membership for every element. Using an arbitrary element and establishing both directions rules out an element belonging to just one of the sets.

**Q2** (MCQ, CONCEPTUAL): A search filter returns records in $A\cup B$. What condition must a returned record satisfy?

- A. It belongs to both sets.
- B. It belongs to neither set.
- C. It belongs to exactly one set.
- D. It belongs to at least one set. ✅

_Explanation:_ Union corresponds to inclusive logical OR, so a record is retained whenever either membership condition holds, including when both hold.

**Q3** (MCQ, CONCEPTUAL): For access-control groups contained in a fixed universe $U$ of system users, which claim about complements is NOT correct?

- A. Changing the universe can change a group's complement.
- B. A user in $A^c$ must belong to $U$.
- C. A group's complement is independent of the chosen universe. ✅
- D. Taking the complement twice restores the original group.

_Explanation:_ A complement includes only elements within the specified universe that are absent from the original set. Its membership therefore depends on which elements the universe contains.

**Q4** (MCQ, CONCEPTUAL): A database filter selects records outside both groups $A$ and $B$. By De Morgan's law, which expression equals $(A\cup B)^c$?

- A. $A^c\cap B^c$ ✅
- B. $A^c\cup B^c$
- C. $A\cap B$
- D. $A\cup B^c$

_Explanation:_ Negating an inclusive OR requires both membership conditions to fail. Translating those two failures back into set notation produces an intersection of complements.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Absorption gives $A\cup(A\cap B)=A$.
Statement 2: Proving $A\subseteq B$ alone is sufficient to establish $A=B$.
Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Intersecting with another set cannot introduce elements outside the original set, so the extra union term adds nothing. A subset proof establishes only one direction of inclusion; equality also requires the reverse direction.

---

## 14. Power Sets, Cartesian Products and Cardinality

| Field | Value |
|---|---|
| Note id | `a4d585f7-ef99-48db-98c4-cc5f29bd7fe0` |
| Section in the proposed plan | Sets |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Power Sets, Cartesian Products and Cardinality

📘 Overview
A power set contains every subset of a set, while a Cartesian product contains ordered pairs drawn from two sets. Cardinality measures set size and distinguishes finite, countably infinite, and uncountable sets.

🧠 Key Idea
Power sets represent all possible selections from one set; Cartesian products represent all ordered combinations across sets. For finite sets, power-set cardinality grows exponentially, while Cartesian-product cardinality is multiplicative.

⚔️ Core Details:
- The power set of $A$ is $\mathcal{P}(A)=\{S\mid S\subseteq A\}$.
- For a finite set $A$ with $|A|=n$, the number of subsets is $|\mathcal{P}(A)|=2^n$.
- The Cartesian product is $A\times B=\{(a,b)\mid a\in A,\ b\in B\}$.
- For finite sets, $|A\times B|=|A|\cdot|B|$.
- Sets have equal cardinality exactly when a bijection exists between them.
- Cantor’s theorem states that $|A|<|\mathcal{P}(A)|$ for every set $A$, including infinite sets.

🎯 Why It Matters:
- A permission system with $n$ independent permissions has $2^n$ possible permission sets, illustrating exponential configuration growth.
- A database cross join generates the Cartesian product of its input rows, explaining why intermediate results can become large.
- Finite binary strings are countable, but languages over those strings form an uncountable power set; therefore, some languages cannot be recognized by any program.

🧠 Quick Recall:
- Cardinality — $|A|$ denotes the size of set $A$; for finite sets, it is the number of distinct elements.
- Empty-set power set — $\mathcal{P}(\varnothing)=\{\varnothing\}$ and $|\mathcal{P}(\varnothing)|=1$.
- Empty-factor product — $A\times\varnothing=\varnothing\times A=\varnothing$.
- Ordered-pair equality — $(a,b)=(c,d)$ exactly when $a=c$ and $b=d$.
- Countably infinite — A set admitting a bijection with $\mathbb{N}$; an infinite set without such a bijection is uncountable.
```

### Study Pack

**Summary:** A power set collects every subset of a set: $\mathcal{P}(A)=\{S\mid S\subseteq A\}$. A Cartesian product collects ordered pairs: $A\times B=\{(a,b)\mid a\in A,\ b\in B\}$.

| Construction | Elements | Finite cardinality |
|---|---|---|
| Power set | Subsets of $A$ | $|\mathcal{P}(A)|=2^{|A|}$ |
| Cartesian product | Ordered pairs from $A$ and $B$ | $|A\times B|=|A|\cdot|B|$ |

Power sets model possible permission configurations; Cartesian products model database cross joins. Ordered pairs satisfy $(a,b)=(c,d)$ exactly when $a=c$ and $b=d$.

Cardinality describes set size. Two sets have equal cardinality exactly when a bijection exists between them. A countably infinite set admits a bijection with $\mathbb{N}$; an infinite set without such a bijection is uncountable. Cantor’s theorem states that $|A|<|\mathcal{P}(A)|$ for every set, including infinite sets. Finite binary strings form a countable set, but the collection of languages over those strings is uncountable. Consequently, some languages cannot be recognized by any program.

**Common Misconceptions**: The power set of the empty set is not empty: $\mathcal{P}(\varnothing)=\{\varnothing\}$. A Cartesian product with an empty factor is empty. Infinite sets do not all have equal cardinality.

**Key concepts:** Power set; Cartesian product; Finite cardinality; Bijections; Cantor’s theorem; Countable infinity; Uncountable sets; Ordered pair equality

**Q1** (MCQ, COMPUTATIONAL): A permission system has $4$ independent permissions. How many distinct permission sets are possible?

- A. $4$
- B. $8$
- C. $16$ ✅
- D. $24$

_Explanation:_ Each permission can be included or excluded. Apply the power-set formula: $2^4=16$. Therefore, the system has $16$ possible permission sets.

**Q2** (MCQ, CONCEPTUAL): Which best describes the result of a database cross join between two sets of rows?

- A. Every subset of the rows in the first input
- B. Every ordered pairing of a row from each input ✅
- C. Only rows that appear in both inputs
- D. A bijection between the two inputs

_Explanation:_ A cross join implements a Cartesian product: each row in the first input is combined with each row in the second input.

**Q3** (MCQ, CONCEPTUAL): Which statement about empty sets is NOT correct?

- A. The empty set is a subset of itself.
- B. The power set of the empty set contains one element.
- C. A Cartesian product with an empty factor is empty.
- D. The power set of the empty set has no elements. ✅

_Explanation:_ Even when a set has no elements, it still has the empty subset. Thus, $\mathcal{P}(\varnothing)=\{\varnothing\}$, which contains one element.

**Q4** (MCQ, CONCEPTUAL): What establishes that two sets have equal cardinality?

- A. A bijection between the sets ✅
- B. At least one shared element
- C. The same power set
- D. An ordered pair containing one element from each set

_Explanation:_ Matching each element of one set with exactly one element of the other, with no elements left unmatched, establishes equality of set size even for infinite sets.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Cantor’s theorem applies to infinite sets as well as finite sets. Statement 2: The collection of all languages over finite binary strings is countably infinite. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Cantor’s theorem guarantees that every power set has strictly greater cardinality than its underlying set. Finite binary strings are countable, but their languages form an uncountable power set.

---

## 15. Relations and Their Representations

| Field | Value |
|---|---|
| Note id | `17335256-d635-4b99-8eaf-2654198f13fc` |
| Section in the proposed plan | Relations |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Relations and Their Representations

📘 Overview
A binary relation specifies which ordered pairs connect elements of two sets. Finite relations can be represented by sets of ordered pairs, Boolean matrices, or directed graphs, making the same connections available for different computational tasks.

🧠 Key Idea
A relation is a set of ordered pairs; its representations encode the same membership information. A listed pair, a matrix entry of one, and a directed edge all indicate that a connection belongs to the relation.

⚔️ Core Details:
- A binary relation from $A$ to $B$ is any subset $R \subseteq A \times B$, where $A \times B = \{(a,b) \mid a \in A, b \in B\}$.
- Set-builder notation specifies related pairs through a condition, such as $R = \{(u,v) \in V \times V \mid u \text{ links directly to } v\}$ for a set of web pages $V$.
- For finite ordered sets $A$ and $B$, the Boolean matrix of $R$ has entry $M_{ij}=1$ exactly when $(a_i,b_j)\in R$, and $M_{ij}=0$ otherwise; rows represent $A$ and columns represent $B$.
- A directed graph represents a relation on $A$ using vertices for elements of $A$ and an edge $a \to b$ for each $(a,b)\in R$, including a loop when $(a,a)\in R$.
- The inverse relation $R^{-1}=\{(b,a)\mid(a,b)\in R\}$ is represented by the transposed matrix or by reversing every directed edge.
- For $R\subseteq A\times B$ and $S\subseteq B\times C$, composition is $S\circ R=\{(a,c)\mid\exists b\in B:\ (a,b)\in R\land(b,c)\in S\}$.

🎯 Why It Matters:
- Directed graphs expose dependency structure and reachable states in software systems.
- Boolean matrices support algorithmic relation composition using logical AND and OR instead of ordinary multiplication and addition.
- Binary relations model database associations without assuming the single-output restriction required of functions.

🧠 Quick Recall:
- Domain — $\operatorname{dom}(R)=\{a\in A\mid\exists b\in B:\ (a,b)\in R\}$.
- Range — $\operatorname{ran}(R)=\{b\in B\mid\exists a\in A:\ (a,b)\in R\}$.
- Reflexive — A relation on $A$ contains $(a,a)$ for every $a\in A$; its graph has a loop at every vertex.
- Symmetric — For all $a,b\in A$, $(a,b)\in R$ implies $(b,a)\in R$; its Boolean matrix equals its transpose.
- Transitive — For all $a,b,c\in A$, $(a,b)\in R$ and $(b,c)\in R$ imply $(a,c)\in R$.
```

### Study Pack

**Summary:** A binary relation from $A$ to $B$ is a subset $R \subseteq A \times B$. It identifies which ordered pairs are connected, without requiring the single-output restriction of a function. In computing, relations model web links, database associations, dependencies, and state transitions.

Finite relations can encode the same membership information in different forms:

| Representation | Membership encoding |
|---|---|
| Ordered pairs | $(a,b) \in R$ explicitly records a connection. |
| Boolean matrix | $M_{ij}=1$ records $(a_i,b_j) \in R$; otherwise $M_{ij}=0$. |
| Directed graph | An edge $a \to b$ records $(a,b) \in R$ for a relation on one set. |

Matrix rows correspond to the source set and columns to the target set. A graph includes a loop whenever an element is related to itself.

The inverse relation reverses every ordered pair. Its matrix is the transpose of the original matrix, and its graph reverses every edge. Composition $S \circ R$ connects a source to a destination when some intermediate element connects them through $R$ first and $S$ second. Boolean matrix composition uses logical AND and OR.

The domain contains elements that occur as first components of related pairs; the range contains those occurring as second components. A relation on $A$ is reflexive when every element relates to itself, symmetric when every connection has its reverse, and transitive when every two-step connection has a corresponding direct connection.

**Common Misconceptions**: A relation need not be a function. The domain need not contain every element of the source set. In $S \circ R$, apply $R$ before $S$.

**Key concepts:** Ordered pairs; Cartesian product; Boolean matrix; Directed graph; Inverse relation; Relation composition; Domain and range; Reflexivity; Symmetry; Transitivity

**Q1** (MCQ, CONCEPTUAL): A relation $R \subseteq A \times B$ models links from source pages to destination pages. What does $M_{ij}=1$ indicate in its Boolean matrix?

- A. The pages $a_i$ and $b_j$ must be identical.
- B. The ordered pair $(a_i,b_j)$ belongs to $R$. ✅
- C. The ordered pair $(a_i,b_j)$ does not belong to $R$.
- D. Every page in $A$ links to $b_j$.

_Explanation:_ A Boolean matrix encodes membership one entry at a time: the row identifies the source and the column identifies the destination.

**Q2** (MCQ, CONCEPTUAL): Which operation does NOT generally produce a representation of the inverse relation?

- A. Swapping the components of every ordered pair.
- B. Transposing the Boolean matrix.
- C. Reversing every directed edge.
- D. Replacing every zero matrix entry with one and every one with zero. ✅

_Explanation:_ Inversion exchanges the source and destination of existing connections. Changing membership bits instead describes absent connections rather than reversing existing ones.

**Q3** (MCQ, CONCEPTUAL): For $R \subseteq A \times B$ and $S \subseteq B \times C$, which best describes when $(a,c) \in S \circ R$?

- A. Some $b \in B$ satisfies $(a,b) \in R$ and $(b,c) \in S$. ✅
- B. Every $b \in B$ satisfies $(a,b) \in R$ and $(b,c) \in S$.
- C. The pair $(a,c)$ must belong to both $R$ and $S$.
- D. Some $b \in B$ satisfies $(a,b) \in R$ or $(b,c) \in S$, without requiring both.

_Explanation:_ Composition requires a complete two-step connection through at least one shared intermediate element. Both links must exist, with the first relation used before the second.

**Q4** (MCQ, CONCEPTUAL): Statement 1: A relation on $A$ is reflexive whenever its graph has a loop at at least one vertex.
Statement 2: A relation on $A$ is transitive when $(a,b) \in R$ and $(b,c) \in R$ always imply $(a,c) \in R$.
Which is correct?

- A. Both statements are correct.
- B. Only Statement 1 is correct.
- C. Only Statement 2 is correct. ✅
- D. Neither statement is correct.

_Explanation:_ Reflexivity requires self-connections for every element, so a single loop is insufficient in general. Transitivity requires every two-step connection to have a direct connection between its endpoints.

**Q5** (MCQ, CONCEPTUAL): A relation records direct links between web pages. Which pages belong to its domain?

- A. All pages in the source set, even if they have no outgoing links.
- B. Pages that appear as the source of at least one recorded link. ✅
- C. Pages that appear as the destination of at least one recorded link.
- D. Only pages with a recorded link to themselves.

_Explanation:_ Domain membership depends on appearing as the first component of an actual ordered pair. Merely belonging to the source set does not establish participation in the relation.

---

## 16. Properties of Relations

| Field | Value |
|---|---|
| Note id | `1c5d914e-02ce-4d2c-8c5a-9db57979d4fd` |
| Section in the proposed plan | Relations |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Properties of Relations

📘 Overview
A binary relation on a set $A$ is a subset $R \subseteq A \times A$, specifying which ordered pairs are related. Reflexivity, symmetry, antisymmetry, asymmetry, and transitivity describe structural constraints on those pairs.

🧠 Key Idea
A relation satisfies a property only when every instance required by its definition holds. Symmetry requires reversed pairs, whereas antisymmetry forbids reversed pairs between distinct elements.

⚔️ Core Details:
- Reflexive: $\forall a \in A,\ (a,a) \in R$.
- Irreflexive: $\forall a \in A,\ (a,a) \notin R$.
- Symmetric: $\forall a,b \in A,\ (a,b) \in R \Rightarrow (b,a) \in R$.
- Antisymmetric: $\forall a,b \in A,\ ((a,b) \in R \land (b,a) \in R) \Rightarrow a=b$.
- Asymmetric: $\forall a,b \in A,\ (a,b) \in R \Rightarrow (b,a) \notin R$.
- Transitive: $\forall a,b,c \in A,\ ((a,b) \in R \land (b,c) \in R) \Rightarrow (a,c) \in R$.

🎯 Why It Matters:
- Equivalence relations partition data into disjoint classes, supporting grouping and deduplication.
- Partial orders model dependency constraints in task scheduling without requiring every pair of tasks to be comparable.
- Relation properties support correctness checks for graph algorithms, database constraints, and access-control rules.

🧠 Quick Recall:
- Equivalence relation — A relation that is reflexive, symmetric, and transitive.
- Partial order — A relation that is reflexive, antisymmetric, and transitive.
- Total order — A partial order satisfying $\forall a,b \in A,\ (a,b) \in R \lor (b,a) \in R$.
- Asymmetry versus antisymmetry — Asymmetry forbids every reversed pair, including self-pairs; antisymmetry permits self-pairs.
- Transitivity counterexample — Elements $a,b,c \in A$ with $(a,b) \in R$ and $(b,c) \in R$, but $(a,c) \notin R$.
```

### Study Pack

**Summary:** A binary relation on a set $A$ is a subset $R \subseteq A \times A$. Its properties impose conditions on every applicable ordered pair, not merely selected examples.

Reflexivity requires every self-pair; irreflexivity excludes every self-pair. Symmetry requires the reverse of each related pair. Antisymmetry forbids mutual relationships between distinct elements but permits self-pairs. Asymmetry forbids all reversed pairs, including self-pairs. Transitivity requires $(a,c) \in R$ whenever $(a,b) \in R$ and $(b,c) \in R$.

| Classification | Required properties | Computing relevance |
|---|---|---|
| Equivalence relation | Reflexive, symmetric, transitive | Partitions data into disjoint classes for grouping and deduplication |
| Partial order | Reflexive, antisymmetric, transitive | Models dependency constraints without requiring all tasks to be comparable |
| Total order | Partial order with every pair comparable | Requires $(a,b) \in R$ or $(b,a) \in R$ for all $a,b \in A$ |

Relation properties support correctness checks for graph algorithms, database constraints, and access-control rules. A single counterexample is enough to disprove a property.

**Common Misconceptions** Antisymmetry is not the opposite of symmetry; a relation can satisfy both. Asymmetry excludes self-pairs, whereas antisymmetry permits them. A partial order need not compare every pair of elements.

**Key concepts:** Reflexivity; Irreflexivity; Symmetry; Antisymmetry; Asymmetry; Transitivity; Equivalence Relations; Partial Orders; Total Orders

**Q1** (MULTI_SELECT, CONCEPTUAL): A relation is used to partition data into equivalence classes. Which three properties must it satisfy? Select three.

- A. Reflexivity ✅
- B. Symmetry ✅
- C. Transitivity ✅
- D. Asymmetry

_Explanation:_ An equivalence relation must relate each element to itself, allow related pairs to be reversed, and preserve relationships across a two-step chain. Together, these requirements support partitioning a set into disjoint classes.

**Q2** (MCQ, CONCEPTUAL): Which situation is NOT permitted by antisymmetry?

- A. An element is related to itself.
- B. Two distinct elements are related in both directions. ✅
- C. Two distinct elements are related in only one direction.
- D. Two distinct elements are unrelated in either direction.

_Explanation:_ Antisymmetry requires $a=b$ whenever both $(a,b) \in R$ and $(b,a) \in R$. A mutual relationship therefore violates the property if the elements are distinct.

**Q3** (MCQ, CONCEPTUAL): Which best describes a counterexample to transitivity in a relation $R$ on $A$?

- A. There is an $a \in A$ with $(a,a) \notin R$.
- B. There are $a,b \in A$ with $(a,b) \in R$ but $(b,a) \notin R$.
- C. There are $a,b,c \in A$ with $(a,b),(b,c) \in R$ but $(a,c) \notin R$. ✅
- D. There are distinct $a,b \in A$ with $(a,b),(b,a) \in R$.

_Explanation:_ To disprove transitivity, find a two-step chain whose required direct connection is missing. One such failure is enough because the definition must hold for every applicable triple.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Every asymmetric relation is irreflexive. Statement 2: Every antisymmetric relation is irreflexive. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ For a self-pair, reversing the pair produces the same pair, so asymmetry rules it out. Antisymmetry permits self-pairs because their two elements are equal.

**Q5** (MCQ, CONCEPTUAL): A task relation is already a partial order. What additional requirement makes it a total order?

- A. Every related pair must also occur in reverse.
- B. Every self-pair must be excluded.
- C. Every pair of distinct tasks must remain incomparable.
- D. Every pair of tasks must be comparable in at least one direction. ✅

_Explanation:_ The additional condition is that for every $a,b \in A$, either $(a,b) \in R$ or $(b,a) \in R$. A partial order alone does not impose this condition.

---

## 17. Equivalence Relations and Partitions

| Field | Value |
|---|---|
| Note id | `0c49a755-d725-46e1-9061-5f4d482842f6` |
| Section in the proposed plan | Relations |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Equivalence Relations and Partitions

📘 Overview
An equivalence relation groups elements into nonempty, disjoint classes that together contain every element of the underlying set. Equivalence relations and partitions describe the same structure: each uniquely determines the other.

🧠 Key Idea
An equivalence relation formalizes when two elements count as equivalent. Its equivalence classes form a partition, so each element belongs to exactly one class.

⚔️ Core Details:
- Reflexivity: $aRa$ for every $a \in A$, where $R$ is a relation on the set $A$.
- Symmetry: $aRb \Rightarrow bRa$ for all $a,b \in A$.
- Transitivity: $(aRb \land bRc) \Rightarrow aRc$ for all $a,b,c \in A$.
- The equivalence class of $a$ is $[a]_R = \{x \in A : aRx\}$.
- The distinct equivalence classes of an equivalence relation form a partition of its underlying set.
- A partition $\mathcal{P}$ induces the equivalence relation $aRb \iff \exists B \in \mathcal{P}\;(a \in B \land b \in B)$.

🎯 Why It Matters:
- Quotient sets replace individual objects with classes of equivalent objects, reducing a problem's state space.
- Disjoint-set union data structures maintain partitions efficiently as equivalence groups merge.
- Canonicalization can assign one representative to each class, enabling consistent comparison and deduplication.

🧠 Quick Recall:
- Equivalence relation — A binary relation that is reflexive, symmetric, and transitive.
- Partition — A collection of nonempty, pairwise disjoint subsets whose union is the entire underlying set.
- Class equality test — $[a]_R = [b]_R \iff aRb$; distinct equivalence classes never overlap.
- Quotient set — $A/R = \{[a]_R : a \in A\}$, the set of all equivalence classes.
- String example — On finite strings, $sRt \iff |s| = |t|$, where $|s|$ denotes string length; each class contains strings of one fixed length.
```

### Study Pack

**Summary:** An equivalence relation is a binary relation that is reflexive, symmetric, and transitive. It groups elements into equivalence classes, with each element belonging to exactly one class.

| Structure | Description |
|---|---|
| Equivalence relation | Specifies which elements count as equivalent. |
| Partition | Divides a set into nonempty, pairwise disjoint subsets that cover the entire set. |
| Quotient set | Collects the distinct equivalence classes as its elements. |

For a relation $R$ on $A$, the equivalence class of $a$ is $[a]_R = \{x \in A : aRx\}$. Two classes satisfy $[a]_R = [b]_R \iff aRb$. The distinct classes form a partition. Conversely, any partition induces an equivalence relation by relating elements exactly when they belong to the same block.

The quotient set is $A/R = \{[a]_R : a \in A\}$. In computing, quotient sets can reduce a problem's state space, disjoint-set union structures maintain partitions as groups merge, and canonicalization assigns representatives for consistent comparison and deduplication. For finite strings, the relation $sRt \iff |s| = |t|$ groups strings by length.

**Common Misconceptions**: Distinct equivalence classes cannot overlap. A quotient set contains classes, not merely one chosen representative from each class. Reflexivity and symmetry alone do not establish an equivalence relation.

**Key concepts:** Reflexivity; Symmetry; Transitivity; Equivalence Classes; Partition Blocks; Class Equality; Quotient Sets; Canonical Representatives

**Q1** (MCQ, CONCEPTUAL): Which combination of properties defines an equivalence relation?

- A. Reflexivity, symmetry, and transitivity ✅
- B. Reflexivity and symmetry only
- C. Symmetry and transitivity only
- D. Reflexivity and transitivity only

_Explanation:_ Every element must relate to itself, every related pair must also be related in reverse, and successive related pairs must imply a relation between their endpoints. All three requirements are necessary.

**Q2** (MCQ, CONCEPTUAL): Which condition is NOT permitted in a partition of a set?

- A. A block contains multiple elements.
- B. Every element belongs to a block.
- C. Two distinct blocks share an element. ✅
- D. A block contains exactly one element.

_Explanation:_ Partition blocks must be pairwise disjoint, so each element has exactly one block membership. Blocks may otherwise differ in size, provided they are nonempty and together cover the underlying set.

**Q3** (MCQ, CONCEPTUAL): Which best describes the quotient set $A/R$?

- A. The set of ordered pairs belonging to the relation
- B. The set of all subsets of the underlying set
- C. The set of one chosen representative from each class
- D. The set of all distinct equivalence classes ✅

_Explanation:_ Its elements are themselves groups of equivalent objects. This allows computing problems to work with groups rather than distinguish every original object individually.

**Q4** (MCQ, CONCEPTUAL): Let $R$ be an equivalence relation. Statement 1: If $aRb$, then $[a]_R = [b]_R$. Statement 2: Two distinct equivalence classes may share an element. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Related elements determine the same class. If two classes share even one member, the equivalence properties force them to coincide, so genuinely different classes are disjoint.

**Q5** (MCQ, CONCEPTUAL): For finite strings with $sRt \iff |s| = |t|$, what determines membership in the equivalence class of a string?

- A. Having the same characters regardless of order
- B. Having the same first character
- C. Having the same number of characters ✅
- D. Having exactly the same character sequence

_Explanation:_ The defining relation compares only length. Character identities and their positions do not affect whether two strings are related.

---

## 18. Partial Orders

| Field | Value |
|---|---|
| Note id | `fa405f1c-276a-46ab-80d2-64a46d643a77` |
| Section in the proposed plan | Relations |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Partial Orders

📘 Overview
A partial order is a reflexive, antisymmetric, and transitive relation that need not compare every pair of elements. Partially ordered sets model relationships such as set inclusion, task dependencies, and information refinement.

🧠 Key Idea
A partial order represents consistent ordering without requiring every pair to be comparable. Distinguishing minimal elements from a least element is essential when interpreting such structures.

⚔️ Core Details:
- A poset $(P,\preceq)$ satisfies reflexivity $a\preceq a$, antisymmetry $(a\preceq b\land b\preceq a)\Rightarrow a=b$, and transitivity $(a\preceq b\land b\preceq c)\Rightarrow a\preceq c$ for all $a,b,c\in P$.
- A total order is a partial order in which every pair is comparable: $a\preceq b$ or $b\preceq a$ for all $a,b\in P$.
- Set inclusion gives a partial order on the power set $\mathcal{P}(S)$; distinct singleton subsets of $S$ are incomparable.
- A Hasse diagram depicts cover relations upward, omitting reflexive loops and edges implied by transitivity.
- A minimal element has no distinct element below it; a least element lies below every element of the poset.
- A lattice is a poset in which every pair of elements has both a least upper bound and a greatest lower bound.

🎯 Why It Matters:
- Dependency constraints permit multiple valid execution sequences rather than forcing one fixed sequence.
- Topological sorting produces a linear order consistent with a finite poset, supporting scheduling and build systems.
- Lattice structures support static program analysis by organizing abstract program states and their combinations.

🧠 Quick Recall:
- Cover relation — $y$ covers $x$ when $x\prec y$ and no $z$ satisfies $x\prec z\prec y$; $\prec$ denotes strict order.
- Upper and lower bounds — For $A\subseteq P$, an upper bound $u\in P$ satisfies $a\preceq u$ for every $a\in A$; a lower bound $l\in P$ satisfies $l\preceq a$ for every $a\in A$.
- Join and meet — $a\vee b$ is the least upper bound of $\{a,b\}$; $a\wedge b$ is its greatest lower bound.
- Maximal versus greatest — A maximal element has no distinct element above it; a greatest element lies above every element.
- Power-set lattice — Under inclusion, $A\vee B=A\cup B$ and $A\wedge B=A\cap B$.
```

### Study Pack

**Summary:** A partially ordered set, or poset, consists of a set $P$ and a relation $\preceq$ that is reflexive, antisymmetric, and transitive. Partial orders model task dependencies, set inclusion, and information refinement without requiring every pair of elements to be comparable.

| Order type | Comparability requirement |
|---|---|
| Partial order | Some pairs may be incomparable. |
| Total order | Every pair must be comparable. |

A Hasse diagram draws cover relations upward, omitting reflexive loops and edges implied by transitivity. An element $y$ covers $x$ when $x\prec y$ and no element lies strictly between them.

A minimal element has no distinct element below it; a least element lies below every element. Dually, a maximal element has no distinct element above it, while a greatest element lies above every element.

An upper bound lies above every element of a specified subset; a lower bound lies below every element of that subset. A lattice is a poset in which every pair has a least upper bound, called its join, and a greatest lower bound, called its meet. In the power-set lattice ordered by inclusion, $A\vee B=A\cup B$ and $A\wedge B=A\cap B$.

Topological sorting produces a linear order consistent with a finite poset, supporting dependency scheduling and build systems. Lattices organize abstract program states in static program analysis.

**Common Misconceptions**: A partial order need not compare every pair. Minimal does not mean least. A Hasse diagram does not display every related pair.

**Key concepts:** Partial orders; Total orders; Cover relations; Minimal and least; Maximal and greatest; Upper and lower bounds; Join and meet; Power set lattice; Topological sorting

**Q1** (MULTI_SELECT, CONCEPTUAL): Which properties are required of every partial order? Select all that apply.

- A. Reflexivity ✅
- B. Antisymmetry ✅
- C. Transitivity ✅
- D. Comparability of every pair

_Explanation:_ A partial order must relate each element to itself, identify mutually ordered elements as equal, and preserve ordering through chains. It may leave some pairs incomparable.

**Q2** (MCQ, CONCEPTUAL): Which feature is NOT part of a Hasse diagram's representation of a poset?

- A. Cover relations drawn upward
- B. Omission of reflexive loops
- C. An edge for every pair related by the strict order ✅
- D. Omission of edges implied by transitivity

_Explanation:_ Only immediate ordering relationships need edges. Relationships obtained by following a chain are already represented by the path and do not require additional edges.

**Q3** (MCQ, CONCEPTUAL): Which best describes the distinction between a minimal element and a least element?

- A. A minimal element lies below every element; a least element need not.
- B. A minimal element has no distinct element below it; a least element lies below every element. ✅
- C. A minimal element has no distinct element above it; a least element lies above every element.
- D. A minimal element and a least element are equivalent in every poset.

_Explanation:_ Minimality rules out a strictly smaller element but permits incomparability. Being least imposes a stronger requirement involving comparison with the entire poset.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Every pair in a lattice has both a least upper bound and a greatest lower bound. Statement 2: In the power-set lattice ordered by inclusion, the join is intersection. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The lattice definition requires both bounds for every pair. Under inclusion, union gives the smallest set containing both operands, so $A\vee B=A\cup B$; intersection instead gives $A\wedge B=A\cap B$.

**Q5** (MCQ, CONCEPTUAL): What does topological sorting produce when applied to a finite poset representing build dependencies?

- A. A unique execution sequence for every dependency structure
- B. A lattice of all possible abstract program states
- C. A diagram containing only cover relations
- D. A linear ordering that respects the dependency constraints ✅

_Explanation:_ Prerequisites must appear before the tasks that depend on them. Incomparable tasks can appear in different relative positions, so several valid results may exist.

---

## 19. Functions: Definition and Notation

| Field | Value |
|---|---|
| Note id | `85566327-9c2e-431a-9cf5-8bd84cf8513d` |
| Section in the proposed plan | Functions and Sequences |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Functions: Definition and Notation

📘 Overview
A function assigns exactly one output to every element of its domain. The notation $f: A \to B$ specifies the allowed inputs and the set containing all outputs, while $f(a)$ denotes the output assigned to input $a$.

🧠 Key Idea
A function must give every allowed input exactly one output, but different inputs may share an output. The codomain lists permitted outputs; the range contains only outputs actually produced.

⚔️ Core Details:
- Formally, a function $f: A \to B$ is a relation $f \subseteq A \times B$ satisfying $\forall a \in A,\ \exists! b \in B:\ (a,b) \in f$.
- The notation $a \mapsto f(a)$ describes the assignment of an input to its output.
- For $f: \mathbb{Z} \to \mathbb{Z}$ defined by $f(n)=n^2$, evaluating at $-3$ gives $f(-3)=9$.
- The range is $f(A)=\{f(a)\mid a\in A\}\subseteq B$.
- Two functions are equal exactly when they have the same domain, the same codomain, and identical outputs for every input.
- A partial function may be undefined on some inputs in its stated source set.

🎯 Why It Matters:
- Function signatures describe input and output contracts for software components.
- The distinction between codomain and range separates permitted results from results an implementation can actually produce.
- The unique-output requirement distinguishes mathematical functions from procedures whose results depend on hidden state or nondeterminism.

🧠 Quick Recall:
- Domain — $A$ in $f: A \to B$; the set of all allowed inputs.
- Codomain — $B$ in $f: A \to B$; the declared set containing every output.
- Range — $f(A)$; the set of outputs actually attained by the function.
- Function evaluation — $f(a)$ is the unique output corresponding to input $a$.
- Existence and uniqueness — $\exists!$ means there exists exactly one.
```

### Study Pack

**Summary:** A function assigns exactly one output to every element of its domain. Formally, $f: A \to B$ is a relation $f \subseteq A \times B$ satisfying $\forall a \in A,\ \exists! b \in B:\ (a,b) \in f$. The symbol $\exists!$ means there exists exactly one.

The notation $f(a)$ denotes the output for input $a$, while $a \mapsto f(a)$ describes the assignment.

| Set | Notation | Meaning |
|---|---|---|
| Domain | $A$ | All allowed inputs |
| Codomain | $B$ | Declared set containing every output |
| Range | $f(A)$ | Outputs actually attained |

The range satisfies $f(A)=\{f(a)\mid a\in A\}\subseteq B$. For $f: \mathbb{Z}\to\mathbb{Z}$ defined by $f(n)=n^2$, evaluation gives $f(-3)=9$.

Two functions are equal exactly when their domains, codomains, and outputs for every input agree. A partial function may be undefined on some inputs in its stated source set.

In computing, function signatures describe input and output contracts. The codomain specifies permitted results, whereas the range identifies results actually produced. Hidden state or nondeterminism can prevent a procedure from representing a mathematical function of its stated inputs alone.

**Common Misconceptions**: Different inputs may share an output. The range need not equal the codomain. A total function cannot leave a domain element without an output.

**Key concepts:** Domain; Codomain; Range; Function evaluation; Existence and uniqueness; Function equality; Partial functions

**Q1** (MCQ, CONCEPTUAL): What requirement must a relation satisfy to define a function $f: A \to B$?

- A. Every element of $A$ is paired with exactly one element of $B$. ✅
- B. Every element of $B$ is paired with exactly one element of $A$.
- C. Each element of $A$ may be paired with several elements of $B$.
- D. Some elements of $A$ may have no assigned output.

_Explanation:_ The definition requires both existence and uniqueness for each allowed input. It does not require every permitted output to be attained.

**Q2** (MCQ, CONCEPTUAL): For a software component modeled by $f: A \to B$, which best describes its range?

- A. The collection of inputs accepted by the component.
- B. The collection of all outputs permitted by its declared contract.
- C. The collection of outputs attained over all allowed inputs. ✅
- D. The collection of inputs on which a partial function is undefined.

_Explanation:_ An element belongs to $f(A)$ only if some input in $A$ maps to it. Declaring an element in $B$ does not guarantee that the component produces it.

**Q3** (MCQ, CONCEPTUAL): Which statement about function equality is NOT correct?

- A. Equal functions must have the same domain.
- B. Equal functions must agree on the output for every input.
- C. Equal functions must have the same codomain.
- D. Functions with identical domains and outputs are equal even if their codomains differ. ✅

_Explanation:_ Under the definition in the notes, a function's codomain is part of its specification. Agreement on inputs and assigned outputs alone is insufficient to establish equality.

**Q4** (MCQ, COMPUTATIONAL): Given $f: \mathbb{Z}\to\mathbb{Z}$ with $f(n)=n^2$, what is $f(-3)$?

- A. $-9$
- B. $9$ ✅
- C. $-6$
- D. $6$

_Explanation:_ Substitute the input into the rule: $f(-3)=(-3)^2=(-3)(-3)=9$. The result is $9$ because multiplying two negative integers gives a positive integer.

**Q5** (MCQ, CONCEPTUAL): Statement 1: A partial function may be undefined on some inputs in its stated source set. Statement 2: A function must assign different outputs to every pair of different inputs. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Partial functions relax the requirement that every source input receive an output. Output uniqueness applies to each individual input; it does not prevent separate inputs from sharing a result.

---

## 20. One-to-One, Onto and Bijective Functions

| Field | Value |
|---|---|
| Note id | `f3933743-e340-4348-95fb-dc82e1049c53` |
| Section in the proposed plan | Functions and Sequences |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
One-to-One, Onto and Bijective Functions

📘 Overview
Injectivity distinguishes inputs by their outputs, while surjectivity requires every codomain element to be reached. For a function $f:A\to B$, $A$ is the domain and $B$ is the codomain; satisfying both properties makes the function bijective.

🧠 Key Idea
A bijection pairs every domain element with exactly one codomain element, with no collisions or missing outputs. Exactly the bijective functions have inverse functions defined on their entire codomain.

⚔️ Core Details:
- Injective, or one-to-one: $\forall x,y\in A,\ f(x)=f(y)\Rightarrow x=y$.
- Surjective, or onto: $\forall b\in B,\ \exists a\in A\text{ such that }f(a)=b$.
- Bijective: every element of $B$ has exactly one preimage in $A$.
- A bijection has an inverse $f^{-1}:B\to A$ satisfying $f^{-1}(f(a))=a$ and $f(f^{-1}(b))=b$ for every $a\in A$ and $b\in B$.
- For finite sets, injectivity requires $|A|\leq|B|$, while surjectivity requires $|A|\geq|B|$; vertical bars denote set cardinality.
- For finite sets with $|A|=|B|$, injectivity, surjectivity, and bijectivity are equivalent.

🎯 Why It Matters:
- Injective encodings preserve distinct data values and prevent information loss through collisions.
- Surjectivity establishes that a mapping can generate every value permitted by its declared output set.
- Bijections support reversible data transformations and exact correspondences between computational representations.

🧠 Quick Recall:
- Image — $f(A)=\{f(a):a\in A\}$; surjectivity means $f(A)=B$.
- Collision — distinct inputs $x\ne y$ with $f(x)=f(y)$; one collision disproves injectivity.
- Unreached output — an element $b\in B$ with no preimage; one such element disproves surjectivity.
- Codomain dependence — $f(n)=2n$ is injective but not onto $\mathbb{Z}$ when $f:\mathbb{Z}\to\mathbb{Z}$; it is bijective when the codomain is the even integers.
- Parity mapping — $p:\mathbb{Z}\to\{0,1\}$ with $p(n)=n\bmod 2$ is surjective but not injective.
```

### Study Pack

**Summary:** For a function $f:A\to B$, $A$ is the domain and $B$ is the codomain. Injectivity distinguishes inputs by their outputs; surjectivity ensures that every declared output is reached.

| Property | Requirement | Computing significance |
|---|---|---|
| Injective | Distinct inputs have distinct outputs | Prevents encoding collisions |
| Surjective | Every codomain element has a preimage | Covers every permitted output |
| Bijective | Every codomain element has exactly one preimage | Supports reversible transformations |

The image is $f(A)=\{f(a):a\in A\}$. Surjectivity means $f(A)=B$. A collision disproves injectivity, while an unreached codomain element disproves surjectivity.

Exactly the bijective functions have inverse functions defined on their entire codomain. The inverse $f^{-1}:B\to A$ satisfies $f^{-1}(f(a))=a$ and $f(f^{-1}(b))=b$.

For finite sets, injectivity requires $|A|\leq|B|$, and surjectivity requires $|A|\geq|B|$. When $|A|=|B|$, injectivity, surjectivity, and bijectivity are equivalent.

The declared codomain matters: $f(n)=2n$ is injective but not surjective from $\mathbb{Z}$ to $\mathbb{Z}$, yet is bijective from $\mathbb{Z}$ to the even integers.

**Common Misconceptions**: Injectivity does not require reaching every codomain element. Equal finite cardinalities alone do not make a function bijective. Surjectivity depends on the declared codomain, not just the mapping rule.

**Key concepts:** Injective functions; Surjective functions; Bijective functions; Domain and codomain; Image; Collisions; Inverse functions; Finite cardinality; Codomain dependence

**Q1** (MCQ, CONCEPTUAL): An encoding maps two distinct input values to the same output. What does this establish?

- A. The encoding is not injective. ✅
- B. The encoding is not surjective.
- C. Every output has multiple preimages.
- D. Some codomain element is unreached.

_Explanation:_ A collision prevents the output from distinguishing the two inputs. One such collision is enough to violate the defining condition for injectivity.

**Q2** (MCQ, CONCEPTUAL): Which best describes a surjective mapping between computational representations?

- A. Every input can be recovered uniquely from its output.
- B. Distinct inputs always produce distinct outputs.
- C. Every value in the declared codomain is generated by some input. ✅
- D. The domain and codomain must be identical sets.

_Explanation:_ Surjectivity concerns complete coverage of the codomain. An output may have more than one preimage without violating this property.

**Q3** (MCQ, CONCEPTUAL): Which claim about a bijective data transformation is NOT correct?

- A. It has an inverse defined on its entire codomain.
- B. Some codomain elements may have no preimage. ✅
- C. Applying the inverse after the transformation recovers the original input.
- D. Each codomain element has exactly one preimage.

_Explanation:_ Bijectivity includes surjectivity, so no declared output can be left unreached. Together with injectivity, this guarantees unique recovery of inputs.

**Q4** (MCQ, CONCEPTUAL): The mapping $f(n)=2n$ initially has domain $\mathbb{Z}$ and codomain $\mathbb{Z}$. Its codomain is then changed to the even integers, with the domain and rule unchanged. What happens?

- A. It loses injectivity but gains surjectivity.
- B. It remains injective and remains non-surjective.
- C. It becomes surjective but cannot have an inverse.
- D. It remains injective and becomes bijective. ✅

_Explanation:_ Restricting the declared codomain to the even integers removes the previously unreached outputs. Each remaining output has a unique integer preimage, so the transformation is reversible.

**Q5** (MCQ, CONCEPTUAL): Let $f:A\to B$ map between finite sets with $|A|=|B|$. Statement 1: If $f$ is injective, it is surjective. Statement 2: Equal cardinalities alone guarantee that $f$ is bijective. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ For equally sized finite sets, assigning distinct outputs to all inputs necessarily exhausts the codomain. Equal set sizes alone do not prevent collisions, so an additional mapping property is still needed.

---

## 21. Composition and Inverse of Functions

| Field | Value |
|---|---|
| Note id | `416ddf8e-c2d7-4dcb-94c3-07f6a5b41666` |
| Section in the proposed plan | Functions and Sequences |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Composition and Inverse of Functions

📘 Overview
Function composition combines mappings by applying one function to the output of another. An inverse reverses a function exactly when the original function is bijective between its stated domain and codomain.

🧠 Key Idea
Composition applies functions from right to left. Reversing a composition requires invertible functions and applies their inverses in the opposite order.

⚔️ Core Details:
- For $f:A\to B$ and $g:B\to C$, the composition $g\circ f:A\to C$ is defined by $(g\circ f)(x)=g(f(x))$ for every $x\in A$.
- Composition is generally not commutative: for integer functions $f(x)=x+1$ and $g(x)=2x$, $(g\circ f)(x)=2x+2$, whereas $(f\circ g)(x)=2x+1$.
- Composition is associative whenever the domains and codomains match: $h\circ(g\circ f)=(h\circ g)\circ f$.
- A function $f:A\to B$ has a two-sided inverse $f^{-1}:B\to A$ if and only if $f$ is bijective.
- The inverse identities are $f^{-1}\circ f=\operatorname{id}_A$ and $f\circ f^{-1}=\operatorname{id}_B$, where $\operatorname{id}_A$ and $\operatorname{id}_B$ are identity functions on the indicated sets.
- For bijections $f:A\to B$ and $g:B\to C$, the inverse composition rule is $(g\circ f)^{-1}=f^{-1}\circ g^{-1}$.

🎯 Why It Matters:
- Composition models data-processing pipelines and nested function calls; compatible input and output types determine which stages can connect.
- Invertibility distinguishes reversible encodings from transformations that lose information through collisions between inputs.
- The inverse composition rule determines the correct order for undoing a sequence of reversible program transformations.

🧠 Quick Recall:
- Injective — $f(x_1)=f(x_2)\Rightarrow x_1=x_2$ for all $x_1,x_2\in A$; distinct inputs cannot share an output.
- Surjective — For every $y\in B$, there exists $x\in A$ such that $f(x)=y$; every codomain element is reached.
- Bijective — Both injective and surjective; every codomain element has exactly one preimage.
- Identity function — $\operatorname{id}_A(x)=x$ for every $x\in A$.
- Inverse function — For bijective $f:A\to B$, $f^{-1}(y)=x\iff f(x)=y$; inverse notation does not mean the reciprocal $1/f(x)$.
```

### Study Pack

**Summary:** Function composition connects mappings in sequence, modeling data-processing pipelines and nested function calls. For $f:A\to B$ and $g:B\to C$, the composition $g\circ f:A\to C$ satisfies $(g\circ f)(x)=g(f(x))$: the rightmost function acts first. Compatible domains and codomains determine which stages can connect.

Composition is associative whenever the mappings are compatible: $h\circ(g\circ f)=(h\circ g)\circ f$. It is generally not commutative, so changing the order can change the result.

| Concept | Defining property |
|---|---|
| Injective | Distinct inputs have distinct outputs. |
| Surjective | Every element of the stated codomain is reached. |
| Bijective | Every codomain element has exactly one preimage. |

A function $f:A\to B$ has a two-sided inverse $f^{-1}:B\to A$ exactly when it is bijective. The inverse identities are $f^{-1}\circ f=\operatorname{id}_A$ and $f\circ f^{-1}=\operatorname{id}_B$. An identity function returns its input unchanged.

For compatible bijections, $(g\circ f)^{-1}=f^{-1}\circ g^{-1}$. Thus, undoing reversible program transformations requires reversing their application order. Collisions between inputs prevent unique recovery, while failure to reach the entire codomain prevents an inverse from being defined on that whole codomain.

**Common Misconceptions**: Composition need not commute; inverse notation does not mean the reciprocal $1/f(x)$; a two-sided inverse requires both injectivity and surjectivity relative to the stated domain and codomain.

**Key concepts:** Function composition; Domain compatibility; Associativity; Noncommutativity; Injectivity; Surjectivity; Bijectivity; Identity functions; Inverse composition

**Q1** (MCQ, COMPUTATIONAL): For integer functions $f(x)=x+1$ and $g(x)=2x$, what is $(g\circ f)(3)$?

- A. $7$
- B. $8$ ✅
- C. $6$
- D. $9$

_Explanation:_ Apply the rightmost function first: $f(3)=3+1=4$. Then apply the outer function: $g(4)=2\cdot4=8$. The result is $8$.

**Q2** (MCQ, CONCEPTUAL): Which statement about composition is NOT correct?

- A. Compatible mappings can be composed to model a processing pipeline.
- B. The rightmost function in a composition is applied first.
- C. Compatible compositions can be regrouped without changing their result.
- D. Exchanging the order of two functions always preserves the result. ✅

_Explanation:_ Composition is generally noncommutative: applying one transformation before another can produce a different output when their order is reversed. Associativity permits regrouping, not arbitrary reordering.

**Q3** (MCQ, CONCEPTUAL): Which condition best characterizes a function that has a two-sided inverse between its stated domain and codomain?

- A. It is injective, regardless of whether it reaches the entire codomain.
- B. It is surjective, even if different inputs share an output.
- C. It is bijective. ✅
- D. Its domain and codomain are the same set.

_Explanation:_ A two-sided inverse must recover a unique input from every codomain element. This requires both the absence of collisions and coverage of the entire codomain.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Injectivity prevents distinct inputs from sharing an output. Statement 2: Surjectivity requires every element of the stated codomain to be reached. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Injectivity concerns uniqueness of the input associated with an output, whereas surjectivity concerns coverage of the codomain. Together, these properties establish bijectivity.

**Q5** (MCQ, CONCEPTUAL): A reversible data-processing pipeline applies $f$ first and then $g$. How should the original input be recovered from the final output?

- A. Apply the inverse of $f$, then the inverse of $g$.
- B. Apply the inverse of $g$, then the inverse of $f$. ✅
- C. Apply $f$ again, then apply $g$ again.
- D. Apply the inverse of $g$, then apply $f$.

_Explanation:_ Undo the most recent transformation first, then undo the earlier transformation. Reversing the application order ensures that each inverse receives the output of its corresponding original function.

---

## 22. Sequences and Summations

| Field | Value |
|---|---|
| Note id | `888adb83-d076-4565-aa9c-ebe405186bcf` |
| Section in the proposed plan | Functions and Sequences |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Sequences and Summations

📘 Overview
Sequences describe ordered values, while summations combine indexed terms into a total. In computing, they model iterative updates, cumulative data, and operation counts in algorithms.

🧠 Key Idea
A sequence specifies the value at each index; a summation specifies which indexed values to add. Recognizing arithmetic or geometric structure often converts repeated computation into a closed-form expression.

⚔️ Core Details:
- A sequence $(a_n)_{n\geq 0}$ is a function from nonnegative integer indices to values; an explicit formula gives a term directly, while a recurrence uses earlier terms and initial values.
- An arithmetic sequence has constant difference $d$: $a_n=a_0+nd$, equivalently $a_{n+1}=a_n+d$.
- A geometric sequence has constant multiplier $r$: $a_n=a_0r^n$, equivalently $a_{n+1}=ra_n$.
- The notation $\sum_{k=p}^{q}a_k$ adds terms indexed from $p$ through $q$, inclusive; the number of terms is $q-p+1$ when $p\leq q$.
- Summation is linear: $\sum_{k=p}^{q}(ca_k+db_k)=c\sum_{k=p}^{q}a_k+d\sum_{k=p}^{q}b_k$, where $c$ and $d$ are constants.
- Prefix sums satisfy $P_0=0$ and $P_{j+1}=P_j+a_j$; the total from index $l$ through $r$ is $P_{r+1}-P_l$.

🎯 Why It Matters:
- Loop work often becomes a summation: nested loops whose inner counts grow from one through $n$ perform $\Theta(n^2)$ total iterations.
- Geometric sums explain why repeatedly halving a linear workload produces $\Theta(n)$ total work rather than $\Theta(n\log n)$.
- Prefix sums support constant-time range-total queries after linear-time preprocessing.

🧠 Quick Recall:
- Arithmetic sum — $\sum_{k=0}^{n-1}(a_0+kd)=\frac{n}{2}[2a_0+(n-1)d]$, where $a_0$ is the first term, $d$ the common difference, and $n$ the number of terms.
- Geometric sum — $\sum_{k=0}^{n-1}a_0r^k=a_0\frac{1-r^n}{1-r}$ for $r\ne1$; the sum is $na_0$ for $r=1$. Here $a_0$ is the first term, $r$ the multiplier, and $n$ the term count.
- First integers — $\sum_{k=1}^{n}k=\frac{n(n+1)}{2}$ for a nonnegative integer $n$.
- First squares — $\sum_{k=1}^{n}k^2=\frac{n(n+1)(2n+1)}{6}$ for a nonnegative integer $n$.
- Empty sum — A sum with no terms equals $0$; for example, $\sum_{k=1}^{0}a_k=0$.
```

### Study Pack

**Summary:** Sequences assign values to ordered nonnegative integer indices; summations add selected indexed terms. In computing, they describe iterative updates, cumulative data, and algorithm operation counts. An explicit formula gives a term directly, while a recurrence uses earlier terms and initial values.

| Structure | Defining pattern | Explicit term |
|---|---|---|
| Arithmetic sequence | Constant difference $d$ | $a_n=a_0+nd$ |
| Geometric sequence | Constant multiplier $r$ | $a_n=a_0r^n$ |

The sum $\sum_{k=p}^{q}a_k$ includes both endpoints and contains $q-p+1$ terms when $p\leq q$. Summation is linear, so constant factors can be extracted and sums of terms can be separated. An empty sum equals $0$.

For $n$ terms, an arithmetic sum is $\frac{n}{2}[2a_0+(n-1)d]$. A geometric sum is $a_0\frac{1-r^n}{1-r}$ when $r\ne1$, and $na_0$ when $r=1$. Useful operation-count identities include $\sum_{k=1}^{n}k=\frac{n(n+1)}{2}$ and $\sum_{k=1}^{n}k^2=\frac{n(n+1)(2n+1)}{6}$.

Prefix sums satisfy $P_0=0$ and $P_{j+1}=P_j+a_j$. The total from index $l$ through $r$ is $P_{r+1}-P_l$, enabling constant-time range queries after linear-time preprocessing.

Nested loops with inner counts growing from one through $n$ perform $\Theta(n^2)$ iterations. Repeatedly halving a linear workload gives $\Theta(n)$ total work.

**Common Misconceptions**: Inclusive summation bounds require counting both endpoints. A prefix total through index $r$ uses $P_{r+1}$, not $P_r$. Repeated halving does not automatically imply $\Theta(n\log n)$ total work.

**Key concepts:** Explicit Formulas; Recurrence Relations; Arithmetic Sequences; Geometric Sequences; Summation Linearity; Inclusive Bounds; Prefix Sums; Closed Form Sums; Algorithm Work

**Q1** (MCQ, CONCEPTUAL): Which best describes how a recurrence specifies the values of a sequence?

- A. It gives each term directly from its index without earlier terms.
- B. It defines terms using earlier terms together with initial values. ✅
- C. It adds every term between two specified indices.
- D. It requires every consecutive pair of terms to have the same difference.

_Explanation:_ To generate a sequence recursively, start from the supplied initial conditions and repeatedly apply the rule to obtain later values.

**Q2** (MCQ, CONCEPTUAL): A loop updates a stored value according to a geometric sequence. What defining property does this update follow?

- A. Each update adds the same constant.
- B. Each update adds the current index.
- C. Each update multiplies the previous value by the same constant. ✅
- D. Each update replaces the value with a cumulative total.

_Explanation:_ The defining rule is $a_{n+1}=ra_n$, where $r$ does not change between updates. Repeating the rule produces the power $r^n$ in the explicit formula.

**Q3** (MCQ, CONCEPTUAL): Which statement about summations is NOT correct?

- A. An empty sum equals $0$.
- B. A constant factor can be moved outside a summation.
- C. A summation of termwise additions can be separated into two sums with the same bounds.
- D. The upper endpoint is excluded from a summation's index range. ✅

_Explanation:_ Both limits belong to the indexed range. Counting inclusively gives $q-p+1$ terms whenever $p\leq q$.

**Q4** (MCQ, COMPUTATIONAL): An array's prefix sums follow $P_0=0$ and $P_{j+1}=P_j+a_j$. If $P_2=7$ and $P_6=25$, what is the total of the array entries from index $2$ through index $5$, inclusive?

- A. $18$ ✅
- B. $32$
- C. $25$
- D. $7$

_Explanation:_ Use the prefix immediately after the final index and subtract the prefix before the starting index: $P_{5+1}-P_2=P_6-P_2=25-7=18$. The range total is $18$.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Repeatedly halving an initially linear workload produces $\Theta(n)$ total work.
Statement 2: After prefix-sum preprocessing, every range-total query requires linear time.
Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Successively halved workloads form a geometric sum bounded by a constant multiple of the initial workload. Once prefix sums are available, a range total needs only two prefix accesses and a subtraction.

---

## 23. Divisibility, Parity and the Division Algorithm

| Field | Value |
|---|---|
| Note id | `f67d2237-996a-4d83-97e0-1aafbb7cfbba` |
| Section in the proposed plan | Integers and Divisibility |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Divisibility, Parity and the Division Algorithm

📘 Overview
Divisibility identifies exact integer multiples, while parity classifies integers as even or odd. The division algorithm gives every integer a unique quotient and nonnegative remainder for a positive divisor, providing a foundation for modular arithmetic in computing.

🧠 Key Idea
For any integer $a$ and positive integer $d$, there are unique integers $q$ and $r$ such that $a=dq+r$ and $0\le r<d$. Divisibility means the remainder is zero; parity is determined by the remainder upon division by $2$.

⚔️ Core Details:
- For integers $a$ and $b$, $a\mid b$ means $b=ak$ for some integer $k$.
- Divisibility is closed under integer linear combinations: if $a\mid b$ and $a\mid c$, then $a\mid (ub+vc)$ for any integers $u$ and $v$.
- An integer $n$ is even if $n=2k$ and odd if $n=2k+1$, for some integer $k$.
- A sum is even exactly when its integer summands have the same parity; a product is odd exactly when both integer factors are odd.
- Division algorithm: for any integer $a$ and positive integer $d$, unique integers $q$ and $r$ satisfy $a=dq+r$ with $0\le r<d$.
- Negative-dividend example: $-17=5(-4)+3$, so division by $5$ gives quotient $-4$ and remainder $3$.

🎯 Why It Matters:
- Remainders support circular-buffer indexing, cyclic scheduling, and mapping hash values into table positions.
- Parity provides a simple invariant for proving that certain algorithm states or transitions are impossible.
- Repeated quotient–remainder division drives base conversion and Euclid’s greatest-common-divisor algorithm.
- Programming-language remainder operators may differ from the mathematical nonnegative remainder when the dividend is negative.

🧠 Quick Recall:
- Divisibility — $a\mid b$ means $b=ak$ for some integer $k$; $a\nmid b$ means no such integer exists.
- Parity — Even integers have remainder $0$ upon division by $2$; odd integers have remainder $1$.
- Quotient — $q=\left\lfloor a/d\right\rfloor$ for integer dividend $a$ and positive integer divisor $d$.
- Remainder — $r=a-dq$ with $0\le r<d$, where $a$ is the integer dividend, $d$ the positive integer divisor, and $q$ the integer quotient.
- Exact division — For integer $a$ and positive integer $d$, $d\mid a$ if and only if the remainder upon division by $d$ is $0$.
```

### Study Pack

**Summary:** Divisibility, parity, and quotient–remainder division describe integer relationships used in computing. For integers $a$ and $b$, $a\mid b$ means $b=ak$ for some integer $k$. If $a\mid b$ and $a\mid c$, then $a\mid(ub+vc)$ for any integers $u$ and $v$.

| Concept | Defining condition | Computing relevance |
|---|---|---|
| Divisibility | $a\mid b$ when $b=ak$ for an integer $k$ | Identifies exact integer multiples |
| Parity | Even: $n=2k$; odd: $n=2k+1$ | Supports invariants in algorithm reasoning |
| Division algorithm | $a=dq+r$, with $0\le r<d$ | Supports cyclic indexing, hashing, and base conversion |

For every integer $a$ and positive integer $d$, the division algorithm guarantees unique integers $q$ and $r$. The quotient is $q=\lfloor a/d\rfloor$, and the remainder is $r=a-dq$. Exact divisibility occurs precisely when the remainder is $0$.

Two integers have an even sum exactly when they have the same parity. Their product is odd exactly when both factors are odd. Repeated quotient–remainder division underlies base conversion and Euclid’s greatest-common-divisor algorithm.

**Common Misconceptions** A negative dividend does not require a negative mathematical remainder. Programming-language remainder operators may use conventions different from the division algorithm’s nonnegative remainder.

**Key concepts:** Integer multiples; Integer linear combinations; Even integers; Odd integers; Parity invariants; Unique quotient; Nonnegative remainder; Exact division

**Q1** (MCQ, CONCEPTUAL): What does $a\mid b$ mean for integers $a$ and $b$?

- A. $a=bk$ for some integer $k$
- B. $b=ak$ for some integer $k$ ✅
- C. $b=ak+1$ for some integer $k$
- D. $b=ak$ for some noninteger rational number $k$

_Explanation:_ The integer to the right of the divisibility symbol must be an exact integer multiple of the integer to its left.

**Q2** (MCQ, CONCEPTUAL): Suppose $a\mid b$ and $a\mid c$. Which conclusion is NOT guaranteed?

- A. $a\mid(b+c)$
- B. $a\mid(b-c)$
- C. $a\mid(2b+3c)$
- D. $a\mid(b+1)$ ✅

_Explanation:_ Divisibility is preserved under integer linear combinations of the known multiples. Adding an unrelated constant need not preserve that property.

**Q3** (MCQ, CONCEPTUAL): Statement 1: The sum of two odd integers is even. Statement 2: The product of an even integer and an odd integer is odd. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Matching parities produce an even sum. An odd product requires both factors to be odd, so an even factor prevents that outcome.

**Q4** (MCQ, COMPUTATIONAL): Using the division algorithm, what is the remainder when $-17$ is divided by $5$?

- A. $-2$
- B. $2$
- C. $0$
- D. $3$ ✅

_Explanation:_ First find the quotient: $q=\lfloor-17/5\rfloor=-4$. Then calculate $r=-17-5(-4)=3$. The remainder is $3$, which satisfies $0\le r<5$.

**Q5** (MCQ, CONCEPTUAL): Which best describes a caution when implementing quotient–remainder arithmetic with negative dividends?

- A. A language’s remainder operator may differ from the mathematical nonnegative remainder. ✅
- B. The division algorithm allows multiple valid remainders for the same positive divisor.
- C. A negative dividend prevents the division algorithm from applying.
- D. A language’s remainder operator must always return a negative result.

_Explanation:_ The mathematical rule requires $0\le r<d$ for positive $d$. An implementation’s operator conventions should be checked before using its result for tasks such as circular-buffer indexing.

---

## 24. Greatest Common Divisors and the Euclidean Algorithm

| Field | Value |
|---|---|
| Note id | `26ccf35e-04d8-42a2-8e91-950947d3980d` |
| Section in the proposed plan | Integers and Divisibility |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Greatest Common Divisors and the Euclidean Algorithm

📘 Overview
The Euclidean algorithm computes the greatest common divisor by repeatedly replacing a pair of integers with the smaller integer and their division remainder. Its extended form also finds integer coefficients used to compute modular inverses.

🧠 Key Idea
Taking a remainder preserves common divisors: $\gcd(a,b)=\gcd(b,a\bmod b)$ for $b>0$. Repeating this reduction until the remainder is zero makes the last nonzero remainder the greatest common divisor.

⚔️ Core Details:
- The greatest common divisor $\gcd(a,b)$ is the greatest positive integer dividing both integers $a$ and $b$, which are not both zero.
- Euclidean division gives $a=qb+r$ with $0\le r<b$ for integers $a\ge b>0$, where $q$ is the quotient and $r$ is the remainder.
- The identity $\gcd(a,b)=\gcd(b,r)$ follows from the two pairs having exactly the same common divisors.
- Strictly decreasing nonnegative remainders guarantee termination at the base case $\gcd(a,0)=|a|$ for $a\ne0$.
- The extended Euclidean algorithm computes integers $x$ and $y$ satisfying Bézout’s identity: $ax+by=\gcd(a,b)$.
- For positive inputs $a\ge b$, the algorithm uses $O(\log b)$ division steps in the worst case; consecutive Fibonacci numbers produce worst-case inputs.

🎯 Why It Matters:
- GCD computation avoids enumerating divisors, making it practical for large integers in computing systems.
- The extended algorithm supplies modular inverses needed in modular arithmetic and public-key cryptography.
- The algorithm illustrates correctness through an invariant and termination through a strictly decreasing quantity.

🧠 Quick Recall:
- Coprime integers — Integers $a$ and $b$ with $\gcd(a,b)=1$.
- Euclidean update — Replace $(a,b)$ with $(b,a\bmod b)$ while $b\ne0$.
- Worked example — $252=2\cdot105+42$; $105=2\cdot42+21$; $42=2\cdot21+0$; therefore $\gcd(252,105)=21$.
- Bézout coefficients — Integers $x,y$ satisfying $ax+by=\gcd(a,b)$; back-substitution through the division equations finds them.
- Modular inverse — For modulus $m>1$, an inverse of $a$ exists exactly when $\gcd(a,m)=1$; from $ax+my=1$, the inverse is $x\bmod m$.
```

### Study Pack

**Summary:** The greatest common divisor of integers $a$ and $b$, not both zero, is the greatest positive integer dividing both. The Euclidean algorithm computes it efficiently without enumerating divisors.

Euclidean division writes $a=qb+r$, where $0\le r<b$. Replacing $(a,b)$ with $(b,r)$ preserves all common divisors, so $\gcd(a,b)=\gcd(b,r)$. Repeated updates terminate because the nonnegative remainders strictly decrease. When the second entry becomes zero, the first entry’s absolute value is the GCD.

| Method or application | Result or condition |
|---|---|
| Euclidean algorithm | Computes $\gcd(a,b)$ using repeated division |
| Extended Euclidean algorithm | Finds integers $x,y$ with $ax+by=\gcd(a,b)$ |
| Modular inverse | Exists exactly when $\gcd(a,m)=1$; equals $x\bmod m$ when $ax+my=1$ |

For positive inputs $a\ge b$, the worst-case number of division steps is $O(\log b)$; consecutive Fibonacci numbers produce worst-case inputs. The extended algorithm finds Bézout coefficients through back-substitution, supporting modular arithmetic and public-key cryptography.

**Common Misconceptions**: The GCD is the last nonzero remainder, not the final zero remainder. A modular inverse does not always exist; the integer and modulus must be coprime.

**Key concepts:** Greatest Common Divisor; Euclidean Division; Common Divisor Invariant; Algorithm Termination; Bézout Coefficients; Coprime Integers; Modular Inverses; Worst Case Complexity

**Q1** (MCQ, CONCEPTUAL): Which best describes why replacing an integer pair with its smaller entry and division remainder preserves the GCD?

- A. The quotient is a common divisor of both pairs.
- B. Both pairs have exactly the same common divisors. ✅
- C. The remainder divides both original integers.
- D. Both pairs have the same sum.

_Explanation:_ Any divisor of both original integers also divides their remainder. Conversely, a divisor of the smaller integer and the remainder divides the original larger integer through the division equation.

**Q2** (MCQ, CONCEPTUAL): What guarantees termination of the Euclidean algorithm for positive integer inputs?

- A. The quotient remains constant during every update.
- B. The GCD becomes smaller after every division.
- C. Every remainder is exactly half the preceding divisor.
- D. The nonnegative remainders strictly decrease until zero. ✅

_Explanation:_ Each remainder is smaller than the divisor that produced it. A strictly decreasing sequence of nonnegative integers cannot continue indefinitely.

**Q3** (MCQ, CONCEPTUAL): Which statement about the Euclidean algorithm is NOT correct?

- A. It must enumerate all divisors of both inputs. ✅
- B. It uses $O(\log b)$ division steps in the worst case for positive inputs $a\ge b$.
- C. Consecutive Fibonacci numbers produce worst-case inputs.
- D. The extended algorithm can obtain Bézout coefficients by back-substitution.

_Explanation:_ Repeated division reduces the problem while preserving its GCD, avoiding an exhaustive search through possible divisors.

**Q4** (MCQ, CONCEPTUAL): Assume $m>1$. Statement 1: An inverse of $a$ modulo $m$ exists exactly when $\gcd(a,m)=1$. Statement 2: If $ax+my=1$, then $y\bmod m$ is necessarily an inverse of $a$. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Coprimality is the existence condition. Reducing Bézout’s identity modulo $m$ gives $ax\equiv1\pmod m$, so the coefficient multiplying $a$ supplies its inverse.

**Q5** (MCQ, COMPUTATIONAL): Using the Euclidean algorithm, what is $\gcd(252,105)$?

- A. $7$
- B. $42$
- C. $21$ ✅
- D. $105$

_Explanation:_ Divide successively: $252=2\cdot105+42$, then $105=2\cdot42+21$, and finally $42=2\cdot21+0$. The last nonzero remainder is $21$.

---

## 25. Introduction to Mathematical Proof

| Field | Value |
|---|---|
| Note id | `c275a182-64a8-4717-bda3-05fa8a5b19b6` |
| Section in the proposed plan | Proof Techniques |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Introduction to Mathematical Proof

📘 Overview
A mathematical proof establishes a claim through valid deductions from definitions, assumptions, and previously established results. In computing, proofs justify claims about algorithms, data structures, and program behavior beyond the cases tested.

🧠 Key Idea
A proof must establish the entire claim under its stated assumptions, not merely show that several examples work. The logical structure of the claim determines an appropriate proof method.

⚔️ Core Details:
- Direct proof of $P \Rightarrow Q$ derives the conclusion $Q$ from the assumption $P$ using valid deductions.
- Proof by contraposition establishes $P \Rightarrow Q$ by proving the logically equivalent statement $\neg Q \Rightarrow \neg P$.
- Proof by contradiction establishes a claim by deriving an impossibility from its negation together with the given assumptions.
- A universal claim requires an argument for an arbitrary element of its domain; an existential claim can be proved by producing a witness.
- Induction proves $P(n)$ for every integer $n \geq n_0$ using a base case $P(n_0)$ and a step $P(k) \Rightarrow P(k+1)$ for arbitrary $k \geq n_0$.
- A single counterexample disproves a universal claim by satisfying its assumptions while violating its conclusion.

🎯 Why It Matters:
- Algorithm correctness requires guarantees for every permitted input rather than success on a finite test set.
- Inductive reasoning supports correctness arguments for recursive functions and recursively defined data structures.
- Precise assumptions expose missing preconditions, boundary cases, and invalid deductions in software verification.

🧠 Quick Recall:
- Proposition — A declarative statement with a definite truth value, either true or false.
- Implication — $P \Rightarrow Q$ is false exactly when $P$ is true and $Q$ is false.
- Converse — $Q \Rightarrow P$ is the converse of $P \Rightarrow Q$ and is not generally equivalent to it.
- Universal quantifier — $\forall x \in D,\ P(x)$ asserts that predicate $P$ holds for every element $x$ in domain $D$.
- Existential quantifier — $\exists x \in D,\ P(x)$ asserts that predicate $P$ holds for at least one element $x$ in domain $D$.
```

### Study Pack

**Summary:** A mathematical proof establishes a claim through valid deductions from definitions, assumptions, and established results. In computing, proofs justify algorithm correctness, data structure properties, and program behavior for every permitted input, rather than only tested cases.

The logical structure of a claim guides the proof method.

| Method | Approach |
|---|---|
| Direct proof | Assume $P$ and derive $Q$ to establish $P \Rightarrow Q$. |
| Contraposition | Prove $\neg Q \Rightarrow \neg P$, which is equivalent to $P \Rightarrow Q$. |
| Contradiction | Assume the claim's negation and derive an impossibility under the given assumptions. |

A proposition is a declarative statement with a definite truth value. An implication $P \Rightarrow Q$ is false exactly when $P$ is true and $Q$ is false.

A universal claim, $\forall x \in D,\ P(x)$, requires reasoning about an arbitrary element of its domain. An existential claim, $\exists x \in D,\ P(x)$, can be established by producing a witness. A single counterexample disproves a universal claim when it satisfies the assumptions but violates the conclusion.

Induction establishes $P(n)$ for every integer $n \geq n_0$ by proving the base case $P(n_0)$ and the step $P(k) \Rightarrow P(k+1)$ for arbitrary $k \geq n_0$. It supports reasoning about recursive functions and recursively defined data structures. Precise assumptions help expose missing preconditions and boundary cases.

**Common Misconceptions**: Successful tests do not establish a universal guarantee. The converse $Q \Rightarrow P$ is not generally equivalent to $P \Rightarrow Q$. An induction step alone does not replace the base case.

**Key concepts:** Direct proof; Proof by contraposition; Proof by contradiction; Universal claims; Existential witnesses; Mathematical induction; Counterexamples; Implication and converse; Algorithm correctness

**Q1** (MCQ, CONCEPTUAL): To directly prove that an algorithm's precondition implies its postcondition, how should the argument begin?

- A. Assume the precondition holds and derive the postcondition. ✅
- B. Assume the postcondition holds and derive the precondition.
- C. Check several inputs and generalize from their outputs.
- D. Assume the precondition fails and derive the postcondition.

_Explanation:_ A direct proof starts with the hypothesis of the implication. Each valid deduction must lead toward the required conclusion for an arbitrary permitted input.

**Q2** (MCQ, CONCEPTUAL): Which best describes proving an implication by contraposition?

- A. Proving that its conclusion implies its hypothesis.
- B. Deriving an impossibility after assuming the entire claim is false.
- C. Showing that failure of its conclusion implies failure of its hypothesis. ✅
- D. Showing that its conclusion holds for selected permitted inputs.

_Explanation:_ The contrapositive is logically equivalent to the original implication. Establishing it therefore proves the original claim without relying on its converse.

**Q3** (MCQ, CONCEPTUAL): Which observation does NOT disprove the claim that an algorithm returns a correct result for every input satisfying its precondition?

- A. A boundary input satisfies the precondition but produces an incorrect result.
- B. A permitted input produces an output that violates the required postcondition.
- C. An input within the stated domain satisfies the assumptions but violates the conclusion.
- D. An input violates the precondition and produces an incorrect result. ✅

_Explanation:_ A counterexample must fall within the guarantee's stated assumptions. Behavior outside those assumptions cannot refute the conditional claim.

**Q4** (MCQ, CONCEPTUAL): Statement 1: An induction proof requires a base case at the stated starting integer. Statement 2: The induction step must apply to an arbitrary integer at or above that starting point. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ Induction needs an initial established case and a general implication from each case to its successor. Together, these justify the claim throughout the specified integer range.

**Q5** (MCQ, CONCEPTUAL): What is sufficient to prove that there exists a permitted input for which an algorithm satisfies a stated property?

- A. Prove that every input outside the permitted domain has the property.
- B. Produce a permitted input and establish that it has the property. ✅
- C. Assume an unspecified permitted input has the property without justification.
- D. Show that a permitted input fails to have the property.

_Explanation:_ An existential claim needs a witness within its specified domain. Verifying the predicate for that witness establishes existence without requiring a universal argument.

---

## 26. Direct Proof

| Field | Value |
|---|---|
| Note id | `3d36cf2b-1d4c-45cc-a539-b45e34604ecd` |
| Section in the proposed plan | Proof Techniques |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Direct Proof

📘 Overview
A direct proof establishes an implication by deriving its conclusion from its hypothesis using definitions and valid deductions. In discrete mathematics for computing, this method establishes properties of integers, sets, relations, and algorithms.

🧠 Key Idea
To prove $P \Rightarrow Q$ directly, assume $P$ and derive $Q$ without introducing an assumption that contradicts the desired conclusion. For a universally quantified claim, the argument must apply to an arbitrary element of the stated domain.

⚔️ Core Details:
- The proof obligation for $P \Rightarrow Q$ is to establish $Q$ under the assumption $P$.
- A proof of $\forall x \in D,\; P(x) \Rightarrow Q(x)$ begins with an arbitrary $x \in D$ satisfying $P(x)$.
- Each deduction must follow from a hypothesis, a definition, an established result, or a valid inference rule.
- An integer $n$ is even exactly when $n = 2k$ for some integer $k$.
- For even integers $a$ and $b$, write $a = 2r$ and $b = 2s$ with integers $r$ and $s$.
- The identity $a+b = 2r+2s = 2(r+s)$ proves that the sum is even because $r+s$ is an integer.

🎯 Why It Matters:
- Expanding definitions turns informal claims about discrete structures into precise proof obligations.
- Direct deduction supports algorithm correctness arguments from preconditions to postconditions.
- An arbitrary-element argument establishes a general guarantee rather than evidence from selected test cases.

🧠 Quick Recall:
- Implication — $P \Rightarrow Q$ states that whenever hypothesis $P$ holds, conclusion $Q$ holds.
- Arbitrary element — An unspecified member of the domain with no special properties beyond the stated assumptions.
- Even integer — $n = 2k$ for some integer $k$.
- Converse — $Q \Rightarrow P$ is a different claim from $P \Rightarrow Q$.
- Proof by examples — Checking particular cases does not establish a universal claim unless every case in a finite domain is covered.
```

### Study Pack

**Summary:** A direct proof establishes an implication $P \Rightarrow Q$ by assuming $P$ and deriving $Q$ using definitions, established results, and valid inference rules. In computing, this reasoning supports properties of discrete structures and algorithm correctness arguments from preconditions to postconditions.

To prove $\forall x \in D,\; P(x) \Rightarrow Q(x)$, begin with an arbitrary $x \in D$ satisfying $P(x)$. The argument must not rely on special properties beyond the stated assumptions.

For example, if integers $a$ and $b$ are even, write $a = 2r$ and $b = 2s$ for integers $r$ and $s$. Then $a+b = 2(r+s)$. Since $r+s$ is an integer, the sum is even.

| Approach | What it establishes |
|---|---|
| Direct proof with an arbitrary element | A universal implication through valid deductions |
| Checking selected examples | Evidence for those cases, not a universal guarantee |

**Common Misconceptions**: The converse $Q \Rightarrow P$ is not the same claim as $P \Rightarrow Q$. Selected examples do not prove a universal claim unless they exhaust a finite domain.

**Key concepts:** Direct proof; Hypothesis and conclusion; Arbitrary element; Universal implication; Valid deductions; Even integers; Integer closure; Converse; Proof by examples

**Q1** (MCQ, CONCEPTUAL): Which best describes a direct proof of $P \Rightarrow Q$?

- A. Assume $Q$ and derive $P$.
- B. Assume $P$ and derive $Q$ using valid deductions. ✅
- C. Assume $\neg Q$ and derive a contradiction.
- D. Verify $Q$ for several inputs satisfying $P$.

_Explanation:_ The hypothesis provides the starting point, and each justified step moves toward the required conclusion without introducing its negation.

**Q2** (MCQ, CONCEPTUAL): In a direct argument for algorithm correctness, what can justify a deduction from a precondition toward a postcondition?

- A. An observed pattern in selected test outputs
- B. The desired postcondition treated as already established
- C. A valid inference from the precondition and an established result ✅
- D. An unstated restriction to inputs that make the argument work

_Explanation:_ Every step needs a logical basis in what is already known or assumed. This preserves the connection between the algorithm's starting guarantee and its required outcome.

**Q3** (MCQ, CONCEPTUAL): In the proof that two even integers have an even sum, why is it important that $r+s$ is an integer?

- A. It shows that both original integers must be positive.
- B. It establishes that the two original integers are equal.
- C. It allows the proof to restrict attention to selected examples.
- D. It supplies the integer witness required by the definition of evenness. ✅

_Explanation:_ Evenness requires an expression equal to twice an integer. After factoring the sum as $2(r+s)$, closure under integer addition ensures that the factor in parentheses has the required type.

**Q4** (MCQ, CONCEPTUAL): Which claim is NOT justified merely by proving $P \Rightarrow Q$?

- A. The converse $Q \Rightarrow P$ also holds. ✅
- B. The conclusion $Q$ holds whenever $P$ holds.
- C. Under the hypothesis $P$, the conclusion $Q$ follows.
- D. Every case satisfying $P$ must also satisfy $Q$.

_Explanation:_ Reversing the direction creates a separate implication that needs its own justification. A proof establishes only the direction actually demonstrated.

**Q5** (MCQ, CONCEPTUAL): Statement 1: An arbitrary element has no special properties beyond the stated domain and assumptions. Statement 2: Checking several selected cases always establishes a universal claim. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ A general argument must work without relying on a specially chosen case. Testing provides a universal guarantee only when every case in a finite domain has been covered.

---

## 27. Proof by Contraposition

| Field | Value |
|---|---|
| Note id | `a214fd5c-bbbc-4e9c-b209-b650df393a7c` |
| Section in the proposed plan | Proof Techniques |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Proof by Contraposition

📘 Overview
Proof by contraposition establishes a conditional statement by proving its logically equivalent contrapositive. In computing, it is useful when the absence of an output property exposes a violated input condition or structural requirement.

🧠 Key Idea
To prove “if $P$, then $Q$,” assume $Q$ is false and deduce that $P$ is false. This proves the original implication without assuming its hypothesis directly.

⚔️ Core Details:
- Logical basis: $(P \to Q) \equiv (\neg Q \to \neg P)$ for propositions $P$ and $Q$.
- Proof structure: derive $\neg P$ from the assumption $\neg Q$ using definitions and established results.
- Quantifier negation: $\neg(\forall x\,R(x)) \equiv \exists x\,\neg R(x)$, where $R(x)$ is a predicate over the stated domain.
- The converse $Q \to P$ is not generally equivalent to the original implication $P \to Q$.
- Graph example: a finite directed graph containing a directed cycle cannot have a topological ordering.
- Contrapositive argument: every edge in a topological ordering points toward a later vertex, ruling out a directed cycle.

🎯 Why It Matters:
- Nonexistence claims about computing structures can become easier existence-based arguments, as with topological orderings and cycles.
- Correctly negating specifications prevents errors when proving algorithm properties involving all inputs or the existence of a counterexample.
- Separating an implication from its converse prevents unjustified claims about necessary and sufficient conditions in software specifications.

🧠 Quick Recall:
- Contrapositive — $\neg Q \to \neg P$ for the original implication $P \to Q$.
- Starting assumption — $\neg Q$, the negation of the original conclusion.
- Proof target — $\neg P$, the negation of the original hypothesis.
- Topological ordering — a linear ordering of vertices in which every directed edge points from an earlier vertex to a later vertex.
- Proof by contradiction — assumes the negation of the entire claim and derives an inconsistency; contraposition directly proves an equivalent implication.
```

### Study Pack

**Summary:** Proof by contraposition establishes a conditional statement by proving its logically equivalent contrapositive: $(P \to Q) \equiv (\neg Q \to \neg P)$. Begin by assuming $\neg Q$, then use definitions and established results to derive $\neg P$.

| Form | Expression | Relationship to original |
|---|---|---|
| Original implication | $P \to Q$ | Claim to establish |
| Contrapositive | $\neg Q \to \neg P$ | Logically equivalent |
| Converse | $Q \to P$ | Not generally equivalent |

Correct quantifier negation is essential when proving algorithm specifications: $\neg(\forall x\,R(x)) \equiv \exists x\,\neg R(x)$. A universal property fails when at least one input violates it.

For a finite directed graph, a directed cycle prevents a topological ordering. The contrapositive argument assumes a topological ordering exists. Every directed edge must then point toward a later vertex, making a directed cycle impossible.

Contraposition directly proves an equivalent implication. Proof by contradiction instead assumes the negation of the entire claim and derives an inconsistency.

**Common Misconceptions**: The converse is not the contrapositive. Negating a universal statement requires one counterexample, not failure for every input. Contraposition begins by negating the conclusion, not the hypothesis.

**Key concepts:** Conditional Statements; Logical Equivalence; Contrapositive; Converse; Quantifier Negation; Topological Ordering; Directed Cycles; Proof by Contradiction

**Q1** (MCQ, CONCEPTUAL): To prove a software specification $P \to Q$ by contraposition, what should you assume and then establish?

- A. Assume $P$ and establish $Q$.
- B. Assume $\neg Q$ and establish $\neg P$. ✅
- C. Assume $Q$ and establish $P$.
- D. Assume $\neg P$ and establish $\neg Q$.

_Explanation:_ Contraposition reverses the direction of the original implication and negates both propositions, producing a logically equivalent statement.

**Q2** (MCQ, CONCEPTUAL): A specification states $\forall x\,R(x)$ over a stated input domain. What is its negation?

- A. $\forall x\,\neg R(x)$
- B. $\exists x\,R(x)$
- C. $\neg(\exists x\,R(x))$
- D. $\exists x\,\neg R(x)$ ✅

_Explanation:_ A universal specification fails as soon as one input provides a counterexample. Failure does not need to occur throughout the domain.

**Q3** (MCQ, CONCEPTUAL): Which claim about a conditional software specification is NOT generally correct?

- A. Proving its converse is sufficient to establish the original implication. ✅
- B. Its contrapositive has the same truth value as the original implication.
- C. A contraposition proof can begin by negating the original conclusion.
- D. Definitions and established results can support the deduction in a contraposition proof.

_Explanation:_ Reversing an implication without negating its propositions changes the claim and does not preserve logical equivalence in general.

**Q4** (MCQ, CONCEPTUAL): Which best describes why a topological ordering rules out a directed cycle?

- A. It requires every vertex to have an outgoing edge.
- B. It places every pair of vertices next to each other.
- C. Following directed edges always moves later in the ordering, preventing a return to the starting vertex. ✅
- D. It allows a cycle provided its vertices occupy consecutive positions.

_Explanation:_ Returning to the starting vertex would conflict with the strict forward progression required by every edge in the ordering.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Proof by contraposition directly establishes an implication logically equivalent to the original claim. Statement 2: Proof by contradiction assumes the negation of the entire claim and derives an inconsistency. Which is correct?

- A. Both statements are correct ✅
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ The methods differ in their proof structure: contraposition uses logical equivalence, whereas contradiction demonstrates that denying the claim leads to an impossibility.

---

## 28. Proof by Contradiction

| Field | Value |
|---|---|
| Note id | `4150f7b6-4cc6-4ada-92ea-34b8aaefeba9` |
| Section in the proposed plan | Proof Techniques |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Proof by Contradiction

📘 Overview
Proof by contradiction establishes a claim by showing that its negation leads to an impossibility. In computing, it supports proofs about algorithms, graph structures, and limits on what programs can compute.

🧠 Key Idea
Assume the claim is false and derive a contradiction using valid deductions. In classical logic, this rules out the negation and establishes the original claim.

⚔️ Core Details:
- Starting assumption: To prove a proposition $P$, temporarily assume $\neg P$ alongside the stated premises.
- Contradiction: Derive an impossibility such as $Q \land \neg Q$ from the temporary assumption.
- Conclusion: Discharge the assumption $\neg P$ and conclude $P$ by classical contradiction reasoning.
- Quantified claims: The negation of $\forall x\,P(x)$ is $\exists x\,\neg P(x)$.
- Graph example: A directed cycle in a topologically ordered graph would require $r(v_1)<r(v_2)<\cdots<r(v_k)<r(v_1)$, where $r(v)$ is the position of vertex $v$.
- Graph contradiction: Transitivity would give $r(v_1)<r(v_1)$, contradicting the irreflexivity of strict order.

🎯 Why It Matters:
- Nonexistence and impossibility claims can be established without constructing or enumerating every candidate.
- Algorithm correctness proofs can rule out an alleged failing execution by exposing an inconsistency with an invariant.
- Graph proofs connect structural constraints to algorithm requirements, such as the absence of directed cycles in topologically sortable graphs.

🧠 Quick Recall:
- Contradiction — An impossible statement, denoted $\bot$; a standard example is $Q \land \neg Q$.
- Classical contradiction rule — A derivation of $\bot$ from an assumption $\neg P$ permits concluding $P$ after discharging that assumption.
- Negating an implication — $\neg(P\rightarrow Q)\equiv P\land\neg Q$.
- Negating an existential claim — $\neg\exists x\,P(x)\equiv\forall x\,\neg P(x)$.
- Contraposition — Proves $P\rightarrow Q$ by establishing $\neg Q\rightarrow\neg P$, rather than explicitly deriving a contradiction.
```

### Study Pack

**Summary:** Proof by contradiction establishes a proposition by showing that its negation is incompatible with the stated premises. To prove $P$, temporarily assume $\neg P$, use valid deductions to derive an impossibility $\bot$, then discharge the assumption and conclude $P$ using classical logic. A standard contradiction is $Q\land\neg Q$.

| Method | Proof strategy |
|---|---|
| Contradiction | Assume $\neg P$ and derive $\bot$ to establish $P$. |
| Contraposition | Establish $\neg Q\rightarrow\neg P$ to prove $P\rightarrow Q$. |

Correct negation is essential: $\neg\forall x\,P(x)\equiv\exists x\,\neg P(x)$, $\neg\exists x\,P(x)\equiv\forall x\,\neg P(x)$, and $\neg(P\rightarrow Q)\equiv P\land\neg Q$.

In a topologically ordered graph, a directed cycle would force $r(v_1)<r(v_2)<\cdots<r(v_k)<r(v_1)$. Transitivity then gives $r(v_1)<r(v_1)$, contradicting the irreflexivity of strict order. Thus, a topologically ordered graph cannot contain a directed cycle.

In computing, contradiction supports nonexistence proofs, algorithm correctness arguments that rule out failing executions using invariants, and reasoning about limits of computation.

**Common Misconceptions** Negating a universal claim requires one counterexample, not failure for every element. Contraposition does not require explicitly deriving a contradiction. The temporary assumption is discharged, not retained as an established premise.

**Key concepts:** Temporary Assumptions; Logical Contradiction; Assumption Discharge; Quantifier Negation; Implication Negation; Contraposition; Topological Ordering; Strict Order Irreflexivity

**Q1** (MCQ, CONCEPTUAL): Which best describes the starting step when proving a proposition $P$ by contradiction?

- A. Assume $P$ and use it to justify itself.
- B. Temporarily assume $\neg P$ alongside the stated premises. ✅
- C. Discard the stated premises and assume $\neg P$.
- D. Prove a related proposition without assuming anything about $P$.

_Explanation:_ The proof tests whether denying the target is compatible with the premises. If valid deductions produce an impossibility, that denial can be rejected.

**Q2** (MCQ, CONCEPTUAL): To prove $\forall x\,P(x)$ by contradiction, what is the correct negation to assume?

- A. $\forall x\,\neg P(x)$
- B. $\exists x\,P(x)$
- C. $\neg\exists x\,P(x)$
- D. $\exists x\,\neg P(x)$ ✅

_Explanation:_ A universal claim fails if at least one element violates the property. The temporary assumption therefore introduces a counterexample.

**Q3** (MCQ, CONCEPTUAL): In the contradiction proof that a topologically ordered graph has no directed cycle, which statement is NOT correct?

- A. Vertex positions strictly increase along the assumed cycle's directed edges.
- B. Transitivity combines the position inequalities around the assumed cycle.
- C. A strict ordering permits a vertex position to be less than itself. ✅
- D. The impossible position inequality rules out the assumed directed cycle.

_Explanation:_ Strict order is irreflexive: $r(v_1)<r(v_1)$ cannot hold. Deriving this relation exposes the inconsistency caused by assuming a directed cycle.

**Q4** (MCQ, CONCEPTUAL): Statement 1: Negating $P\rightarrow Q$ gives $P\land\neg Q$. Statement 2: Proving $P\rightarrow Q$ by contraposition requires explicitly deriving a contradiction. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct ✅
- C. Only Statement 2 is correct
- D. Neither statement is correct

_Explanation:_ An implication fails when its antecedent holds but its consequent does not. Contraposition instead establishes $\neg Q\rightarrow\neg P$; an explicit impossibility is not required.

**Q5** (MCQ, CONCEPTUAL): A proof derives $Q\land\neg Q$ from the stated premises and a temporary assumption $\neg P$. What does classical contradiction reasoning permit next?

- A. Discharge $\neg P$ and conclude $P$. ✅
- B. Retain $\neg P$ as an established conclusion.
- C. Conclude that both $P$ and $\neg P$ are established.
- D. Discard the contradiction and conclude $Q$ alone.

_Explanation:_ The impossibility shows that the temporary denial cannot be maintained alongside the premises. Classical reasoning allows the original proposition to be established once that assumption is discharged.

---

## 29. Proof by Cases and Counterexamples

| Field | Value |
|---|---|
| Note id | `a9d41b2f-2781-4d79-8937-ee0e8afc9763` |
| Section in the proposed plan | Proof Techniques |
| Subject / Domain Context / Authored Depth | Discrete Mathematics / COMPUTING / COLLEGE |
| Current Applicable Programs | Computer Engineering, Computer Science |
| Study Pack | 1 pack, 5 quiz items |

### Note body

```text
Proof by Cases and Counterexamples

📘 Overview
Proof by cases establishes a claim by proving it under every possibility in an exhaustive case split. A counterexample disproves a universal claim by identifying one valid input for which the claim fails.

🧠 Key Idea
A proof must cover every permitted input; a disproof of a universal claim needs only one permitted input that violates its conclusion. Checking several successful examples is not a proof.

⚔️ Core Details:
- A case split is exhaustive when every input satisfying the original assumptions belongs to at least one case.
- Each case must establish the same target conclusion using the original assumptions and that case's additional condition.
- Cases may overlap; mutual exclusivity is not required.
- Even case for an integer parity claim: $n=2k$ gives $n(n+1)=2[k(2k+1)]$ for an integer $k$.
- Odd case for the same claim: $n=2k+1$ gives $n(n+1)=2[(2k+1)(k+1)]$ for an integer $k$.
- The array $[1,3,2,4]$ disproves the claim that an array is sorted whenever its first element is no greater than its last.

🎯 Why It Matters:
- Algorithm correctness proofs often split inputs according to conditional branches, empty versus nonempty structures, or boundary conditions.
- Counterexamples expose insufficient preconditions and incorrect invariants before they become implementation errors.
- Passing tests supports confidence in software but does not establish correctness over an unexamined input domain.

🧠 Quick Recall:
- Proof by cases — establish an exhaustive set of conditions and prove the target under each condition.
- Exhaustiveness — for cases $C_1,\ldots,C_m$ over domain $D$, show $\forall x\in D,\ C_1(x)\lor\cdots\lor C_m(x)$.
- Counterexample — an element $a\in D$ satisfying $\neg P(a)$, which disproves $\forall x\in D,\ P(x)$.
- Conditional claim — refuting $\forall x\in D,\ (P(x)\Rightarrow Q(x))$ requires $a\in D$ with $P(a)\land\neg Q(a)$.
- Integer parity — every integer is even or odd, so the two parity cases prove $n(n+1)$ is even for every integer $n$.
```

### Study Pack

**Summary:** Proof by cases establishes a claim by dividing its permitted inputs into exhaustive cases and proving the same conclusion in each case. Every case uses the original assumptions together with its additional condition. Cases may overlap; complete coverage, not mutual exclusivity, is required.

| Method | Requirement | Outcome |
|---|---|---|
| Proof by cases | Cover every permitted input and establish the conclusion in each case | Proves the claim |
| Counterexample | Find one permitted input that violates the conclusion | Disproves a universal claim |

For integer parity, every integer is even or odd. If $n=2k$, then $n(n+1)=2[k(2k+1)]$. If $n=2k+1$, then $n(n+1)=2[(2k+1)(k+1)]$. With integer $k$, both cases establish that $n(n+1)$ is even.

To refute $\forall x\in D,\ (P(x)\Rightarrow Q(x))$, find $a\in D$ satisfying $P(a)\land\neg Q(a)$. For example, the array $[1,3,2,4]$ satisfies the condition that its first element is no greater than its last, yet it is not sorted.

In algorithm correctness, cases can represent conditional branches, empty versus nonempty structures, or boundary conditions. Counterexamples help identify insufficient preconditions and incorrect invariants.

**Common Misconceptions**: Cases need not be mutually exclusive. Several successful tests do not prove correctness over all permitted inputs. A conditional counterexample must satisfy the premise while violating the conclusion.

**Key concepts:** Proof by cases; Exhaustive case splits; Overlapping cases; Universal claims; Conditional counterexamples; Integer parity; Algorithm correctness; Testing limitations

**Q1** (MCQ, CONCEPTUAL): Which best describes an exhaustive case split in an algorithm correctness proof?

- A. Every permitted input belongs to at least one case. ✅
- B. Every case contains the same number of inputs.
- C. Every input belongs to exactly two cases.
- D. Every case corresponds to a successful test.

_Explanation:_ A case-based proof must leave no input satisfying the original assumptions uncovered; otherwise, its conclusion has not been established for the entire domain.

**Q2** (MCQ, CONCEPTUAL): Which is NOT a requirement for a valid proof by cases?

- A. The cases collectively cover the permitted input domain.
- B. Each case establishes the same target conclusion.
- C. The cases are mutually exclusive. ✅
- D. Each case retains the original assumptions.

_Explanation:_ Overlap does not invalidate a case-based proof. What matters is complete coverage and a valid argument for the target conclusion within each case.

**Q3** (MCQ, CONCEPTUAL): To disprove an algorithm claim of the form $\forall x\in D,\ (P(x)\Rightarrow Q(x))$, what must a counterexample demonstrate?

- A. An input outside $D$ for which $Q$ fails.
- B. An input in $D$ for which both $P$ and $Q$ hold.
- C. An input in $D$ for which $P$ fails.
- D. An input in $D$ for which $P$ holds and $Q$ fails. ✅

_Explanation:_ A conditional guarantee is violated only when an admissible input meets its premise but does not meet its promised conclusion.

**Q4** (MCQ, CONCEPTUAL): Why does the array $[1,3,2,4]$ refute the claim that an array is sorted whenever its first element is no greater than its last?

- A. Its endpoints violate the claim's premise.
- B. Its endpoints satisfy the premise, but an internal adjacent pair is out of order. ✅
- C. Its length prevents the claim from applying.
- D. Its internal ordering establishes that every array is unsorted.

_Explanation:_ The endpoint comparison holds, but $3>2$ creates an inversion. This single permitted input therefore defeats the universal guarantee.

**Q5** (MCQ, CONCEPTUAL): Statement 1: Several successful tests establish algorithm correctness over an unexamined input domain. Statement 2: One valid input that violates a universal correctness claim is enough to disprove it. Which is correct?

- A. Both statements are correct
- B. Only Statement 1 is correct
- C. Only Statement 2 is correct ✅
- D. Neither statement is correct

_Explanation:_ Successful tests provide evidence only for the inputs examined. A universal claim, however, requires no exceptions, so a single permitted failure is decisive.

---

_End of pack._
