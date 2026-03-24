# ComplexityAI — Java Static Complexity Analyzer

> Research-grade Java algorithm complexity analyzer.
> Created by **themugglecoder** with ♥

---

## Architecture

```
complexity-analyzer.html (Frontend)
        │
        ├─── [Primary] POST http://localhost:8080/api/analyze
        │              Java Spring Boot Backend
        │              └── StaticAnalysisEngine (JavaParser AST)
        │                    ├── Loop detection (O(n), O(log n))
        │                    ├── Nesting analysis (multiply)
        │                    ├── Recursion detection
        │                    └── Space complexity
        │
        └─── [Fallback] Anthropic API (LLM)
                        Used only when backend is offline
```

---

## Quick Start

### 1. Run the Java Backend

**Requirements:** Java 17+, Maven 3.8+

```bash
cd complexity-analyzer/
mvn spring-boot:run
```

Server starts at `http://localhost:8080`

Verify with:
```
GET http://localhost:8080/api/health
```

### 2. Open the Frontend

Just open `complexity-analyzer.html` in your browser.

The status bar will show:
- 🟢 **Backend Online** — full static analysis mode
- 🔴 **Backend Offline** — falls back to AI analysis

---

## API

### POST /api/analyze

**Request:**
```json
{
  "code": "your Java code here"
}
```

**Response:**
```json
{
  "timeComplexity":  "O(n²)",
  "timeLabel":       "Quadratic — grows with square of input size",
  "spaceComplexity": "O(1)",
  "spaceLabel":      "Constant — fixed memory regardless of input",
  "steps": [
    "Outer `for` loop with incremental counter update → O(n)",
    "`for` loop (O(n)) nesting O(n) body → O(n²) combined"
  ],
  "verdict": "This algorithm has O(n²) time complexity, using O(1) auxiliary space.",
  "notes": null,
  "confidence": 0.91,
  "source": "static_analysis",
  "hasRecursion": false
}
```

### GET /api/health

Returns engine status.

---

## What the Engine Detects

| Pattern                        | Complexity  |
|-------------------------------|-------------|
| `for (i++)`                   | O(n)        |
| `for (i *= 2)` / `for (i /= 2)` | O(log n)  |
| `for-each` loop               | O(n)        |
| `while` with halving pattern  | O(log n)    |
| Nested loops                  | Multiply    |
| Sequential loops              | Max (dominant) |
| `if-else` branches            | Worst case  |
| Single recursive call         | O(n)        |
| 2+ recursive calls per frame  | O(2ⁿ)       |
| Array / collection allocation | O(n) space  |
| Recursive call stack          | O(n) space  |

---

## Project Structure

```
complexity-analyzer/
├── pom.xml
├── complexity-analyzer.html        ← Frontend (open in browser)
└── src/main/java/com/muggle/analyzer/
    ├── AnalyzerApplication.java    ← Spring Boot entry point
    ├── controller/
    │   └── AnalyzerController.java ← REST endpoint
    ├── engine/
    │   └── StaticAnalysisEngine.java ← Core AST logic
    └── model/
        ├── Complexity.java         ← Big-O algebra
        ├── AnalysisRequest.java    ← Request DTO
        └── AnalysisResult.java     ← Response DTO
```

---

## Research Extensions

This system is designed as a foundation for research. Extend it with:

1. **Empirical Validator** — Run code with increasing n, fit the time curve, compare to prediction
2. **Hallucination Detector** — Compare static result vs LLM result, flag mismatches
3. **Benchmark Dataset** — Run all sample algorithms, measure accuracy
4. **Master Theorem Solver** — Formally resolve divide-and-conquer recurrences
5. **Average-case Analysis** — Probabilistic analysis for algorithms like QuickSort

---

## Limitations (Be Honest in Research)

- Loop bounds assumed to be n (unknown bounds flagged)
- Recursion resolution is heuristic (Master Theorem not fully implemented)
- Dynamic programming patterns not fully recognized
- Input-dependent behavior not modeled
- Amortized complexity not analyzed

These are your research contribution areas.
