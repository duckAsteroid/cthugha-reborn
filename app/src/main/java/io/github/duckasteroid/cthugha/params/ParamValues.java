package io.github.duckasteroid.cthugha.params;

/**
 * Applies a raw text value to a leaf node, sharing the coercion/validation rules between the
 * remote PATCH-leaf route ({@code RemoteServer}) and trigger-fired value sets
 * ({@code ActionTrigger}) so the two call sites can't drift apart.
 *
 * <p>A {@link StringValue} is assigned the text directly. Any other {@link AbstractValue} is
 * parsed as a {@code double} and range-checked against {@link AbstractValue#getMin()}/{@link
 * AbstractValue#getMax()} before being stored — this covers {@code BooleanParameter} (0/1),
 * {@code IntegerParameter}/{@code LongParameter} (rounded), {@code DoubleParameter}, and {@code
 * EnumParameter} (index) uniformly, since they all funnel through {@link
 * AbstractValue#setValue(Number)}.</p>
 */
public final class ParamValues {

    public enum ApplyResult { OK, NOT_A_LEAF, PARSE_ERROR, OUT_OF_RANGE }

    private ParamValues() {}

    /** Applies {@code raw} to {@code node}; does not modify {@code node} unless the result is {@link ApplyResult#OK}. */
    public static ApplyResult applyText(Node node, String raw) {
        if (node instanceof StringValue sv) {
            sv.setValue(raw);
            return ApplyResult.OK;
        }
        if (!(node instanceof AbstractValue param)) {
            return ApplyResult.NOT_A_LEAF;
        }
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException | NullPointerException e) {
            return ApplyResult.PARSE_ERROR;
        }
        if (value < param.getMin().doubleValue() || value > param.getMax().doubleValue()) {
            return ApplyResult.OUT_OF_RANGE;
        }
        param.setValue(value);
        return ApplyResult.OK;
    }
}
