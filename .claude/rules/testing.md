---
paths:
  - "**/src/test/**/*.kt"
---

# Testing rules

- One behaviour per test. If the name needs "and", split it.
- Test names state the behaviour in backticks: `` `a miss with no rest days banked pauses the streak` ``.
- Assert on observable behaviour, never on internal call counts, unless the
  call itself is the contract.
- Fake at the process boundary only: the `Clock`, the in-memory Room stack
  from `inMemoryRepositories`, DataStore on a temp file. Do not mock Sprout's
  own classes to make a test easier to write.
- Scoring changes need a rate test as well as a case test. A single miss and a
  single repair both passing says nothing about a habit done every other day.
- A bug fix lands with the test that would have caught it.
- Never weaken an assertion, add `@Ignore`, or widen a tolerance to get green.
  If a test is wrong, say why before changing it.
