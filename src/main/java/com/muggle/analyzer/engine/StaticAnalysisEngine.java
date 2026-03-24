package com.muggle.analyzer.engine;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.muggle.analyzer.model.AnalysisResult;
import com.muggle.analyzer.model.Complexity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core static analysis engine.
 *
 * Pipeline:
 *   1. Parse code into AST via JavaParser
 *   2. Walk AST recursively
 *   3. Detect loop patterns, nesting, recursion
 *   4. Compute time + space complexity via Complexity algebra
 *   5. Generate step-by-step reasoning
 */
@Service
public class StaticAnalysisEngine {

    // ── Public API ──────────────────────────────────────────────────

    public AnalysisResult analyze(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return AnalysisResult.error("No code provided.");
        }

        List<String> steps = new ArrayList<>();
        CompilationUnit cu = tryParse(rawCode);

        if (cu == null) {
            return AnalysisResult.error(
                "Could not parse the provided code as valid Java. " +
                "Make sure the code is syntactically correct."
            );
        }

        // Collect all method names (needed for recursion detection)
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        Set<String> methodNames = methods.stream()
            .map(MethodDeclaration::getNameAsString)
            .collect(Collectors.toSet());

        // ── Time Complexity Analysis ──
        Complexity timeComplexity = Complexity.CONSTANT();
        boolean hasRecursion = false;
        int maxRecursiveCallCount = 0;
        String recursiveMethodName = null;

        for (MethodDeclaration method : methods) {
            String mName = method.getNameAsString();

            // Detect recursive calls
            List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class);
            long recCount = calls.stream()
                .filter(c -> c.getNameAsString().equals(mName))
                .count();

            if (recCount > 0) {
                hasRecursion = true;
                if (recCount > maxRecursiveCallCount) {
                    maxRecursiveCallCount = (int) recCount;
                    recursiveMethodName = mName;
                }
                steps.add("Recursion detected in `" + mName + "()` — calls itself "
                    + recCount + " time(s) per invocation");
            }

