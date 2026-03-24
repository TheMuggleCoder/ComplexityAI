package com.muggle.analyzer.model;

/**
 * Represents a Big-O complexity as a mathematical model.
 *
 * Encoding:
 *   nPower=0, logPower=0, !isExponential  → O(1)
 *   nPower=1, logPower=0                  → O(n)
 *   nPower=0, logPower=1                  → O(log n)
 *   nPower=1, logPower=1                  → O(n log n)
 *   nPower=2, logPower=0                  → O(n²)
 *   isExponential=true                    → O(2ⁿ)
 */
public class Complexity {

    public int nPower;
    public int logPower;
    public boolean isExponential;

    // ── Constants ───────────────────────────────────────
    public static Complexity CONSTANT()    { return new Complexity(0, 0); }
    public static Complexity LINEAR()      { return new Complexity(1, 0); }
    public static Complexity LOG()         { return new Complexity(0, 1); }
    public static Complexity LINEARITHMIC(){ return new Complexity(1, 1); }
    public static Complexity QUADRATIC()   { return new Complexity(2, 0); }
    public static Complexity CUBIC()       { return new Complexity(3, 0); }
    public static Complexity EXPONENTIAL() {
        Complexity c = new Complexity(0, 0);
        c.isExponential = true;
        return c;
    }

    public Complexity(int nPower, int logPower) {
        this.nPower = nPower;
        this.logPower = logPower;
        this.isExponential = false;
    }

    /**
     * Multiply complexities — used for nested structures.
     * O(n) * O(n) = O(n²)
     * O(n) * O(log n) = O(n log n)
     */
    public Complexity multiply(Complexity other) {
        if (this.isExponential || other.isExponential) {
            return EXPONENTIAL();
        }
        return new Complexity(
            this.nPower + other.nPower,
            this.logPower + other.logPower
        );
    }

    /**
     * Add complexities — used for sequential code (take the dominant term).
     * O(n) + O(n²) = O(n²)
     * O(n log n) + O(n) = O(n log n)
     */
    public Complexity add(Complexity other) {
        if (this.isExponential) return this;
        if (other.isExponential) return other;

        // Higher nPower dominates
        if (this.nPower > other.nPower) return this;
        if (other.nPower > this.nPower) return other;

        // Same nPower: higher logPower dominates
        return this.logPower >= other.logPower ? this : other;
    }

    /**
     * Convert to standard Big-O string notation.
     */
    public String toBigO() {
        if (isExponential) return "O(2ⁿ)";
        if (nPower == 0 && logPower == 0) return "O(1)";
        if (nPower == 0 && logPower == 1) return "O(log n)";
        if (nPower == 0)                  return "O(log" + logPower + " n)";
        if (nPower == 1 && logPower == 0) return "O(n)";
        if (nPower == 1 && logPower == 1) return "O(n log n)";
        if (nPower == 1)                  return "O(n log" + logPower + " n)";
        if (logPower == 0)                return "O(n" + superscript(nPower) + ")";
        if (logPower == 1)                return "O(n" + superscript(nPower) + " log n)";
        return "O(n" + superscript(nPower) + " log" + logPower + " n)";
    }

    /**
     * Returns true if this is strictly more complex than O(1).
     */
    public boolean isNonConstant() {
        return isExponential || nPower > 0 || logPower > 0;
    }

    private String superscript(int n) {
        return switch (n) {
            case 2 -> "²";
            case 3 -> "³";
            case 4 -> "⁴";
            case 5 -> "⁵";
            default -> "^" + n;
        };
    }

    @Override
    public String toString() {
        return toBigO();
    }
}
