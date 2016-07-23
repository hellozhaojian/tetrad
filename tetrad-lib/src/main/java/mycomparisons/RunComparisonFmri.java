///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package mycomparisons;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LoadDataFromFileWithoutGraph;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;

/**
 * @author Joseph Ramsey
 */
public class RunComparisonFmri {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.put("penaltyDiscount", 30);
        parameters.put("fgsDepth", -1);

        Statistics statistics = new Statistics(); // Just need a blank one--no graphs are provided.

        Algorithms algorithms = new Algorithms(); // Just need the one algorithm.
        algorithms.add(new Fgs(new SemBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new LoadDataFromFileWithoutGraph(
                "/Users/jdramsey/BitTorrent Sync/418_datapoints/Hipp_L_first10_480.txt"));

        Comparison comparison = new Comparison();
        comparison.setSaveGraphs(true);

        comparison.compareAlgorithms("comparison/Comparison.30.txt", simulations, algorithms,
                statistics, parameters);
    }
}



