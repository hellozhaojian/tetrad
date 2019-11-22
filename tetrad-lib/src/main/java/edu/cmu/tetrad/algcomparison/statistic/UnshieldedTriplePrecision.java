package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TriangleConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class UnshieldedTriplePrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "UnshTripPrec";
    }

    @Override
    public String getDescription() {
        return "Unshielded triple precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);
        double num = confusion.getTp();

        double den = confusion.getTp() + confusion.getFp();

        return num / den;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
