# EXP-0011 — Multi-hop Semantic Support Graph

Extends EXP-0010 from single-hop semantic support to bounded multi-hop reasoning.

Rules:
- governed evidence is the only graph root;
- every derived node names a registered Flooow-owned rule;
- every premise must already be supported;
- cycles, missing premises and unknown rules fail deterministically;
- unsupported intermediate nodes poison downstream claims;
- narrative text has zero authority;
- accepted intelligence remains non-canonical and non-executable.

CI:
```powershell
.\gradlew.bat -p research\experiments\exp-0011-multihop-support-graph-harness test
```
