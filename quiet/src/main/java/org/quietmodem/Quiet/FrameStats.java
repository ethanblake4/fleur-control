package org.quietmodem.Quiet;


public class FrameStats {
    private Complex[] symbols;
    private float receivedSignalStrengthIndicator;
    private float errorVectorMagnitude;
    private boolean isFrameValid;

    FrameStats(Complex[] symbols, float receivedSignalStrengthIndicator, float errorVectorMagnitude, boolean isFrameValid) {
        this.symbols = symbols;
        this.receivedSignalStrengthIndicator = receivedSignalStrengthIndicator;
        this.errorVectorMagnitude = errorVectorMagnitude;
        this.isFrameValid = isFrameValid;
    }

    public Complex[] getSymbols() { return this.symbols; }
    public float getReceivedSignalStrengthIndicator() { return this.receivedSignalStrengthIndicator; }
    public float getErrorVectorMagnitude() { return this.errorVectorMagnitude; }
    public boolean getIsFrameValid() { return this.isFrameValid; }
    public String toString(){
        StringBuilder sb = new StringBuilder("frameStats { recievedSigStren: ");
        sb.append(receivedSignalStrengthIndicator);
        sb.append(", errorVectorMagnitude: ");
        sb.append(errorVectorMagnitude);
        sb.append(", isFrameValid: ");
        sb.append(isFrameValid);
        sb.append(" }");
        return sb.toString();
    }

    static {
        QuietInit.init();
    }
}
