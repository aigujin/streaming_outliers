package com.caseystella.analytics.outlier.streaming.mad;

import com.caseystella.analytics.DataPoint;
import com.caseystella.analytics.outlier.streaming.OutlierConfig;
import com.caseystella.analytics.outlier.Severity;
import com.caseystella.analytics.util.JSONUtil;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SketchyMovingMADTest {

    /**
     {
     "rotationPolicy" : {
                        "type" : "BY_AMOUNT"
                       ,"amount" : 100
                       ,"unit" : "POINTS"
                        }
     ,"chunkingPolicy" : {
                        "type" : "BY_AMOUNT"
                       ,"amount" : 10
                       ,"unit" : "POINTS"
                         }
     ,"globalStatistics" : {
                         "min" : -10000
                         }
     ,"outlierAlgorithm" : "SKETCHY_MOVING_MAD"
     ,"config" : {
                 "minAmountToPredict" : 100
                ,"zscoreCutoffs" : {
                                    "NORMAL" : 3.5
                                   ,"MODERATE_OUTLIER" : 5
                                   }
                 }
     }
     */
    @Multiline
    public static String madConfig;

    public static double getValAtModifiedZScore(double zScore, double mad, double median) {
        return (mad*zScore)/SketchyMovingMAD.ZSCORE + median;

    }

    @Test
    public void testSketchyMovingMAD() throws IOException {
        Random r = new Random(0);
        List<DataPoint> points = new ArrayList<>();
        DescriptiveStatistics stats = new DescriptiveStatistics();
        DescriptiveStatistics medianStats = new DescriptiveStatistics();
        OutlierConfig config = JSONUtil.INSTANCE.load(madConfig, OutlierConfig.class);
        SketchyMovingMAD madAlgo = ((SketchyMovingMAD)config.getSketchyOutlierAlgorithm()).withConfig(config);
        int i = 0;
        for(i = 0; i < 10000;++i) {
            double val = r.nextDouble() * 1000 - 10000;
            stats.addValue(val);
            DataPoint dp = (new DataPoint(i, val, null, "foo"));
            madAlgo.analyze(dp);
            points.add(dp);
        }
        for(DataPoint dp : points) {
            medianStats.addValue(Math.abs(dp.getValue() - stats.getPercentile(50)));
        }
        double mad = medianStats.getPercentile(50);
        double median = stats.getPercentile(50);
        {
            double val = getValAtModifiedZScore(3.6, mad, median);
            System.out.println("MODERATE => " + val);
            DataPoint dp = (new DataPoint(i++, val, null, "foo"));
            Severity s = madAlgo.analyze(dp).getSeverity();
            Assert.assertTrue(s == Severity.MODERATE_OUTLIER );
        }
        {
            double val = getValAtModifiedZScore(6, mad, median);
            System.out.println("SEVERE => " + val);
            DataPoint dp = (new DataPoint(i++, val, null, "foo"));
            Severity s = madAlgo.analyze(dp).getSeverity();
            Assert.assertTrue(s == Severity.SEVERE_OUTLIER );
        }

        Assert.assertTrue(madAlgo.getMedianDistributions().get("foo").getAmount() <= 110);
        Assert.assertTrue(madAlgo.getMedianDistributions().get("foo").getChunks().size() <= 12);
    }
}
