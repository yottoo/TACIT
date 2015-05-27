/**
 * @author Aswin Rajkumar <aswin.rajkumar@usc.edu>
 */
package edu.uc.cssl.nlputils.wordcount.weighted.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;

import edu.uc.cssl.nlputils.wordcount.weighted.Activator;
import snowballstemmer.PorterStemmer;

public class WordCountApi {

	private StringBuilder readMe = new StringBuilder();
	private Trie categorizer = new Trie();
	private Trie phrazer = new Trie();
	private boolean phraseDetect = false;
	private HashMap<String, HashMap<String, Double>> weightMap = new HashMap<String, HashMap<String, Double>>();
	private HashMap<String, List<Integer>> phraseLookup = new HashMap<String, List<Integer>>();
	private HashMap<String, List<Integer>> conditionalCategory = new HashMap<String, List<Integer>>();
	private TreeMap<Integer, String> categories = new TreeMap<Integer, String>();
	private String delimiters;
	private boolean doLower;
	private boolean doStopWords;
	private boolean noDictionary = false;
	private HashSet<String> stopWordSet = new HashSet<String>();
	private boolean doLiwcStemming = true;
	private boolean doSpss = true;
	private boolean doWordDistribution = true;
	private boolean doSnowballStemming = true;
	private boolean stemDictionary = false;
	PorterStemmer stemmer = new PorterStemmer();
	private int weirdDashCount = 0;
	private String punctuations = " .,;\"!-()[]{}:?'/\\`~$%#@&*_=+<>";
	// counting numbers - // 5.7, .7 , 5., 567, -25.9, +45
	Pattern pattern = Pattern
			.compile("^[+-]{0,1}[\\d]*[.]{0,1}[\\d]+[.]{0,1}$");
	// Pattern pattern =
	// Pattern.compile("\\s+[+-]{0,1}[\\d]*[.]{0,1}[\\d]+[.,\\s]+");

	// end of line detection
	Pattern eol = Pattern.compile("\\w+\\s*[.?!]+\\B");

	// compound word detection
	Pattern compoundPattern = Pattern.compile("[\\w\\d]+[-]{1}[\\w\\d]+");

	Pattern doubleHyphenPattern = Pattern.compile("[\\w\\d]+[-]{2}[\\w\\d]+");

	// Regular word
	Pattern regularPattern = Pattern.compile("[\\w\\d]+");

	// for rounding off the decimals
	DecimalFormat df = new DecimalFormat("#.##");

	// for calculating punctuation ratios
	int period, comma, colon, semiC, qMark, exclam, dash, quote, apostro,
			parenth, otherP, allPct;
	private boolean weighted;

	private static Logger logger = Logger.getLogger(WordCountApi.class
			.getName());

	public WordCountApi(boolean weighted) {
		this.weighted = weighted;
	}