            // Analyze method body
            if (method.getBody().isPresent()) {
                Complexity mc = analyzeBlock(method.getBody().get(), methodNames, steps);
                timeComplexity = timeComplexity.add(mc);
            }
        }

        // If no methods found (bare code block), analyze all top-level blocks
        if (methods.isEmpty()) {
            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                timeComplexity = timeComplexity.add(analyzeBlock(block, methodNames, steps));
            }
            // Also find bare for/while loops at compilation unit level
            for (Statement stmt : cu.findAll(ForStmt.class)) {
                timeComplexity = timeComplexity.add(analyzeStatement(stmt, methodNames, steps));
            }
        }

        // ── Recursion Complexity Override ──
        if (hasRecursion) {
            timeComplexity = resolveRecursiveComplexity(
                timeComplexity, maxRecursiveCallCount, recursiveMethodName, steps
            );
        }

        if (steps.isEmpty()) {
            steps.add("No loops or recursion detected — all operations are constant time O(1)");
        }

        // ── Space Complexity Analysis ──
        boolean finalHasRecursion = hasRecursion;
        int finalRecCallCount = maxRecursiveCallCount;
        Complexity spaceComplexity = analyzeSpace(cu, finalHasRecursion, finalRecCallCount, steps);

        // ── Build Labels & Verdict ──
        String timeLabel  = describeTime(timeComplexity);
        String spaceLabel = describeSpace(spaceComplexity);
        String verdict    = buildVerdict(timeComplexity, spaceComplexity, hasRecursion);

        // ── Confidence ──
        double confidence = computeConfidence(timeComplexity, hasRecursion);

        // ── Notes ──
        String notes = buildNotes(hasRecursion, maxRecursiveCallCount);

        return new AnalysisResult(
            timeComplexity.toBigO(),
            timeLabel,
            spaceComplexity.toBigO(),
            spaceLabel,
            steps,
            verdict,
            notes,
            confidence,
            "static_analysis",
            hasRecursion
        );
    }

    // ── Parsing ────────────────────────────────────────────────────

    /**
     * Tries three parse strategies, returning the first that succeeds.
     */
    private CompilationUnit tryParse(String code) {
        // Strategy 1: parse as-is (works for full class definitions)
        try { return StaticJavaParser.parse(code); } catch (Exception ignored) {}

        // Strategy 2: wrap inside a class (works for method declarations)
        try {
            return StaticJavaParser.parse("public class AnalysisTarget {\n" + code + "\n}");
        } catch (Exception ignored) {}

        // Strategy 3: wrap as method body (works for bare statement blocks)
        try {
            return StaticJavaParser.parse(
                "public class AnalysisTarget {\n" +
                "  public void analyze(int n, int[] arr) {\n" +
                code + "\n  }\n}"
            );
        } catch (Exception ignored) {}

        return null;
    }

    // ── Block & Statement Analysis ──────────────────────────────────

    private Complexity analyzeBlock(BlockStmt block, Set<String> methodNames, List<String> steps) {
        Complexity result = Complexity.CONSTANT();
        for (Statement stmt : block.getStatements()) {
            Complexity sc = analyzeStatement(stmt, methodNames, steps);
            result = result.add(sc);
        }
        return result;
    }

    private Complexity analyzeStatement(Statement stmt, Set<String> methodNames, List<String> steps) {
        if (stmt instanceof ForStmt) {
            return analyzeForLoop((ForStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof ForEachStmt) {
            return analyzeForEach((ForEachStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof WhileStmt) {
            return analyzeWhile((WhileStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof DoStmt) {
            return analyzeDoWhile((DoStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof IfStmt) {
            return analyzeIf((IfStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof BlockStmt) {
            return analyzeBlock((BlockStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof SwitchStmt) {
            return analyzeSwitch((SwitchStmt) stmt, methodNames, steps);
        }
        if (stmt instanceof TryStmt) {
            return analyzeTry((TryStmt) stmt, methodNames, steps);
        }
        // All other statements (assignments, returns, etc.) are O(1)
        return Complexity.CONSTANT();
    }

    // ── Loop Analysis ───────────────────────────────────────────────

    private Complexity analyzeForLoop(ForStmt forStmt, Set<String> methodNames, List<String> steps) {
        boolean isLog = isLogarithmicUpdate(forStmt.getUpdate());
        Complexity loopC = isLog ? Complexity.LOG() : Complexity.LINEAR();
        String loopType  = isLog ? "O(log n)" : "O(n)";

        Complexity bodyC = analyzeStatement(forStmt.getBody(), methodNames, steps);

        if (!bodyC.isNonConstant()) {
            // Simple loop: O(n) or O(log n)
            steps.add("`for` loop with " + (isLog ? "multiplicative" : "linear")
                + " counter update → " + loopType);
            return loopC;
        } else {
            // Nested: multiply outer × inner
            Complexity total = loopC.multiply(bodyC);
            steps.add("`for` loop (" + loopType + ") contains nested "
                + bodyC.toBigO() + " block → " + total.toBigO() + " combined");
            return total;
        }
    }

    private Complexity analyzeForEach(ForEachStmt forEachStmt, Set<String> methodNames, List<String> steps) {
        Complexity bodyC = analyzeStatement(forEachStmt.getBody(), methodNames, steps);
        Complexity loopC = Complexity.LINEAR();

        if (!bodyC.isNonConstant()) {
            steps.add("`for-each` loop iterates over collection once → O(n)");
            return loopC;
        } else {
            Complexity total = loopC.multiply(bodyC);
            steps.add("`for-each` loop (O(n)) nesting " + bodyC.toBigO() + " → " + total.toBigO());
            return total;
        }
    }

    private Complexity analyzeWhile(WhileStmt whileStmt, Set<String> methodNames, List<String> steps) {
        String bodyStr = whileStmt.getBody().toString();
        String condStr = whileStmt.getCondition().toString();

        boolean isLog = isLogarithmicBodyPattern(bodyStr, condStr);
        Complexity loopC = isLog ? Complexity.LOG() : Complexity.LINEAR();
        String loopType  = isLog ? "O(log n)" : "O(n)";

        Complexity bodyC = analyzeStatement(whileStmt.getBody(), methodNames, steps);

        if (!bodyC.isNonConstant()) {
            steps.add("`while` loop" + (isLog ? " (halving pattern detected)" : "") + " → " + loopType);
            return loopC;
        } else {
            Complexity total = loopC.multiply(bodyC);
            steps.add("`while` loop (" + loopType + ") containing " + bodyC.toBigO() + " → " + total.toBigO());
            return total;
        }
    }

    private Complexity analyzeDoWhile(DoStmt doStmt, Set<String> methodNames, List<String> steps) {
        String bodyStr = doStmt.getBody().toString();
        boolean isLog  = bodyStr.contains("/ 2") || bodyStr.contains("/= 2") || bodyStr.contains(">> 1");
        Complexity loopC = isLog ? Complexity.LOG() : Complexity.LINEAR();

        Complexity bodyC = analyzeStatement(doStmt.getBody(), methodNames, steps);
        Complexity total = bodyC.isNonConstant() ? loopC.multiply(bodyC) : loopC;

        steps.add("`do-while` loop → " + total.toBigO());
        return total;
    }

    // ── Conditional Analysis ────────────────────────────────────────

    private Complexity analyzeIf(IfStmt ifStmt, Set<String> methodNames, List<String> steps) {
        Complexity thenC = analyzeStatement(ifStmt.getThenStmt(), methodNames, steps);
        Complexity elseC = Complexity.CONSTANT();

        if (ifStmt.getElseStmt().isPresent()) {
            elseC = analyzeStatement(ifStmt.getElseStmt().get(), methodNames, steps);
        }

        Complexity worst = thenC.add(elseC);
        if (worst.isNonConstant()) {
            steps.add("Conditional branch — taking worst case: " + worst.toBigO());
        }
        return worst;
    }

    private Complexity analyzeSwitch(SwitchStmt switchStmt, Set<String> methodNames, List<String> steps) {
        Complexity worst = Complexity.CONSTANT();
        for (SwitchEntry entry : switchStmt.getEntries()) {
            for (Statement s : entry.getStatements()) {
                worst = worst.add(analyzeStatement(s, methodNames, steps));
            }
        }
        if (worst.isNonConstant()) {
            steps.add("`switch` statement — worst-case branch: " + worst.toBigO());
        }
        return worst;
    }

    private Complexity analyzeTry(TryStmt tryStmt, Set<String> methodNames, List<String> steps) {
        Complexity c = analyzeBlock(tryStmt.getTryBlock(), methodNames, steps);
        for (CatchClause cc : tryStmt.getCatchClauses()) {
            c = c.add(analyzeBlock(cc.getBody(), methodNames, steps));
        }
        return c;
    }

    // ── Recursion Resolution ────────────────────────────────────────

    /**
     * Applies common recurrence relations to determine recursive complexity.
     *
     * T(n) = 2T(n-1) + O(1)  → O(2ⁿ)  [two recursive calls, linear decrement]
     * T(n) = T(n-1) + O(1)   → O(n)   [single recursive call, linear decrement]
     * T(n) = 2T(n/2) + O(n)  → O(n log n) [merge-sort pattern]
     */
    private Complexity resolveRecursiveComplexity(
            Complexity currentC, int recCallCount,
            String methodName, List<String> steps) {

        if (recCallCount >= 2) {
            // Multiple branches per call → exponential (e.g., naive Fibonacci)
            steps.add("Multiple recursive calls (" + recCallCount + ") per invocation → "
                + "recurrence T(n) = " + recCallCount + "T(n−1) + O(1) → O(2ⁿ) exponential");
            return Complexity.EXPONENTIAL();
        }

        // Single recursive call
        if (currentC.nPower >= 1) {
            // T(n) = T(n/2) + O(n) → O(n log n) [like merge sort body]
            if (currentC.nPower == 1) {
                steps.add("Single recursion with O(n) work per level → "
                    + "T(n) = T(n/2) + O(n) → O(n log n)");
                return Complexity.LINEARITHMIC();
            }
        }

        // Default: T(n) = T(n-1) + O(1) → O(n) linear recursion
        if (!currentC.isNonConstant()) {
            steps.add("Single recursive call with O(1) work per level → "
                + "T(n) = T(n−1) + O(1) → O(n) linear recursion");
            return Complexity.LINEAR();
        }

        return currentC;
    }

    // ── Space Complexity Analysis ───────────────────────────────────

    private Complexity analyzeSpace(
            CompilationUnit cu, boolean hasRecursion,
            int recCallCount, List<String> steps) {

        Complexity space = Complexity.CONSTANT();

        // Check for explicit collection/array allocations
        boolean foundAlloc = false;

        List<ObjectCreationExpr> objCreations = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr oce : objCreations) {
            String type = oce.getTypeAsString();
            if (isCollectionType(type)) {
                space = space.add(Complexity.LINEAR());
                steps.add("Collection allocation (`" + type + "`) → O(n) auxiliary space");
                foundAlloc = true;
                break;
            }
        }

        // Array creation expressions (new int[n], new String[n], etc.)
        List<ArrayCreationExpr> arrays = cu.findAll(ArrayCreationExpr.class);
        if (!arrays.isEmpty() && !foundAlloc) {
            space = space.add(Complexity.LINEAR());
            steps.add("Array allocation (`new " + arrays.get(0).getElementType() + "[...]`) → O(n) space");
        }

        // Array type variable declarations (int[], String[], etc.)
        if (!foundAlloc && arrays.isEmpty()) {
            List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class);
            for (VariableDeclarator v : vars) {
                String typeName = v.getTypeAsString();
                if (typeName.endsWith("[]") || isCollectionType(typeName)) {
                    space = space.add(Complexity.LINEAR());
                    steps.add("Array/collection variable `" + typeName + " " + v.getNameAsString()
                        + "` → O(n) auxiliary space");
                    break;
                }
            }
        }

        // Recursion contributes call stack space
        if (hasRecursion) {
            if (recCallCount >= 2) {
                // Binary tree of calls: stack depth is O(n) in worst case
                space = space.add(Complexity.LINEAR());
                steps.add("Recursive call stack: depth O(n) even with branching → O(n) stack space");
            } else {
                // Linear recursion: O(n) stack frames
                space = space.add(Complexity.LINEAR());
                steps.add("Linear recursion: O(n) stack frames on the call stack → O(n) space");
            }
        }

        return space;
    }

    // ── Pattern Detection Helpers ───────────────────────────────────

    /**
     * Returns true if the for-loop update contains a multiplicative or division operator,
     * indicating a logarithmic loop (i *= 2, i /= 2, i >>= 1, etc.).
     */
    private boolean isLogarithmicUpdate(NodeList<Expression> updates) {
        return updates.stream().anyMatch(u -> {
            String s = u.toString();
            return s.contains("*=") || s.contains("/=") || s.contains("<<=") || s.contains(">>=");
        });
    }

    /**
     * Returns true if a while/do-while body shows a halving pattern,
     * indicating a logarithmic loop.
     */
    private boolean isLogarithmicBodyPattern(String bodyStr, String condStr) {
        return bodyStr.contains("/ 2")  || bodyStr.contains("/2")
            || bodyStr.contains(">> 1") || bodyStr.contains("/= 2")
            || bodyStr.contains("*= 2") || bodyStr.contains(">>= 1")
            || (condStr.contains("left") && condStr.contains("right")); // binary search pattern
    }

    /**
     * Returns true if the type name indicates a Java collection.
     */
    private boolean isCollectionType(String type) {
        return type.startsWith("ArrayList") || type.startsWith("LinkedList")
            || type.startsWith("HashMap")   || type.startsWith("TreeMap")
            || type.startsWith("HashSet")   || type.startsWith("TreeSet")
            || type.startsWith("Stack")     || type.startsWith("Queue")
            || type.startsWith("PriorityQueue") || type.startsWith("ArrayDeque")
            || type.startsWith("List")      || type.startsWith("Map")
            || type.startsWith("Set")       || type.startsWith("Deque");
    }

    // ── Label & Verdict Builders ────────────────────────────────────

    private String describeTime(Complexity c) {
        if (c.isExponential)                       return "Exponential — doubles with each additional element";
        if (c.nPower == 0 && c.logPower == 0)     return "Constant — completely independent of input size";
        if (c.nPower == 0 && c.logPower == 1)     return "Logarithmic — problem halves each iteration";
        if (c.nPower == 1 && c.logPower == 0)     return "Linear — grows proportionally with input";
        if (c.nPower == 1 && c.logPower == 1)     return "Linearithmic — efficient divide-and-conquer";
        if (c.nPower == 2 && c.logPower == 0)     return "Quadratic — grows with square of input size";
        if (c.nPower == 3 && c.logPower == 0)     return "Cubic — grows with cube of input size";
        return "Polynomial — grows polynomially with input";
    }

    private String describeSpace(Complexity c) {
        if (c.nPower == 0 && c.logPower == 0) return "Constant — fixed memory regardless of input";
        if (c.nPower == 1 && c.logPower == 0) return "Linear — memory proportional to input size";
        if (c.nPower == 0 && c.logPower == 1) return "Logarithmic — call stack depth is log n";
        return "Polynomial space usage";
    }

    private String buildVerdict(Complexity time, Complexity space, boolean hasRecursion) {
        return "This algorithm has " + time.toBigO() + " time complexity"
            + (hasRecursion ? " (driven by recursive structure)" : "")
            + " and uses " + space.toBigO() + " auxiliary space.";
    }

    private String buildNotes(boolean hasRecursion, int recCallCount) {
        if (!hasRecursion) return null;
        if (recCallCount >= 2) {
            return "Exponential recursion detected. Consider memoization (top-down DP) "
                + "or tabulation (bottom-up DP) to reduce to O(n) time and O(n) space.";
        }
        return "Recursive analysis uses heuristic recurrence matching. "
            + "For divide-and-conquer patterns (e.g. T(n)=2T(n/2)+O(n)), "
            + "verify with the Master Theorem manually.";
    }

    private double computeConfidence(Complexity c, boolean hasRecursion) {
        if (hasRecursion) return 0.78;            // heuristic recursion resolution
        if (c.nPower == 0 && c.logPower == 0) return 0.99;  // trivially constant
        if (c.isExponential)                   return 0.82;
        return 0.91;                              // loop-based, high confidence
    }
}
