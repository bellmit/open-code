package com.winterwell.datalog.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.winterwell.datalog.DataLog;
import com.winterwell.datalog.DataLogConfig;
import com.winterwell.datalog.DataLogEvent;
import com.winterwell.datalog.Dataspace;
import com.winterwell.datalog.ESStorage;
import com.winterwell.datalog.IDataLogAdmin;
import com.winterwell.es.ESType;
import com.winterwell.es.client.BulkRequestBuilder;
import com.winterwell.es.client.BulkResponse;
import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.IESResponse;
import com.winterwell.es.client.IndexRequestBuilder;
import com.winterwell.es.client.KRefresh;
import com.winterwell.es.client.TransformRequestBuilder;
import com.winterwell.es.client.admin.CreateIndexRequest;
import com.winterwell.es.client.admin.PutMappingRequestBuilder;
import com.winterwell.es.fail.ESIndexAlreadyExistsException;
import com.winterwell.gson.FlexiGson;
import com.winterwell.gson.JsonArray;
import com.winterwell.gson.JsonElement;
import com.winterwell.gson.JsonObject;
import com.winterwell.gson.JsonParser;
import com.winterwell.utils.Dep;
import com.winterwell.utils.Null;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Pair2;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.Log;
import com.winterwell.utils.log.LogFile;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;
import com.winterwell.utils.time.TimeUtils;
import com.winterwell.web.FakeBrowser;
import com.winterwell.web.ajax.JSend;
import com.winterwell.web.app.AMain;
import com.winterwell.web.app.AppUtils;
import com.winterwell.web.app.KServerType;

/**
 * 
 * Should we use RollUps?? - Nov 2020, rollups were not so easy to get started with
 * 
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.9/data-rollup-transform.html
 * 
 * 
 * 
 * Use aggregations to compress the index for a month
 * @author Kai, Daniel
 *
 */
public class CompressDataLogIndexMain extends AMain<DataLogConfig> {
	
	public final static String ALIAS = "datalog.transformed.all";
	
	
	public static void main(String[] args) {
		CompressDataLogIndexMain amain = new CompressDataLogIndexMain();
		amain.doMain(args);		
	}

	@Override
	protected void init2(DataLogConfig config) {
		logFile = new LogFile(FileUtils.changeType(config.logFile, "compressor.txt"))
				// keep 1 week of log files
				.setLogRotation(TUnit.DAY.dt, 7);
		// set the config
		DataLog.init(config);
		// usual setup
		super.init2(config);
		
		init3_ES();
//		init3_gson();
//		init3_youAgain();		
	}

	
	/**
	 * Maybe get from config.namespace??
	 */
	private String dataspace = "gl";
	
	public CompressDataLogIndexMain() {
		super("CompressDataLogIndex", DataLogConfig.class);
	}
	
	@Override
	protected void doMain2() {
		// which month? (3 letter lowercase) and year (last 2 digits)		
		Time t;
		if ( ! Utils.isEmpty(configRemainderArgs)) {
			t = TimeUtils.parseExperimental(configRemainderArgs.get(0));
		} else {
			t = new Time().minus(2, TUnit.MONTH);
		}		
		String monthYear = t.format("MMMyy").toLowerCase();
		String index = "datalog."+dataspace+"_transformed_" + monthYear;
		String source = "datalog."+dataspace+"_" + monthYear;
	
		// specify some terms that we want to keep
		// See DataLogEvent#COMMON_PROPS
		// TODO increase this list as our usage changes
		List<String> terms = Arrays.asList(
				("evt domain host country pub vert vertiser campaign lineitem "
				+"cid via invalid dt amount dntn mbl browser os"
				).split(" ")
		);
		// create index and mapping
		createIndexWithPropertiesMapping(index, ALIAS, terms);
		
		ESHttpClient esc = Dep.get(ESHttpClient.class);
		// aggregate data
		String jobId = "transform_"+dataspace+"_"+monthYear;		
		TransformRequestBuilder trb = esc.prepareTransform(jobId);
			
		
		// create transform job
		// specify source and destination and time interval
		trb.setBody(source, index, terms, "24h");
		trb.setDebug(true);
		IESResponse response = trb.get().check();
		Log.d("compress", response);
		
		//after creating transform job, start it 
		TransformRequestBuilder trb2 = esc.prepareTransformStart(jobId); 
		trb2.setDebug(true);
		IESResponse response2 = trb2.get().check();
		Log.d("compress", response2);
				
		//delete the transform job
		TransformRequestBuilder trb4 = esc.prepareTransformDelete(jobId); 
		trb4.setDebug(true);
		IESResponse response4 = trb4.get();
		Log.d("compress", response4);
	}
	
	
	/**
	 * Can we share some code with 
	 * ESStorage.registerDataspace() which does much the same??
	 * (BUT that code is a bit messy)
	 * 
	 * @param idx
	 * @param alias
	 * @param terms 
	 * @return
	 */
	private void createIndexWithPropertiesMapping(String idx, String alias, List<String> terms) {
//		Dep.setIfAbsent(FlexiGson.class, new FlexiGson());
//		Dep.setIfAbsent(ESConfig.class, new ESConfig());
//		if ( ! Dep.has(ESHttpClient.class)) Dep.setSupplier(ESHttpClient.class, false, ESHttpClient::new);
		
		ESHttpClient esc = Dep.get(ESHttpClient.class);
		try {
			// make the index
			CreateIndexRequest cir = esc.admin().indices().prepareCreate(idx).setAlias(alias);
			cir.get().check();
			Utils.sleep(100);
			// set properties mapping
			PutMappingRequestBuilder pm = esc.admin().indices().preparePutMapping(idx);
			ESType mytype = new ESType();
			for(String term : terms) {
				// HACK to turn Class into ESType
				ESType est = ESType.keyword;
				Class klass = DataLogEvent.COMMON_PROPS.get(term);				
				if (klass==StringBuilder.class) {
					est = new ESType().text().norms(false);
				} else if (klass==Time.class) {
					est = new ESType().date();
				} else if (klass==Double.class) {
					est = new ESType().DOUBLE();
				} else if (klass==Integer.class) {
					est = new ESType().INTEGER();
				} else if (klass==Long.class) {
					est = new ESType().LONG();					 
				} 
				mytype.property(term, est);
			}
			// set time and count types
			mytype
				.property("time", new ESType().date())
				.property(ESStorage.count, new ESType().DOUBLE());

			pm.setMapping(mytype);
			pm.setDebug(true);
			IESResponse resp = pm.get().check();
		} catch (ESIndexAlreadyExistsException ex) {
			Log.w("compress", "Index "+idx+" already exists, proceeding...");
		}
	}

}