	// Updated function that can handle multiple input files
	public void wordCount(SubProgressMonitor subProgressMonitor,
			List<String> inputFiles, List<String> dictionaryFile,
			String stopWordsFile, String outputFile, String delimiters,
			boolean doLower, boolean doLiwcStemming,
			boolean doSnowBallStemming, boolean doSpss,
			boolean doWordDistribution, boolean stemDictionary, File oFile,
			File sFile) throws IOException {
		if (delimiters == null || delimiters.equals(""))
			this.delimiters = " ";
		else
			this.delimiters = delimiters;
		this.doLower = doLower;
		this.doLiwcStemming = doLiwcStemming;
		this.doSpss = doSpss;
		this.doWordDistribution = doWordDistribution;
		this.doSnowballStemming = doSnowBallStemming;
		this.stemDictionary = stemDictionary;

		appendLog("Processing...");

		if (stopWordsFile.equals(null) || stopWordsFile.equals(""))
			this.doStopWords = false;
		else
			this.doStopWords = true;
		// An error flag to check the error conditions

		// No errors with the output, dictionary and stop-words paths. Start
		// processing.
		long startTime = System.currentTimeMillis();
		subProgressMonitor.beginTask("Counting Words", 99);
		if (subProgressMonitor.isCanceled()) {
			throw new OperationCanceledException();

		}
		buildCategorizer(dictionaryFile);
		logger.info("Finished building the dictionary trie in "
				+ (System.currentTimeMillis() - startTime) + " milliseconds.");
		appendLog("Finished building the dictionary trie in "
				+ (System.currentTimeMillis() - startTime) + " milliseconds.");

		// Create Stop Words Set if doStopWords is true
		if (doStopWords) {
			startTime = System.currentTimeMillis();
			stopWordSetBuild(new File(stopWordsFile));
			logger.info("Finished building the Stop Words Set in "
					+ (System.currentTimeMillis() - startTime)
					+ " milliseconds.");
			appendLog("Finished building the Stop Words Set in "
					+ (System.currentTimeMillis() - startTime)
					+ " milliseconds.");
		}

		// Write the titles in the output file.
		buildOutputFile(oFile);

		// Write the SPSS file
		if (doSpss)
			buildSpssFile(sFile);

		// categorizer.printTrie();
		// System.out.println(categories);
		// System.out.println(stopWordSet);

		// for each inputFile,
		for (String inputFile : inputFiles) {

			// Mac cache file filtering
			if (inputFile.contains("DS_Store"))
				continue;

			countWords(inputFile, oFile, sFile);
		}

		if (doSpss)
			finalizeSpssFile(sFile);
		// No errors
		writeReadMe(outputFile.substring(0,
				outputFile.lastIndexOf(File.separator)));
	}

