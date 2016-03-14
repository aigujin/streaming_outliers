package com.caseystella.analytics.extractor;

import com.caseystella.analytics.DataPoint;
import com.google.common.collect.Iterables;
import junit.framework.Assert;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import java.text.SimpleDateFormat;

public class ExtractorsTest {

    /**
     {
         "keyConverter" : "NOOP"
       , "valueConverter" : "CSVConverter"
       , "valueConverterConfig" : {
                                    "columnMap" : {
                                                  "sensor1_ts" : 0
                                                 ,"sensor1_value" : 1
                                                 ,"sensor2_ts" : 4
                                                 ,"sensor2_value" : 5
                                                 ,"plant_id" : 7
                                                  }
                                  }
       , "measurements" : [
            {
             "source" : "sensor_1"
            ,"timestampField" : "sensor1_ts"
            ,"measurementField" : "sensor1_value"
            ,"metadataFields" : [ "plant_id"]
            }
           ,{
             "source" : "sensor_2"
            ,"timestampField" : "sensor2_ts"
            ,"measurementField" : "sensor2_value"
            ,"metadataFields" : [ "plant_id"]
            }
                          ]
     }
     */
    @Multiline
    public static String extractorConfig;

    @Test
    public void testExtractor() throws Exception {
        Assert.assertNotNull(extractorConfig);
        DataPointExtractorConfig config = DataPointExtractorConfig.load(extractorConfig);
        DataPointExtractor extractor = new DataPointExtractor().withConfig(config);
        {
            Iterable<DataPoint> dataPoints = extractor.extract(Bytes.toBytes(0L), Bytes.toBytes("   #0,100,foo,bar,50,7,grok,plant_1,baz"), true);
            Assert.assertEquals(0, Iterables.size(dataPoints));
        }
        {
            Iterable<DataPoint> dataPoints = extractor.extract(Bytes.toBytes(0L), Bytes.toBytes("0,100,foo,bar,50,7,grok,plant_1,baz"), true);
            Assert.assertEquals(2, Iterables.size(dataPoints));
            {
                DataPoint dp = Iterables.getFirst(dataPoints, null);
                Assert.assertNotNull(dp);
                Assert.assertEquals(dp.getSource(), "sensor_1");
                Assert.assertEquals(dp.getTimestamp(), 0L);
                Assert.assertEquals(dp.getValue(), 100d);
                Assert.assertEquals(dp.getMetadata().size(), 1);
                Assert.assertEquals(dp.getMetadata().get("plant_id"), "plant_1");
            }
            {
                DataPoint dp = Iterables.getLast(dataPoints, null);
                Assert.assertNotNull(dp);
                Assert.assertEquals(dp.getSource(), "sensor_2");
                Assert.assertEquals(dp.getTimestamp(), 50L);
                Assert.assertEquals(dp.getValue(), 7d);
                Assert.assertEquals(dp.getMetadata().size(), 1);
                Assert.assertEquals(dp.getMetadata().get("plant_id"), "plant_1");
            }
        }
    }
    /**
     {
         "valueConverter" : "CSVConverter"
       , "valueConverterConfig" : {
                                    "columnMap" : {
                                                  "physician_specialty" : 1
                                                 ,"transaction_date" : 2
                                                 ,"transaction_amount" : 3
                                                 ,"transaction_reason" : 4
                                                  }
                                  }
       , "measurements" : [
            {
             "sourceFields" : [ "physician_specialty", "transaction_reason" ]
            ,"timestampField" : "transaction_date"
            ,"timestampConverter" : "DateConverter"
            ,"timestampConverterConfig" : {
                                            "format" : "yyyy-MM-dd"
                                          }
            ,"measurementField" : "transaction_amount"
            }
                          ]
     }
     */
    @Multiline
    public static String fraudExtractorConfig;
    @Test
    public void testFraudExtractor() throws Exception {
        Assert.assertNotNull(extractorConfig);
        DataPointExtractorConfig config = DataPointExtractorConfig.load(fraudExtractorConfig);
        DataPointExtractor extractor = new DataPointExtractor().withConfig(config);
        {
            Iterable<DataPoint> dataPoints = extractor.extract(Bytes.toBytes(0L), Bytes.toBytes("\"id_1\",\"optometrist\",\"2016-02-16\",\"75.00\",\"Food\""), true);
            Assert.assertEquals(1, Iterables.size(dataPoints));
            DataPoint dp = Iterables.get(dataPoints, 0);
            Assert.assertEquals("optometrist.Food", dp.getSource());
            Assert.assertEquals(2, dp.getMetadata().size());
            Assert.assertEquals("Food", dp.getMetadata().get("transaction_reason"));
            Assert.assertEquals("optometrist", dp.getMetadata().get("physician_specialty"));
            Assert.assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2016-02-16").getTime(), dp.getTimestamp());
            Assert.assertEquals(75, dp.getValue(), 1e-5);
        }
    }
}
