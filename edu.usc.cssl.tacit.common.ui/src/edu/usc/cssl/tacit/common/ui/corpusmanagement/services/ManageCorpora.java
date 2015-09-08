package edu.usc.cssl.tacit.common.ui.corpusmanagement.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import edu.usc.cssl.tacit.common.ui.corpusmanagement.internal.ICorpus;
import edu.usc.cssl.tacit.common.ui.corpusmanagement.internal.ICorpusClass;

public class ManageCorpora {

	static String rootDir = System.getProperty("user.dir")
			+ System.getProperty("file.separator") + "tacit_corpora"
			+ System.getProperty("file.separator");

	@SuppressWarnings("unchecked")
	public static void saveCorpuses(ArrayList<Corpus> corporaList) {
		for (Corpus corpus : corporaList) {
			saveCorpus(corpus);
		}
	}

	@SuppressWarnings("unchecked")
	public static void saveCorpus(Corpus corpus) {
		String corpusID = corpus.getCorpusId();
		String corpusLocation = rootDir + corpusID;
		if (!(new File(corpusLocation).exists())) {
			// Add code for everything
			new File(corpusLocation).mkdir();
			String metaFile = corpusLocation
					+ System.getProperty("file.separator") + "meta.txt";

			File metaFp = new File(metaFile);

			JSONObject jsonObj = new JSONObject();
			jsonObj.put("corpus_name", corpus.getCorpusId());
			jsonObj.put("data_type", corpus.getDatatype().toString());
			jsonObj.put("num_classes",
					Integer.toString(corpus.getClasses().size()));

			int numClasses = corpus.getClasses().size();
			ArrayList<ICorpusClass> corporaClasses = (ArrayList<ICorpusClass>) corpus
					.getClasses();

			JSONArray classArray = new JSONArray();

			for (int i = 0; i < numClasses; i++) {
				CorpusClass currClass = (CorpusClass) corporaClasses.get(i);
				JSONObject classObj = new JSONObject();
				classObj.put("class_name", currClass.getClassName());
				classObj.put("original_loc", currClass.getClassPath());

				String[] dirParts = currClass.getClassName().split(
						System.getProperty("file.separator"));
				String dirName = dirParts[dirParts.length - 1];
				classObj.put("tacit_loc",
						corpusLocation + System.getProperty("file.separator")
								+ dirName);

				classArray.add(classObj);
			}

			jsonObj.put("class_details", classArray);
			jsonObj.put("num_analysis", "0");
			JSONArray analysisArray = new JSONArray();
			jsonObj.put("prev_analysis", analysisArray);

			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(metaFp));
				bw.write(jsonObj.toString());
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			copyCorpus(jsonObj);

		} else {
			// corpus already exits, check what has changed

			String metaFile = corpusLocation
					+ System.getProperty("file.separator") + "meta.txt";

			JSONParser parser = new JSONParser();
			JSONArray analysisArray = new JSONArray();
			int numAnalysis = 0;
			try {
				analysisArray = (JSONArray) ((JSONObject) parser
						.parse(new FileReader(metaFile))).get("prev_analysis");
				numAnalysis = (Integer) ((JSONObject) parser
						.parse(new FileReader(metaFile))).get("num_analysis");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}

			File metaFp = new File(metaFile);

			JSONObject jsonObj = new JSONObject();
			jsonObj.put("corpus_name", corpus.getCorpusId());
			jsonObj.put("data_type", corpus.getDatatype());
			jsonObj.put("num_classes",
					Integer.toString(corpus.getClasses().size()));

			int numClasses = corpus.getClasses().size();
			ArrayList<ICorpusClass> corporaClasses = (ArrayList<ICorpusClass>) corpus
					.getClasses();

			JSONArray classArray = new JSONArray();

			for (int i = 0; i < numClasses; i++) {
				CorpusClass currClass = (CorpusClass) corporaClasses.get(i);
				JSONObject classObj = new JSONObject();
				classObj.put("class_name", currClass.getClassName());
				classObj.put("original_loc", currClass.getClassPath());

				String[] dirParts = currClass.getClassName().split(
						System.getProperty("file.separator"));
				String dirName = dirParts[dirParts.length - 1];
				classObj.put("tacit_loc",
						corpusLocation + System.getProperty("file.separator")
								+ dirName);

				classArray.add(classObj);
			}

			jsonObj.put("class_details", classArray);
			jsonObj.put("num_analysis", numAnalysis);
			jsonObj.put("prev_analysis", analysisArray);

			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(metaFp));
				bw.write(jsonObj.toString());
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			copyCorpus(jsonObj);
		}

	}

	private static void copyCorpus(JSONObject jsonObj) {
		int numClasses = (Integer) jsonObj.get("num_classes");
		JSONArray classArray = (JSONArray) jsonObj.get("class_details");

		for (int i = 0; i < numClasses; i++) {
			String originalLoc = (String) ((JSONObject) classArray.get(i))
					.get("original_loc");
			String tacitLoc = (String) ((JSONObject) classArray.get(i))
					.get("tacit_loc");

			if (!(new File(tacitLoc).exists())) {
				new File(tacitLoc).mkdir();

				try {
					FileUtils.copyDirectory(new File(originalLoc), new File(
							tacitLoc));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public List<ICorpus> getAllCorpusDetails() throws IOException, ParseException {
		File[] classses = new File(rootDir).listFiles();
		System.out.println("RootDir:" + rootDir);
		List<ICorpus> corpuses = new ArrayList<ICorpus>();
		if(null == classses) return corpuses;
		
		for(File corpusClass : classses) {			
			if(corpusClass.isDirectory()) { // class
				Corpus corpora = new Corpus();
				FileReader metaDataFile = new FileReader(corpusClass.getAbsolutePath() + File.separator + "meta.txt"); // get the meta data file				
				JSONParser jsonParser = new JSONParser();
				JSONObject jsonObject = (JSONObject) jsonParser.parse(metaDataFile);
				if(null == jsonObject) continue;
				corpora.setCorpusId((String) jsonObject.get("corpus_name"));
				//corpora.setDataType((DataType) jsonObject.get("data_type"));
				
				if(Integer.parseInt((String) jsonObject.get("num_classes"))>0) 
					parseClassDetails(corpora, (JSONArray) jsonObject.get("class_details"));
				corpuses.add(corpora);
			} 
		}		
		return corpuses;
	}
 
	private void parseClassDetails(Corpus corpora, JSONArray classes) {
		if(null == corpora || null  == classes) return;
		Iterator<JSONObject> classItr = classes.iterator();
		while (classItr.hasNext()) {
			JSONObject corpusClassObj = classItr.next();
			if(null == corpusClassObj) continue;
			
			CorpusClass cc = new CorpusClass();
			cc.setClassName((String) corpusClassObj.get("class_name"));
			cc.setClassPath((String) corpusClassObj.get("original_loc"));
			cc.setTacitLocation((String) corpusClassObj.get("tacit_loc"));
			corpora.getClasses().add(cc);
				
		}		
	}	
	
	public String[] getNames() {
		List<ICorpus> readCorpusList = readCorpusList();
		List<String> names = new ArrayList<String>();
		for (ICorpus iCorpus : readCorpusList) {
			names.add(iCorpus.getCorpusId());
		}
		return (String[]) names.toArray(new String[names.size()]);
	}

	public List<ICorpus> readCorpusList() {

	
		try {
			return getAllCorpusDetails();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}

	public ICorpus readCorpusById(String id) {  // why? - to get updated corpus instead of stale data at tool

		List<ICorpus> readCorpusList = readCorpusList();
		for (ICorpus iCorpus : readCorpusList) {
			if (iCorpus.getCorpusId().equals(id)) {
				return iCorpus;
			}
		}
		return null;
	}

	
 }