	public void countWords(String inputFile, File oFile, File spssFile)
			throws IOException {
		File iFile = new File(inputFile);
		if (iFile.isDirectory()) {
			return;
		}
		logger.info("Current input file - " + inputFile);
		appendLog("Current input file - " + inputFile);
		// For calculating Category wise distribution of each word.
		HashMap<String, HashSet<String>> wordCategories = new HashMap<String, HashSet<String>>();

		// Build a hashmap of the words in the input file
		long startTime = System.currentTimeMillis();
		BufferedReader br = new BufferedReader(new FileReader(iFile));
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		String currentLine;
		int totalWords = 0;
		int sixltr = 0;
		int noOfLines = 0;
		int numerals = 0;
		weirdDashCount = 0;
		period = comma = colon = semiC = qMark = exclam = dash = quote = apostro = parenth = otherP = allPct = 0;
		while ((currentLine = br.readLine()) != null) {

			Matcher eolMatcher = eol.matcher(currentLine);
			while (eolMatcher.find())
				noOfLines++;

			period = period + StringUtils.countMatches(currentLine, ".");
			comma = comma + StringUtils.countMatches(currentLine, ",");
			colon = colon + StringUtils.countMatches(currentLine, ":");
			semiC = semiC + StringUtils.countMatches(currentLine, ";");
			qMark = qMark + StringUtils.countMatches(currentLine, "?");
			exclam = exclam + StringUtils.countMatches(currentLine, "!");
			dash = dash + StringUtils.countMatches(currentLine, "-");
			quote = quote + StringUtils.countMatches(currentLine, "\"");
			quote = quote + StringUtils.countMatches(currentLine, "�");
			quote = quote + StringUtils.countMatches(currentLine, "�");
			apostro = apostro + StringUtils.countMatches(currentLine, "'");
			parenth = parenth + StringUtils.countMatches(currentLine, "(");
			parenth = parenth + StringUtils.countMatches(currentLine, ")");

			for (char c : "#$%&*+=/\\<>@_^`~|{}[]".toCharArray()) {
				otherP = otherP
						+ StringUtils.countMatches(currentLine,
								String.valueOf(c));
			}

			int[] i = process(currentLine, map);
			totalWords = totalWords + i[0];
			sixltr = sixltr + i[1];
			numerals = numerals + i[2];
		}
		allPct = allPct + period + comma + colon + semiC + qMark + exclam
				+ dash + quote + apostro + parenth + otherP;

		br.close();
		logger.info("Total number of words - " + totalWords);
		logger.info("Finished building hashmap in "
				+ (System.currentTimeMillis() - startTime) + " milliseconds.");
		appendLog("Total number of words - " + totalWords);
		appendLog("Finished building hashmap in "
				+ (System.currentTimeMillis() - startTime) + " milliseconds.");

		// Calculate Category-wise count
		HashMap<String, Integer> catCount = new HashMap<String, Integer>();
		List<Integer> currCategories;
		int dicCount = 0;
		String currCategoryName = "";
		// Search each input word in the trie prefix tree categorizer
		// (dictionary).
		for (String currWord : map.keySet()) {

			if (currWord == null || currWord.equals(""))
				continue;

			currCategories = categorizer.query(currWord.toLowerCase());

			// If the word is in the trie, update the dictionary words count and
			// the per-category count
			if (currCategories != null) {
				// dicCount = dicCount+1;
				dicCount = dicCount + map.get(currWord); // add the count of the
															// current word. we
															// are not counting
															// unique words
															// here.
				for (int i : currCategories) {
					currCategoryName = categories.get(i);
					// System.out.println(currCategoryName+"->"+currWord);
					if (catCount.get(currCategoryName) != null) {
						// catCount.put(currCategoryName,
						// catCount.get(currCategoryName)+1);
						// Add 1 to count the unique words in the category.
						// Add map.get(currWord), i.e, the num of each word to
						// count total number of words in the category
						catCount.put(
								currCategoryName,
								catCount.get(currCategoryName)
										+ map.get(currWord));
					} else {
						catCount.put(currCategoryName, map.get(currWord));
					}

					// Populate the Category Set for each Word
					HashSet<String> currWordCategories = wordCategories
							.get(currWord);
					if (currWordCategories != null) {
						wordCategories.get(currWord).add(currCategoryName);
					} else {
						currWordCategories = new HashSet<String>();
						currWordCategories.add(currCategoryName);
						wordCategories.put(currWord, currWordCategories);
					}

				}
			} else {
				// System.out.println("No category -> "+currWord);
			}
		}
		// If Word Distribution output is enabled, calculate the values
		if (doWordDistribution)
			calculateWordDistribution(map, catCount, wordCategories, inputFile,
					oFile);

		// If there are no punctuation marks, minimum number of lines = 1
		if (noOfLines == 0)
			noOfLines = 1;

		writeToFile(oFile, iFile.getName(), totalWords, totalWords
				/ (float) noOfLines, (sixltr * 100) / (float) totalWords,
				(dicCount * 100) / (float) totalWords, (numerals * 100)
						/ (float) totalWords, catCount);
		if (doSpss)
			writeToSpss(spssFile, iFile.getName(), totalWords, totalWords
					/ (float) noOfLines, (sixltr * 100) / (float) totalWords,
					(dicCount * 100) / (float) totalWords, catCount);
	}

	public void calculateWordDistribution(HashMap<String, Integer> map,
			HashMap<String, Integer> catCount,
			HashMap<String, HashSet<String>> wordCategories, String inputFile,
			File oFile) throws IOException {
		File outputDir = oFile.getParentFile();
		String iFilename = inputFile.substring(inputFile.lastIndexOf(System
				.getProperty("file.separator")));
		File wdFile = new File(outputDir.getAbsolutePath()
				+ System.getProperty("file.separator") + iFilename
				+ "_wordDistribution.csv");
		BufferedWriter bw = new BufferedWriter(new FileWriter(wdFile));
		bw.write("Word,Count,");
		StringBuilder toWrite = new StringBuilder();

		for (String currCat : catCount.keySet()) {
			toWrite.append(currCat + ",");
		}
		bw.write(toWrite.toString());
		bw.newLine();

		// check for words in wordCategories instead of map because
		// wordCategories has the words that are present in the dictionary
		for (String currWord : wordCategories.keySet()) {
			StringBuilder row = new StringBuilder();
			int currWC = map.get(currWord);
			row.append(currWord + "," + currWC + ",");

			for (String currCat : catCount.keySet()) {
				// multiplier is 0 if the current word does not belong to the
				// current category
				int multiplier = 0;
				if (wordCategories.get(currWord).contains(currCat))
					multiplier = 100; // 100 instead of 1 because the output
										// should be of the form 25%, not 0.25
				if (multiplier == 0)
					row.append("0,");
				else {
					// Find the root word as that's what's stored in the
					// dictionary and weight map
					String rootWord = categorizer.root(currWord);
					if (this.weighted) {
						row.append((multiplier * map.get(currWord) * weightMap
								.get(rootWord).get(currCat))
								/ (float) catCount.get(currCat) + ",");
					} else {
						row.append(multiplier * map.get(currWord)
								/ (float) catCount.get(currCat) + ",");
					}
				}
			}
			bw.write(row.toString());
			bw.newLine();
		}

		bw.close();
	}

