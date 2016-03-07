package com.caseystella.analytics.timeseries.tsdb;

import com.caseystella.analytics.DataPoint;
import com.caseystella.analytics.distribution.TimeRange;
import com.caseystella.analytics.timeseries.TimeseriesDatabaseHandler;
import com.caseystella.analytics.timeseries.TimeseriesDatabaseHandlers;
import com.caseystella.analytics.util.ConfigUtil;
import com.google.common.base.Function;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.*;
import net.opentsdb.utils.Config;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TSDBHandler implements TimeseriesDatabaseHandler {
    public static final String TSDB_CONFIG = "tsdb_config";
    public static final String DOWNSAMPLE_AGGREGATOR_CONFIG = "downsample_aggregator";
    public static final String DOWNSAMPLE_INTERVAL_CONFIG = "downsample_interval";
    private TSDB tsdb;
    private Aggregator aggregator = null;
    private long sampleInterval;


    public void persist(String metric, DataPoint dp, Map<String, String> tags) {
        persist(metric, dp, tags , null);
    }
    @Override
    public void persist(String metric, DataPoint dp, Map<String, String> tags, final Function<Object, Void> callback) {
        try {
            if(callback == null) {
                tsdb.addPoint(metric, dp.getTimestamp(), dp.getValue(), tags).joinUninterruptibly();
            }
            else {

                Deferred<Object> ret = tsdb.addPoint(metric, dp.getTimestamp(), dp.getValue(), tags);
                ret.addCallback(new Callback<Object, Object>() {
                    @Override
                    public Object call(Object o) throws Exception {
                        return callback.apply(o);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<DataPoint> retrieve(String metric, DataPoint pt, TimeRange range) {
        Query q = tsdb.newQuery();
        q.setStartTime(range.getBegin());
        q.setEndTime(pt.getTimestamp());
        Map<String, String> tags =
                new HashMap<String, String>() {{
                            put(TimeseriesDatabaseHandlers.SERIES_TAG_KEY, TimeseriesDatabaseHandlers.SERIES_TAG_VALUE);
                        }};
        q.setTimeSeries(metric
                       , tags
                       , Aggregators.AVG
                       , false
                       );
        if(aggregator != null && sampleInterval > 0) {
            q.downsample(sampleInterval, aggregator);
        }
        net.opentsdb.core.DataPoints[] datapoints = q.run();
        if(datapoints.length == 0) {
            throw new RuntimeException("Unable to retrieve points (empty set)");
        }
        List<DataPoint> ret = new ArrayList<>();
        for(int j = 0;j < datapoints.length;++j) {
            DataPoints dp = datapoints[j];
            for (int i = 0; i < dp.size(); ++i) {
                double val = dp.doubleValue(i);
                long ts = dp.timestamp(i);
                if (ts >= q.getEndTime()) {
                    break;
                }
                if(ts >= q.getStartTime()) {
                    ret.add(new DataPoint(ts, val, dp.getTags(), metric));
                }
            }
        }
        return ret;
    }

    public static class TSDBConfig extends Config {
        public TSDBConfig(Map<String, String> props) throws IOException {
            this(props.entrySet());
        }
        public TSDBConfig(Iterable<Map.Entry<String, String>> props) throws IOException {
            super(false);
            for(Map.Entry<String, String> prop : props) {
                properties.put(prop.getKey(), prop.getValue());
            }
        }
    }

    @Override
    public void configure(Map<String, Object> config) {
        try {
            Object tsdbConfigObj = config.get(TSDB_CONFIG);
            if(tsdbConfigObj == null) {
                tsdb = new TSDB(new TSDBConfig(HBaseConfiguration.create()));
            }
            else if(tsdbConfigObj instanceof Map) {
                Map<String, String> tsdbConfig = (Map<String, String>) tsdbConfigObj;
                tsdb = new TSDB(new TSDBConfig(tsdbConfig == null?  new HashMap<String, String>() :tsdbConfig));
            }
            else if(tsdbConfigObj instanceof Configuration){
                tsdb = new TSDB(new TSDBConfig((Configuration) tsdbConfigObj));
            }
            else if(tsdbConfigObj instanceof Config) {
                tsdb = new TSDB((Config) tsdbConfigObj);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize TSDB connector.", e);
        }
        {
            Object aggObj = config.get(DOWNSAMPLE_AGGREGATOR_CONFIG);
            if(aggObj != null) {
                aggregator = Aggregators.get(aggObj.toString());
            }
        }
        {
            Object aggObj = config.get(DOWNSAMPLE_INTERVAL_CONFIG);
            if(aggObj != null) {
                sampleInterval = ConfigUtil.INSTANCE.coerceLong(DOWNSAMPLE_INTERVAL_CONFIG, aggObj);
            }
        }
    }
}