	// Builds the Stop Word Set
	public void stopWordSetBuild(File sFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(sFile));
		String currentLine = null;
		while ((currentLine = br.readLine()) != null) {
			stopWordSet.add(currentLine.trim().toLowerCase());
		}
		br.close();
	}

	public void buildOutputFile(File oFile) throws IOException {
		StringBuilder titles = new StringBuilder();
		titles.append("Filename,Seg,WC,WPS,Sixltr,Dic,Numerals,");
		for (String title : categories.values()) {
			titles.append(title + ",");
		}
		titles.append("Period, Comma, Colon, SemiC, QMark, Exclam, Dash, Quote, Apostro, Parenth, OtherP, AllPct");
		FileWriter fw = new FileWriter(oFile);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(titles.toString());
		bw.newLine();
		bw.close();
		logger.info("Building the output File.");
		appendLog("Building the output File.");
	}

	public void buildSpssFile(File spssFile) throws IOException {
		StringBuilder titles = new StringBuilder();
		titles.append("DATA LIST LIST\n/ Filename A(40) WC WPS Sixltr Dic ");
		for (String title : categories.values()) {
			titles.append(title + " ");
		}
		titles.append(".\nBEGIN DATA.");
		FileWriter fw = new FileWriter(spssFile);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(titles.toString());
		bw.newLine();
		bw.close();
		// logger.info("Created the SPSS output File.");
	}

	public void finalizeSpssFile(File spssFile) throws IOException {
		String end = "END DATA.\n\nLIST.";
		FileWriter fw = new FileWriter(spssFile, true);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(end);
		bw.newLine();
		bw.close();
		logger.info("Created the SPSS output File.");
		appendLog("Created the SPSS output File.");
	}

	public void writeToSpss(File spssFile, String docName, int totalCount,
			float wps, float sixltr, float dic,
			HashMap<String, Integer> catCount) throws IOException {
		StringBuilder row = new StringBuilder();
		row.append("\"" + docName + "\"" + " " + totalCount + " " + wps + " "
				+ sixltr + " " + dic + " ");
		int currCatCount = 0;
		// Get the category-wise word count and create the comma-separated row
		// string
		for (String title : categories.values()) {
			if (catCount.get(title) == null)
				currCatCount = 0;
			else
				currCatCount = catCount.get(title);
			row.append(((currCatCount * 100) / (float) totalCount) + " ");
		}
		// Append mode because the titles are already written. Append a row
		// corresponding to each input file
		FileWriter fw = new FileWriter(spssFile, true);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(row.toString());
		bw.newLine();
		bw.close();
		logger.info("SPSS File Updated Successfully");
	}

	public void writeToFile(File oFile, String docName, int totalCount,
			float wps, float sixltr, float dic, float numerals,
			HashMap<String, Integer> catCount) throws IOException {
		StringBuilder row = new StringBuilder();
		row.append(docName + ",1," + totalCount + "," + df.format(wps) + ","
				+ df.format(sixltr) + "," + df.format(dic) + ","
				+ df.format(numerals) + ",");

		int currCatCount = 0;
		// Get the category-wise word count and create the comma-separated row
		// string
		for (String title : categories.values()) {
			if (catCount.get(title) == null)
				currCatCount = 0;
			else
				currCatCount = catCount.get(title);
			row.append(df.format(((currCatCount * 100) / (float) totalCount))
					+ ",");
		}

		// Period, Comma, Colon, SemiC, QMark, Exclam, Dash, Quote, Apostro,
		// Parenth, OtherP, AllPct
		row.append(df.format(((period * 100) / (float) totalCount)) + ",");
		row.append(df.format(((comma * 100) / (float) totalCount)) + ",");
		row.append(df.format(((colon * 100) / (float) totalCount)) + ",");
		row.append(df.format(((semiC * 100) / (float) totalCount)) + ",");
		row.append(df.format(((qMark * 100) / (float) totalCount)) + ",");
		row.append(df.format(((exclam * 100) / (float) totalCount)) + ",");
		// row.append(df.format(((dash*100)/(float)totalCount))+","); correct
		// way
		dash = (dash * 2) - weirdDashCount;
		row.append(df.format(((dash * 100) / (float) totalCount)) + ",");
		row.append(df.format(((quote * 100) / (float) totalCount)) + ",");
		row.append(df.format(((apostro * 100) / (float) totalCount)) + ",");
		row.append(df.format(((parenth * 50) / (float) totalCount)) + ","); // multiply
																			// by
																			// 50
																			// =
																			// dividing
																			// by
																			// two.
																			// parantheses
																			// are
																			// counted
																			// as
																			// pairs
		row.append(df.format(((otherP * 100) / (float) totalCount)) + ",");
		row.append(df.format(((allPct * 100) / (float) totalCount)) + ",");

		// Append mode because the titles are already written. Append a row
		// corresponding to each input file
		FileWriter fw = new FileWriter(oFile, true);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(row.toString());
		bw.newLine();
		bw.close();
		logger.info("CSV File Updated Successfully");
		appendLog("CSV File Updated Successfully");
	}

	public void buildCategorizer(List<String> dictFiles) throws IOException {

		for (String dFile : dictFiles) {
			BufferedReader br = new BufferedReader(new FileReader(new File(
					dFile)));
			String currentLine = br.readLine().trim();
			if (currentLine == null) {
				logger.warning("The dictionary file " + dFile + " is empty");
				appendLog("The dictionary file " + dFile + " is empty");
			}
			 
			if (currentLine.equals("%"))
				while ((currentLine = br.readLine().trim().toLowerCase()) != null
						&& !currentLine.equals("%"))
					categories.put(
							Integer.parseInt(currentLine.split("\\s+")[0].trim()),
							currentLine.split("\\s+")[1].trim());

			if (currentLine == null) {
				logger.warning("The dictionary file " + dFile
						+ " does not have categorized words");
				appendLog("The dictionary file " + dFile
						+ " does not have categorized words");
			} else {
				while ((currentLine = br.readLine()) != null) {
					ArrayList<Integer> categories = new ArrayList<Integer>();
					ArrayList<Integer> condCategories = new ArrayList<Integer>();
					HashMap<String, Double> weights = new HashMap<String, Double>();
					currentLine = currentLine.trim().toLowerCase(); // Dictionary
																	// is stored
																	// in
																	// lowercase
																	// in LIWC

					if (currentLine.equals(""))
						continue;
					boolean conditional = false;
					String[] words = currentLine.split("\\s+");
					String currPhrase = words[0];
					String condPhrase = words[0];
					for (int i = initialize(); i < words.length; i = increment(i)) {
						if (!words[i].matches("\\d+")) {
							if (words[i].contains("/")) {
								String[] splits = words[i].split("/");
								categories.add(Integer.parseInt(splits[1]));
								if (splits[0].contains(">")) {
									conditional = true;
									condCategories.add(Integer
											.parseInt(splits[0].split(">")[1]));
									condPhrase = words[0]
											+ " "
											+ splits[0].split(">")[0].replace(
													"<", "");
								}
							} else if (words[i].contains(")")
									|| words[i].contains("("))
								continue;
							else {
								currPhrase = currPhrase + " " + words[i];
								phraseDetect = true;
							}
							continue;
						}
						if (this.weighted) {
							weights.put(this.categories.get(Integer
									.parseInt(words[i])), Double
									.parseDouble(words[i - 1]));
						}
						categories.add(Integer.parseInt(words[i]));
					}

					String currentWord = words[0];
					if (phraseDetect)
						currentWord = currPhrase;

					if (stemDictionary) {
						currentWord = currentWord.replace("*", "");
						stemmer.setCurrent(currentWord);
						String stemmedWord = "";
						if (stemmer.stem())
							stemmedWord = stemmer.getCurrent();
						if (!stemmedWord.equals(""))
							currentWord = stemmedWord;
					}

					// System.out.println(currentWord);
					// System.out.println(words[0]+" "+categories);

					// do Stemming or not. if Stemming is disabled, remove *
					// from the dictionary words
					if (!doLiwcStemming)
						currentWord = currentWord.replace("*", "");
					categorizer.insert(currentWord, categories);

					if (phraseDetect)
						phraseLookup.put(currentWord, categories);

					if (conditional)
						conditionalCategory.put(condPhrase, condCategories);
					// categorizer.printTrie();

					if (this.weighted)
						weightMap.put(currentWord, weights);
				}
			}
			br.close();
		}
	}

	private int initialize() {

		if (this.weighted)
			return 2;
		else
			return 1;
	}

	private int increment(int val) {
		if (this.weighted) {
			return val + 2;
		} else
			return val + 1;
	}

	// Adds words and their corresponding count to the hashmap. Returns total
	// number of words.
	public int[] process(String line, HashMap<String, Integer> map) {
		int ret[] = new int[3];
		int numWords = 0;
		int sixltr = 0;
		int numerals = 0;
		Matcher matcher = pattern.matcher(line);

		/*
		 * //LIWC checks the numerals before stripping off the hyphens
		 * StringTokenizer tokens = new StringTokenizer(line," ");
		 * while(tokens.hasMoreTokens()){ String currentWord =
		 * tokens.nextToken(); matcher = pattern.matcher(currentWord); while
		 * (matcher.find()){ numerals++; } }
		 */

		// preprocess
		if (doLower)
			line = line.toLowerCase();

		// phrase check
		// increment total words and sixltr words. put the word count in the
		// map.
		// add phrase to lookup and its categories to categorizer
		// phrases ending with *
		// phrase only. single pattern for space or beginning of file?
		// phrases with numerals?
		// use string builder?
		// right now, putting key in the map rather than the match
		if (phraseDetect) {
			for (String key : phraseLookup.keySet()) {
				Pattern p, justp;
				if (key.endsWith("*")) {
					p = Pattern.compile("[\\s\"\\.,()]{1}" + key + "[\\w\\d]*"); // if
																					// key
																					// is
																					// 'string*'
					justp = Pattern.compile(key + "[\\w\\d]*");
					key = key.substring(0, key.length() - 1);
				} else {
					p = Pattern.compile("[\\s\"\\.,()]{1}" + key + "\\b");
					justp = Pattern.compile(key + "\\b");
				}

				int wordsInPhrase = key.split("\\s+").length;

				Matcher m = p.matcher(line);
				ArrayList<Integer> indexes = new ArrayList<Integer>();
				while (m.find()) {
					// System.out.println(m.group());
					// System.out.println(m.start()+1);
					// System.out.println(m.end());
					String match = m.group();
					indexes.add(m.start() + 1);
					indexes.add(m.end());

					numWords = numWords + wordsInPhrase;
					sixltr = sixltr + bigWords(match);

					Object value = map.get(key);
					if (value != null) {
						int i = ((Integer) value).intValue();
						map.put(key, i + wordsInPhrase);
					} else {
						map.put(key, wordsInPhrase);
					}
				}

				for (int i = 0; i < indexes.size(); i = i + 2) {
					// phrases and replacements would be rare. No need for a
					// StringBuilder
					line = line.substring(0, indexes.get(i))
							+ line.substring(indexes.get(i + 1));
					int diff = indexes.get(i + 1) - indexes.get(i);
					for (int j = 0; j < indexes.size(); j++)
						indexes.set(j, indexes.get(j) - diff);
					// System.out.println(line);
				}
				indexes.clear();

				// in case the file just has that one phrase or begins with it
				Matcher m2 = justp.matcher(line);
				while (m2.find()) {
					String match = m2.group();
					indexes.add(m2.start());
					indexes.add(m2.end());

					numWords = numWords + wordsInPhrase;
					sixltr = sixltr + bigWords(match);

					Object value = map.get(key);
					if (value != null) {
						int i = ((Integer) value).intValue();
						map.put(key, i + wordsInPhrase);
					} else {
						map.put(key, wordsInPhrase);
					}
				}

				for (int i = 0; i < indexes.size(); i = i + 2) {
					// phrases and replacements would be rare. No need for a
					// StringBuilder
					line = line.substring(0, indexes.get(i))
							+ line.substring(indexes.get(i + 1));
					int diff = indexes.get(i + 1) - indexes.get(i);
					for (int j = 0; j < indexes.size(); j++)
						indexes.set(j, indexes.get(j) - diff);
					// System.out.println(line);
				}
				// System.out.println("Phrase removed - "+line);

			}
		}

		StringTokenizer st = new StringTokenizer(line, delimiters);
		String currentWord = null;
		if (st.hasMoreTokens())
			currentWord = trimChars(st.nextToken(), punctuations);
		do {
			String nextWord = null;
			// take the next word too
			if (st.hasMoreTokens())
				nextWord = trimChars(st.nextToken(), punctuations);
			// String currentWord = st.nextToken();

			if (currentWord == null || currentWord.equals(""))
				continue;

			// Checking numerals
			matcher = pattern.matcher(currentWord);
			while (matcher.find()) {
				numerals++;
			}

			// Do Porter2/Snowball Stemming if enabled
			if (doSnowballStemming) {
				stemmer.setCurrent(currentWord);
				String stemmedWord = "";
				if (stemmer.stem())
					stemmedWord = stemmer.getCurrent();
				if (!stemmedWord.equals(""))
					currentWord = stemmedWord;
			}

			// If stop word, ignore
			if (doStopWords)
				if (stopWordSet.contains(currentWord))
					continue;

			Matcher word = regularPattern.matcher(currentWord);
			if (word.find()) {
				numWords = numWords + 1;
				if (currentWord.length() > 6) {
					sixltr = sixltr + 1;
					// System.out.println(currentWord+" "+sixltr);
				}
			}

			/*
			 * numWords = numWords + 1; if (currentWord.length()>6){ sixltr =
			 * sixltr + 1; //System.out.println(currentWord+" "+sixltr); }
			 */

			boolean treatAsOne = true;

			Matcher dh = doubleHyphenPattern.matcher(currentWord);
			// if double quotes, convert to single quotes and treat as a single
			// word in the lookup
			if (dh.find()) {
				currentWord = currentWord.replace("--", "-").toLowerCase();
				if (categorizer.query(currentWord) != null
						&& !categorizer.checkHyphen(currentWord)) {
					// treat as one word.
					// numWords = numWords; already 1 added above
					// System.out.println("Treating as one - "+currentWord);
					Object value = map.get(currentWord);
					if (value != null) {
						int i = ((Integer) value).intValue();
						map.put(currentWord, i + 1);
					} else {
						map.put(currentWord, 1);
					}
					String[] words = currentWord.split("-");
					int hyphened = 0;
					// boolean allFound = true;
					for (String s : words) {
						// if (s==null || s.equals(""))
						// continue;
						// if (categorizer.query(s) == null){
						// allFound = false;
						// }
						hyphened++;
					}

					weirdDashCount = weirdDashCount + hyphened; // twice if two
																// dashes
				} else {
					String[] words = currentWord.split("-");
					int hyphened = -1;
					// boolean allFound = true;
					for (String s : words) {
						// if (s==null || s.equals(""))
						// continue;
						// if (categorizer.query(s) == null){
						// allFound = false;
						// }
						hyphened++;
					}

					if (categorizer.query(currentWord) != null) {
						Object value = map.get(currentWord);
						if (value != null) {
							int i = ((Integer) value).intValue();
							map.put(words[0], i + 1);
						} else {
							map.put(words[0], 1);
						}
						numWords = numWords + hyphened;
						treatAsOne = true;
					} else {
						numWords = numWords + hyphened;
						if (categorizer.query(words[0]) != null) {
							Object value = map.get(words[0]);
							if (value != null) {
								int i = ((Integer) value).intValue();
								map.put(words[0], i + 1);
							} else {
								map.put(words[0], 1);
							}
						}
						treatAsOne = false;
					}
					if (treatAsOne)
						weirdDashCount++;
					else
						weirdDashCount = weirdDashCount + hyphened; // twice if
																	// two
																	// dashes
				}
			} else {
				Matcher cm = compoundPattern.matcher(currentWord);
				if (cm.find()) {
					String[] words = currentWord.split("-");
					int hyphened = -1;
					for (String s : words) {
						if (s == null || s.equals(""))
							continue;
						hyphened++;
					}
					// int hyphens = StringUtils.countMatches(currentWord, "-");
					// -- breaks on double hyphens

					// If the word is not in the dictionary, consider as
					// separate words.
					if (categorizer.query(currentWord.toLowerCase()) == null) {
						numWords = numWords + hyphened; // no need to add +1 as
														// the count was
														// increased by 1 above.
						treatAsOne = false;
					} else
						// Add hyphencount to the weird dash count to subtract
						// from the final value.
						weirdDashCount = weirdDashCount + 1;
				}

				if (treatAsOne) {
					// can use map.containsKey function. But avoiding two calls
					// with the one below.
					Object value = map.get(currentWord);
					if (value != null) {
						int i = ((Integer) value).intValue();
						map.put(currentWord, i + 1);
					} else {
						map.put(currentWord, 1);
					}
				} else {
					// if the compound word doesnt exist in the dictionary,
					// treat as separate words.
					String[] parts = currentWord.split("-");
					for (String part : parts) {
						if (part == null || part.equals(""))
							continue;
						Object value = map.get(part);
						if (value != null) {
							int i = ((Integer) value).intValue();
							map.put(part, i + 1);
						} else {
							map.put(part, 1);
						}
					}
				}
			}
			currentWord = nextWord;
		} while (st.hasMoreTokens());

		ret[0] = numWords;
		ret[1] = sixltr;
		ret[2] = numerals;
		return ret;
	}

	private int bigWords(String group) {
		int bigs = 0;
		for (String word : group.split("\\s+"))
			if (word.length() > 6)
				bigs = bigs + 1;
		return bigs;
	}

	private void appendLog(String message) {
		System.out.println(message);
	}

	public static String trimChars(String source, String trimChars) {
		char[] chars = source.toCharArray();
		int length = chars.length;
		int start = 0;

		while (start < length && trimChars.indexOf(chars[start]) > -1) {
			start++;
		}

		while (start < length && trimChars.indexOf(chars[length - 1]) > -1) {
			length--;
		}

		if (start > 0 || length < chars.length) {
			return source.substring(start, length);
		} else {
			return source;
		}
	}

	public void writeReadMe(String location) {
		File readme = new File(location + "/README.txt");
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(readme));
			String appV = Platform
					.getBundle("edu.usc.cssl.nlputils.repository").getHeaders()
					.get("Bundle-Version");
			Date date = new Date();
			bw.write("Weighted Word Count Output\n--------------------------\n\nApplication Version: "
					+ appV + "\nDate: " + date.toString() + "\n\n");
			bw.write(readMe.toString());
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
