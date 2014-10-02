package net.massbank.validator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.Map;
import java.util.TreeMap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import massbank.MassBankEnv;
import massbank.admin.DatabaseAccess;
import massbank.admin.FileUtil;
import massbank.FileUpload;
import massbank.GetConfig;

import org.apache.commons.cli.*;

// <%@ include file="../jsp/Common.jsp"%>

public class RecordValidator {

	/** 作業ディレクトリ用日時フォーマット */
	private final static SimpleDateFormat sdf = new SimpleDateFormat(
			"yyMMdd_HHmmss_SSS");

	/** 改行文字列 */
	private final static String NEW_LINE = System.getProperty("line.separator");

	/** アップロードレコードファイル名（ZIP） */
	private final static String UPLOAD_RECDATA_ZIP = "recdata.zip";

	/** zipファイル拡張子 */
	private final static String ZIP_EXTENSION = ".zip";

	/** アップロードレコードファイル名（MSBK） */
	private final static String UPLOAD_RECDATA_MSBK = "*.msbk";

	/** msbkファイル拡張子 */
	private final static String MSBK_EXTENSION = ".msbk";

	/** レコードデータディレクトリ名 */
	private final static String RECDATA_DIR_NAME = "recdata";

	/** レコード拡張子 */
	private final static String REC_EXTENSION = ".txt";

	/** レコード値デフォルト */
	private final static String DEFAULT_VALUE = "N/A";

	/** ステータス（OK） */
	private final static String STATUS_OK = "<span class=\"msgFont\">ok</span>";

	/** ステータス（警告） */
	private final static String STATUS_WARN = "<span class=\"warnFont\">warn</span>";

	/** ステータス（エラー） */
	private final static String STATUS_ERR = "<span class=\"errFont\">error</span>";

	/**
	 * HTML表示用メッセージテンプレート（情報）
	 * 
	 * @param msg
	 *            メッセージ（情報）
	 * @return 表示用メッセージ（情報）
	 */
	private static String msgInfo(String msg) {
		StringBuilder sb = new StringBuilder(
				"<i>info</i> : <span class=\"msgFont\">");
		sb.append(msg);
		sb.append("</span><br>");
		return sb.toString();
	}

	/**
	 * HTML表示用メッセージテンプレート（警告）
	 * 
	 * @param msg
	 *            メッセージ（警告）
	 * @return 表示用メッセージ（警告）
	 */
	private static String msgWarn(String msg) {
		StringBuilder sb = new StringBuilder(
				"<i>warn</i> : <span class=\"warnFont\">");
		sb.append(msg);
		sb.append("</span><br>");
		return sb.toString();
	}

	/**
	 * HTML表示用メッセージテンプレート（エラー）
	 * 
	 * @param msg
	 *            メッセージ（エラー）
	 * @return 表示用メッセージ（エラー）
	 */
	private static String msgErr(String msg) {
		StringBuilder sb = new StringBuilder(
				"<i>error</i> : <span class=\"errFont\">");
		sb.append(msg);
		sb.append("</span><br>");
		return sb.toString();
	}

	/**
	 * チェック処理
	 * 
	 * @param db
	 *            DBアクセスオブジェクト
	 * @param op
	 *            PrintWriter出力バッファ
	 * @param dataPath
	 *            チェック対象レコードパス
	 * @param registPath
	 *            登録先予定パス
	 * @param ver
	 *            レコードフォーマットバージョン
	 * @return チェック結果Map<ファイル名, 画面表示用タブ区切り文字列>
	 * @throws IOException
	 *             入出力例外
	 */
	private static TreeMap<String, String> validationRecord(DatabaseAccess dbx,
			PrintStream op, String dataPath, String registPath, int ver)
			throws IOException {

		if (ver == 1) {
			op.println(msgInfo("check record format version is [version 1]."));
		}

		final String[] dataList = (new File(dataPath)).list();
		TreeMap<String, String> validationMap = new TreeMap<String, String>();

		if (dataList.length == 0) {
			op.println(msgWarn("no file for validation."));
			return validationMap;
		}

		// ----------------------------------------------------
		// レコードファイル必須項目、必須項目値チェック処理
		// ----------------------------------------------------
		String[] requiredList = new String[] { // Ver.2
		"ACCESSION: ", "RECORD_TITLE: ", "DATE: ", "AUTHORS: ", "LICENSE: ",
				"CH$NAME: ", "CH$COMPOUND_CLASS: ", "CH$FORMULA: ",
				"CH$EXACT_MASS: ", "CH$SMILES: ", "CH$IUPAC: ",
				"AC$INSTRUMENT: ", "AC$INSTRUMENT_TYPE: ",
				"AC$MASS_SPECTROMETRY: MS_TYPE ",
				"AC$MASS_SPECTROMETRY: ION_MODE ", "PK$NUM_PEAK: ", "PK$PEAK: " };
		if (ver == 1) { // Ver.1
			requiredList = new String[] { "ACCESSION: ", "RECORD_TITLE: ",
					"DATE: ", "AUTHORS: ", "COPYRIGHT: ", "CH$NAME: ",
					"CH$COMPOUND_CLASS: ", "CH$FORMULA: ", "CH$EXACT_MASS: ",
					"CH$SMILES: ", "CH$IUPAC: ", "AC$INSTRUMENT: ",
					"AC$INSTRUMENT_TYPE: ", "AC$ANALYTICAL_CONDITION: MODE ",
					"PK$NUM_PEAK: ", "PK$PEAK: " };
		}
		for (int i = 0; i < dataList.length; i++) {
			String name = dataList[i];
			String status = "";
			StringBuilder detailsErr = new StringBuilder();
			StringBuilder detailsWarn = new StringBuilder();

			// 読み込み対象チェック処理
			File file = new File(dataPath + name);
			if (file.isDirectory()) {
				// ディレクトリの場合
				status = STATUS_ERR;
				detailsErr.append("<span class=\"errFont\">[" + name
						+ "]&nbsp;&nbsp;is directory.</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			} else if (file.isHidden()) {
				// 隠しファイルの場合
				status = STATUS_ERR;
				detailsErr.append("<span class=\"errFont\">[" + name
						+ "]&nbsp;&nbsp;is hidden.</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			} else if (name.lastIndexOf(REC_EXTENSION) == -1) {
				// ファイル拡張子不正の場合
				status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">file extension of&nbsp;&nbsp;["
								+ name
								+ "]&nbsp;&nbsp;is not&nbsp;&nbsp;["
								+ REC_EXTENSION + "].</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			}

			// 読み込み
			boolean isEndTagRead = false;
			boolean isInvalidInfo = false;
			boolean isDoubleByte = false;
			ArrayList<String> fileContents = new ArrayList<String>();
			boolean existLicense = false; // LICENSEタグ存在チェック用（Ver.1）
			ArrayList<String> workChName = new ArrayList<String>(); // RECORD_TITLEチェック用にCH$NAMEの値を退避（Ver.1以降）
			String workAcInstrumentType = ""; // RECORD_TITLEチェック用にAC$INSTRUMENT_TYPEの値を退避（Ver.1以降）
			String workAcMsType = ""; // RECORD_TITLEチェック用にAC$MASS_SPECTROMETRY:
										// MS_TYPEの値を退避（Ver.2）
			String line = "";
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(file));
				while ((line = br.readLine()) != null) {
					if (isEndTagRead) {
						if (!line.equals("")) {
							isInvalidInfo = true;
						}
					}

					// 終了タグ検出時フラグセット
					if (line.startsWith("//")) {
						isEndTagRead = true;
					}
					fileContents.add(line);

					// LICENSE退避（Ver.1）
					if (line.startsWith("LICENSE: ")) {
						existLicense = true;
					}
					// CH$NAME退避（Ver.1以降）
					else if (line.startsWith("CH$NAME: ")) {
						workChName.add(line.trim()
								.replaceAll("CH\\$NAME: ", ""));
					}
					// AC$INSTRUMENT_TYPE退避（Ver.1以降）
					else if (line.startsWith("AC$INSTRUMENT_TYPE: ")) {
						workAcInstrumentType = line.trim().replaceAll(
								"AC\\$INSTRUMENT_TYPE: ", "");
					}
					// AC$MASS_SPECTROMETRY: MS_TYPE退避（Ver.2）
					else if (ver != 1
							&& line.startsWith("AC$MASS_SPECTROMETRY: MS_TYPE ")) {
						workAcMsType = line.trim().replaceAll(
								"AC\\$MASS_SPECTROMETRY: MS_TYPE ", "");
					}

					// 全角文字混入チェック
					if (!isDoubleByte) {
						byte[] bytes = line.getBytes("MS932");
						if (bytes.length != line.length()) {
							isDoubleByte = true;
						}
					}
				}
			} catch (IOException e) {
				Logger.getLogger("global").severe(
						"file read failed." + NEW_LINE + "    "
								+ file.getPath());
				e.printStackTrace();
				op.println(msgErr("server error."));
				validationMap.clear();
				return validationMap;
			} finally {
				try {
					if (br != null) {
						br.close();
					}
				} catch (IOException e) {
				}
			}
			if (isInvalidInfo) {
				// 終了タグ以降の記述がある場合
				if (status.equals(""))
					status = STATUS_WARN;
				detailsWarn
						.append("<span class=\"warnFont\">invalid after the end tag&nbsp;&nbsp;[//].</span><br />");
			}
			if (isDoubleByte) {
				// 全角文字が混入している場合
				if (status.equals(""))
					status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">double-byte character included.</span><br />");
			}
			if (ver == 1 && existLicense) {
				// LICENSEタグが存在する場合（Ver.1）
				if (status.equals(""))
					status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">[LICENSE: ]&nbsp;&nbsp;tag can not be used in record format &nbsp;&nbsp;[version 1].</span><br />");
			}

			// ----------------------------------------------------
			// 必須項目に対するメインチェック処理
			// ----------------------------------------------------
			boolean isNameCheck = false;
			int peakNum = -1;
			for (int j = 0; j < requiredList.length; j++) {
				String requiredStr = requiredList[j];
				ArrayList<String> valStrs = new ArrayList<String>(); // 値
				boolean findRequired = false; // 必須項目検出フラグ
				boolean findValue = false; // 値検出フラグ
				boolean isPeakMode = false; // ピーク情報検出モード
				for (int k = 0; k < fileContents.size(); k++) {
					String lineStr = fileContents.get(k);

					// 終了タグもしくはRELATED_RECORDタグ以降は無効（必須項目検出対象としない）
					if (lineStr.startsWith("//")) { // Ver.1以降
						break;
					} else if (ver == 1
							&& lineStr.startsWith("RELATED_RECORD:")) { // Ver.1
						break;
					}
					// 値（ピーク情報）検出（終了タグまでを全てピーク情報とする）
					else if (isPeakMode) {
						findRequired = true;
						if (!lineStr.trim().equals("")) {
							valStrs.add(lineStr);
						}
					}
					// 必須項目が見つかった場合
					else if (lineStr.indexOf(requiredStr) != -1) {
						// 必須項目検出
						findRequired = true;
						if (requiredStr.equals("PK$PEAK: ")) {
							isPeakMode = true;
							findValue = true;
							valStrs.add(lineStr.replace(requiredStr, ""));
						} else {
							// 値検出
							String tmpVal = lineStr.replace(requiredStr, "");
							if (!tmpVal.trim().equals("")) {
								findValue = true;
								valStrs.add(tmpVal);
							}
							break;
						}
					}
				}
				if (!findRequired) {
					// 必須項目が見つからない場合
					status = STATUS_ERR;
					detailsErr
							.append("<span class=\"errFont\">no required item&nbsp;&nbsp;["
									+ requiredStr + "].</span><br />");
				} else {
					if (!findValue) {
						// 値が存在しない場合
						status = STATUS_ERR;
						detailsErr
								.append("<span class=\"errFont\">no value of required item&nbsp;&nbsp;["
										+ requiredStr + "].</span><br />");
					} else {
						// 値が存在する場合

						// ----------------------------------------------------
						// 各値チェック
						// ----------------------------------------------------
						String val = (valStrs.size() > 0) ? valStrs.get(0) : "";
						// ACESSION（Ver.1以降）
						if (requiredStr.equals("ACCESSION: ")) {
							if (!val.equals(name.replace(REC_EXTENSION, ""))) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;not correspond to file name.</span><br />");
							}
							if (val.length() != 8) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is 8 digits necessary.</span><br />");
							}
						}
						// RECORD_TITLE（Ver.1以降）
						else if (requiredStr.equals("RECORD_TITLE: ")) {
							if (!val.equals(DEFAULT_VALUE)) {
								if (val.indexOf(";") != -1) {
									String[] recTitle = val.split(";");
									if (!workChName
											.contains(recTitle[0].trim())) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;compound name is not included in the&nbsp;&nbsp;[CH$NAME].</span><br />");
									}
									if (!workAcInstrumentType
											.equals(recTitle[1].trim())) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
									}
									if (ver != 1
											&& !workAcMsType.equals(recTitle[2]
													.trim())) { // Ver.2
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
									}
								} else {
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "]&nbsp;&nbsp;is not record title format.</span><br />");

									if (!workChName.contains(val)) {
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;compound name is not included in the&nbsp;&nbsp;[CH$NAME].</span><br />");
									}
									if (!workAcInstrumentType
											.equals(DEFAULT_VALUE)) {
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
									}
									if (ver != 1
											&& !workAcMsType
													.equals(DEFAULT_VALUE)) { // Ver.2
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
									}
								}
							} else {
								if (!workAcInstrumentType.equals(DEFAULT_VALUE)) {
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
								}
								if (ver != 1
										&& !workAcMsType.equals(DEFAULT_VALUE)) { // Ver.2
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
								}
							}
						}
						// DATE（Ver.1以降）
						else if (requiredStr.equals("DATE: ")
								&& !val.equals(DEFAULT_VALUE)) {
							val = val.replace(".", "/");
							val = val.replace("-", "/");
							try {
								DateFormat.getDateInstance(DateFormat.SHORT,
										Locale.JAPAN).parse(val);
							} catch (ParseException e) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not date format.</span><br />");
							}
						}
						// CH$COMPOUND_CLASS（Ver.1以降）
						else if (requiredStr.equals("CH$COMPOUND_CLASS: ")
								&& !val.equals(DEFAULT_VALUE)) {
							if (!val.startsWith("Natural Product")
									&& !val.startsWith("Non-Natural Product")) {

								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not compound class format.</span><br />");
							}
						}
						// CH$EXACT_MASS（Ver.1以降）
						else if (requiredStr.equals("CH$EXACT_MASS: ")
								&& !val.equals(DEFAULT_VALUE)) {
							try {
								Double.parseDouble(val);
							} catch (NumberFormatException e) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not numeric.</span><br />");
							}
						}
						// AC$INSTRUMENT_TYPE（Ver.1以降）
						else if (requiredStr.equals("AC$INSTRUMENT_TYPE: ")
								&& !val.equals(DEFAULT_VALUE)) {
							if (val.trim().indexOf(" ") != -1) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is space included.</span><br />");
							}
							if (val.trim().indexOf(" ") != -1) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is space included.</span><br />");
							}
						}
						// AC$MASS_SPECTROMETRY: MS_TYPE（Ver.2）
						else if (ver != 1
								&& requiredStr
										.equals("AC$MASS_SPECTROMETRY: MS_TYPE ")
								&& !val.equals(DEFAULT_VALUE)) {
							boolean isMsType = true;
							if (val.startsWith("MS")) {
								val = val.replace("MS", "");
								if (!val.equals("")) {
									try {
										Integer.parseInt(val);
									} catch (NumberFormatException e) {
										isMsType = false;
									}
								}
							} else {
								isMsType = false;
							}
							if (!isMsType) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not \"MSn\".</span><br />");
							}
						}
						// AC$MASS_SPECTROMETRY:
						// ION_MODE（Ver.2）、AC$ANALYTICAL_CONDITION: MODE（Ver.1）
						else if ((ver != 1
								&& requiredStr
										.equals("AC$MASS_SPECTROMETRY: ION_MODE ") && !val
									.equals(DEFAULT_VALUE))
								|| (ver == 1
										&& requiredStr
												.equals("AC$ANALYTICAL_CONDITION: MODE ") && !val
											.equals(DEFAULT_VALUE))) {
							if (!val.equals("POSITIVE")
									&& !val.equals("NEGATIVE")) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not \"POSITIVE\" or \"NEGATIVE\".</span><br />");
							}
						}
						// PK$NUM_PEAK（Ver.1以降）
						else if (requiredStr.equals("PK$NUM_PEAK: ")
								&& !val.equals(DEFAULT_VALUE)) {
							try {
								peakNum = Integer.parseInt(val);
							} catch (NumberFormatException e) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not numeric.</span><br />");
							}
						}
						// PK$PEAK:（Ver.1以降）
						else if (requiredStr.equals("PK$PEAK: ")) {
							if (valStrs.size() == 0
									|| !valStrs.get(0).startsWith(
											"m/z int. rel.int.")) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;[PK$PEAK: ]&nbsp;&nbsp;, the first line is not \"PK$PEAK: m/z int. rel.int.\".</span><br />");
							} else {
								boolean isNa = false;
								String peak = "";
								String mz = "";
								String intensity = "";
								boolean mzDuplication = false;
								boolean mzNotNumeric = false;
								boolean intensityNotNumeric = false;
								boolean invalidFormat = false;
								HashSet<String> mzSet = new HashSet<String>();
								for (int l = 0; l < valStrs.size(); l++) {
									peak = valStrs.get(l).trim();
									// N/A検出
									if (peak.indexOf(DEFAULT_VALUE) != -1) {
										isNa = true;
										break;
									}
									if (l == 0) {
										continue;
									} // m/z int. rel.int.が格納されている行のため飛ばす

									if (peak.indexOf(" ") != -1) {
										mz = peak.split(" ")[0];
										if (!mzSet.add(mz)) {
											mzDuplication = true;
										}
										try {
											Double.parseDouble(mz);
										} catch (NumberFormatException e) {
											mzNotNumeric = true;
										}
										intensity = peak.split(" ")[1];
										try {
											Double.parseDouble(intensity);
										} catch (NumberFormatException e) {
											intensityNotNumeric = true;
										}
									} else {
										invalidFormat = true;
									}
									if (mzDuplication && mzNotNumeric
											&& intensityNotNumeric
											&& invalidFormat) {
										break;
									}
								}
								if (isNa) {// PK$PEAK:がN/Aの場合
									if (peakNum != -1) { // PK$NUM_PEAK:もN/Aにする
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is mismatch or \""
														+ DEFAULT_VALUE
														+ "\".</span><br />");
									}
									if (valStrs.size() - 1 > 0) { // PK$PEAK:にはピーク情報を記述しないようにする
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is invalid peak information exists.</span><br />");
									}
								} else {
									if (mzDuplication) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">mz value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is duplication.</span><br />");
									}
									if (mzNotNumeric) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">mz value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not numeric.</span><br />");
									}
									if (intensityNotNumeric) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">intensity value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not numeric.</span><br />");
									}
									if (invalidFormat) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not peak format.</span><br />");
									}
									if (peakNum != 0 && valStrs.size() - 1 == 0) { // 値がない場合はN/Aを追加するようにする（PK$NUM_PEAK:が0の場合は記述なしでも可）
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$PEAK: ]&nbsp;&nbsp;is no value.  at that time, please add \""
														+ DEFAULT_VALUE
														+ "\". </span><br />");
									}
									if (peakNum != valStrs.size() - 1) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is mismatch or \""
														+ DEFAULT_VALUE
														+ "\".</span><br />");
									}
								}
							}
						}
					}
				}
			}
			String details = detailsErr.toString() + detailsWarn.toString();
			if (status.equals("")) {
				status = STATUS_OK;
				details = " ";
			}
			validationMap.put(name, status + "\t" + details);
		}

//		// ----------------------------------------------------
//		// 登録済みデータ重複チェック処理
//		// ----------------------------------------------------
//		// 登録済みIDリスト生成（DB）
//		HashSet<String> regIdList = new HashSet<String>();
//		String[] sqls = { "SELECT ID FROM SPECTRUM ORDER BY ID",
//				"SELECT ID FROM RECORD ORDER BY ID",
//				"SELECT ID FROM PEAK GROUP BY ID ORDER BY ID",
//				"SELECT ID FROM CH_NAME ID ORDER BY ID",
//				"SELECT ID FROM CH_LINK ID ORDER BY ID",
//				"SELECT ID FROM TREE WHERE ID IS NOT NULL AND ID<>'' ORDER BY ID" };
//		for (int i = 0; i < sqls.length; i++) {
//			String execSql = sqls[i];
//			ResultSet rs = null;
//			try {
//				rs = db.executeQuery(execSql);
//				while (rs.next()) {
//					String idStr = rs.getString("ID");
//					regIdList.add(idStr);
//				}
//			} catch (SQLException e) {
//				Logger.getLogger("global").severe("    sql : " + execSql);
//				e.printStackTrace();
//				op.println(msgErr("database access error."));
//				return new TreeMap<String, String>();
//			} finally {
//				try {
//					if (rs != null) {
//						rs.close();
//					}
//				} catch (SQLException e) {
//				}
//			}
//		}
//		// 登録済みIDリスト生成（レコードファイル）
//		final String[] recFileList = (new File(registPath)).list();
//		for (int i = 0; i < recFileList.length; i++) {
//			String name = recFileList[i];
//			File file = new File(registPath + File.separator + name);
//			if (!file.isFile() || file.isHidden()
//					|| name.lastIndexOf(REC_EXTENSION) == -1) {
//				continue;
//			}
//			String idStr = name.replace(REC_EXTENSION, "");
//			regIdList.add(idStr);
//		}

//		// 登録済みチェック
//		for (Map.Entry<String, String> e : validationMap.entrySet()) {
//			String statusStr = e.getValue().split("\t")[0];
//			if (statusStr.equals(STATUS_ERR)) {
//				continue;
//			}
//			String nameStr = e.getKey();
//			String idStr = e.getKey().replace(REC_EXTENSION, "");
//			String detailsStr = e.getValue().split("\t")[1];
//			if (regIdList.contains(idStr)) {
//				statusStr = STATUS_WARN;
//				detailsStr += "<span class=\"warnFont\">id&nbsp;&nbsp;["
//						+ idStr + "]&nbsp;&nbsp;of file name&nbsp;&nbsp;["
//						+ nameStr
//						+ "]&nbsp;&nbsp;already registered.</span><br />";
//				validationMap.put(nameStr, statusStr + "\t" + detailsStr);
//			}
//		}

		return validationMap;
	}

	/**
	 * チェック結果表示処理
	 * 
	 * @param op
	 *            PrintWriter出力バッファ
	 * @param resultMap
	 *            チェック結果
	 * @return 結果
	 * @throws IOException
	 */
	private static boolean dispResult(PrintStream op, TreeMap<String, String> resultMap)
			throws IOException {

		// ----------------------------------------------------
		// テーブルヘッダー部
		// ----------------------------------------------------
		NumberFormat nf = NumberFormat.getNumberInstance();
		int okCnt = 0;
		int warnCnt = 0;
		int errCnt = 0;
		for (Map.Entry<String, String> e : resultMap.entrySet()) {
			String statusStr = e.getValue().split("\t")[0];
			if (statusStr.equals(STATUS_OK)) {
				okCnt++;
			} else if (statusStr.equals(STATUS_WARN)) {
				warnCnt++;
			} else if (statusStr.equals(STATUS_ERR)) {
				errCnt++;
			}
		}
		op.println("\t<br />");
		op.println("\t<div class=\"count baseFont\">");
		op.println("\t\t<span class=\"msgFont\">" + nf.format(okCnt)
				+ " ok</span>&nbsp;,&nbsp;");
		op.println("\t\t<span class=\"warnFont\">" + nf.format(warnCnt)
				+ " warn</span>&nbsp;,&nbsp;");
		op.println("\t\t<span class=\"errFont\">" + nf.format(errCnt)
				+ " error</span> / " + nf.format(resultMap.size())
				+ " files&nbsp;");
		op.println("\t</div>");
		op.println("\t<table width=\"980\" cellspacing=\"1\" cellpadding=\"0\" bgcolor=\"Lavender\">");
		op.println("\t\t<tr class=\"rowHeader\">");
		op.println("\t\t\t<td width=\"140\">Name</td>");
		op.println("\t\t\t<td width=\"70\">Status</td>");
		op.println("\t\t\t<td>Details</td>");
		op.println("\t\t</tr>");

		// ----------------------------------------------------
		// 一覧表示部
		// ----------------------------------------------------
		for (Map.Entry<String, String> e : resultMap.entrySet()) {
			String nameStr = e.getKey();
			String statusStr = e.getValue().split("\t")[0];
			String detailsStr = e.getValue().split("\t")[1].trim();
			op.println("\t\t<tr class=\"rowEnable\">");
			op.println("\t\t\t<td class=\"leftIndent\" height=\"24\">"
					+ nameStr + "</td>");
			op.println("\t\t\t<td align=\"center\">" + statusStr + "</td>");
			op.println("\t\t\t<td class=\"details\">" + detailsStr + "</td>");
			op.println("\t\t</tr>");
		}
		op.println("\t</table>");

		return true;
	}

	public static void printHelp(Options lvOptions) {
		HelpFormatter lvFormater = new HelpFormatter();
		lvFormater.printHelp("Programm_Name", lvOptions);
	}

	public static void main(String[] args) {
		RequestDummy request;

		PrintStream out = System.out;

		Options lvOptions = new Options();
		lvOptions.addOption("h", "help", false, "show this help.");
		lvOptions
				.addOption(
						"r",
						"recdata",
						true,
						"points to the recdata directory containing massbank records. Reads all *.txt files in there.");

		CommandLineParser lvParser = new BasicParser();
		CommandLine lvCmd = null;
		try {
			lvCmd = lvParser.parse(lvOptions, args);
			if (lvCmd.hasOption('h')) {
				printHelp(lvOptions);
				return;
			}
		} catch (org.apache.commons.cli.ParseException pvException) {
			System.out.println(pvException.getMessage());
		}

		String recDataPath = lvCmd.getOptionValue("recdata");

		// ---------------------------------------------
		// 各種パラメータ取得および設定
		// ---------------------------------------------

		final String baseUrl = MassBankEnv.get(MassBankEnv.KEY_BASE_URL);
		final String dbRootPath = "./";
		final String dbHostName = MassBankEnv.get(MassBankEnv.KEY_DB_HOST_NAME);
		final String tomcatTmpPath = ".";
		final String tmpPath = (new File(tomcatTmpPath + sdf.format(new Date())))
				.getPath() + File.separator;
		GetConfig conf = new GetConfig(baseUrl);
		int recVersion = 2;
		String selDbName = "";
		Object up = null; // Was: file Upload
		boolean isResult = true;
		String upFileName = "";
		boolean upResult = false;
		DatabaseAccess db = null;

		try {
			// ----------------------------------------------------
			// ファイルアップロード時の初期化処理
			// ----------------------------------------------------
			// if (FileUpload.isMultipartContent(request)) {
			// (new File(tmpPath)).mkdir();
			// String os = System.getProperty("os.name");
			// if (os.indexOf("Windows") == -1) {
			// isResult = FileUtil.changeMode("777", tmpPath);
			// if (!isResult) {
			// out.println(msgErr("[" + tmpPath
			// + "]&nbsp;&nbsp; chmod failed."));
			// return;
			// }
			// }
			// up = new FileUpload(request, tmpPath);
			// }

			// ----------------------------------------------------
			// 存在するDB名取得（ディレクトリによる判定）
			// ----------------------------------------------------
			List<String> dbNameList = Arrays.asList(conf.getDbName());
			ArrayList<String> dbNames = new ArrayList<String>();
			dbNames.add("");
			File[] dbDirs = (new File(dbRootPath)).listFiles();
			if (dbDirs != null) {
				for (File dbDir : dbDirs) {
					if (dbDir.isDirectory()) {
						int pos = dbDir.getName().lastIndexOf("\\");
						String dbDirName = dbDir.getName().substring(pos + 1);
						pos = dbDirName.lastIndexOf("/");
						dbDirName = dbDirName.substring(pos + 1);
						if (dbNameList.contains(dbDirName)) {
							// DBディレクトリが存在し、massbank.confに記述があるDBのみ有効とする
							dbNames.add(dbDirName);
						}
					}
				}
			}
			if (dbDirs == null || dbNames.size() == 0) {
				out.println(msgErr("[" + dbRootPath
						+ "]&nbsp;&nbsp;directory not exist."));
				return;
			}
			Collections.sort(dbNames);

			// ----------------------------------------------------
			// リクエスト取得
			// ----------------------------------------------------
			// if (FileUpload.isMultipartContent(request)) {
			// HashMap<String, String[]> reqParamMap = new HashMap<String,
			// String[]>();
			// reqParamMap = up.getRequestParam();
			// if (reqParamMap != null) {
			// for (Map.Entry<String, String[]> req : reqParamMap
			// .entrySet()) {
			// if (req.getKey().equals("ver")) {
			// try {
			// recVersion = Integer
			// .parseInt(req.getValue()[0]);
			// } catch (NumberFormatException nfe) {
			// }
			// } else if (req.getKey().equals("db")) {
			// selDbName = req.getValue()[0];
			// }
			// }
			// }
			// } else {
			// if (request.getParameter("ver") != null) {
			// try {
			// recVersion = Integer.parseInt(request
			// .getParameter("ver"));
			// } catch (NumberFormatException nfe) {
			// }
			// }
			// selDbName = request.getParameter("db");
			// }
			// if (selDbName == null || selDbName.equals("")
			// || !dbNames.contains(selDbName)) {
			// selDbName = dbNames.get(0);
			// }

			// ---------------------------------------------
			// フォーム表示
			// ---------------------------------------------
			out.println("Database: ");
			for (int i = 0; i < dbNames.size(); i++) {
				String dbName = dbNames.get(i);
				out.print("dbName");
				if (dbName.equals(selDbName)) {
					out.print(" selected");
				}
				if (i == 0) {
					out.println("------------------");
				} else {
					out.println(dbName);
				}
			}
			out.println("Record Version : ");
			out.println(recVersion);

			out.println("Record Archive :");

			// ---------------------------------------------
			// ファイルアップロード
			// ---------------------------------------------
//			HashMap<String, Boolean> upFileMap = up.doUpload();
//			if (upFileMap != null) {
//				for (Map.Entry<String, Boolean> e : upFileMap.entrySet()) {
//					upFileName = e.getKey();
//					upResult = e.getValue();
//					break;
//				}
//				if (upFileName.equals("")) {
//					out.println(msgErr("please select file."));
//					isResult = false;
//				} else if (!upResult) {
//					out.println(msgErr("[" + upFileName
//							+ "]&nbsp;&nbsp;upload failed."));
//					isResult = false;
//				} else if (!upFileName.endsWith(ZIP_EXTENSION)
//						&& !upFileName.endsWith(MSBK_EXTENSION)) {
//					out.println(msgErr("please select&nbsp;&nbsp;["
//							+ UPLOAD_RECDATA_ZIP
//							+ "]&nbsp;&nbsp;or&nbsp;&nbsp;["
//							+ UPLOAD_RECDATA_MSBK + "]."));
//					up.deleteFile(upFileName);
//					isResult = false;
//				}
//			} else {
//				out.println(msgErr("server error."));
//				isResult = false;
//			}
//			up.deleteFileItem();
//			if (!isResult) {
//				return;
//			}

			// ---------------------------------------------
			// アップロードファイルの解凍処理
			// ---------------------------------------------
//			final String upFilePath = (new File(tmpPath + File.separator
//					+ upFileName)).getPath();
//			isResult = FileUtil.unZip(upFilePath, tmpPath);
//			if (!isResult) {
//				out.println(msgErr("["
//						+ upFileName
//						+ "]&nbsp;&nbsp; extraction failed. possibility of time-out."));
//				return;
//			}

			// ---------------------------------------------
			// アップロードファイル格納ディレクトリ存在確認
			// ---------------------------------------------
			final String recPath = (new File(dbRootPath + File.separator
					+ selDbName)).getPath();
			File tmpRecDir = new File(recDataPath);
			if (!tmpRecDir.isDirectory()) {
				tmpRecDir.mkdirs();
			}

			// ---------------------------------------------
			// 解凍ファイルチェック処理
			// ---------------------------------------------
			// dataディレクトリ存在チェック
//			final String recDataPath = (new File(tmpPath + File.separator
//					+ RECDATA_DIR_NAME)).getPath()
//					+ File.separator;
//
//			if (!(new File(recDataPath)).isDirectory()) {
//				if (upFileName.endsWith(ZIP_EXTENSION)) {
//					out.println(msgErr("["
//							+ RECDATA_DIR_NAME
//							+ "]&nbsp;&nbsp; directory is not included in the up-loading file."));
//				} else if (upFileName.endsWith(MSBK_EXTENSION)) {
//					out.println(msgErr("The uploaded file is not record data."));
//				}
//				return;
//			}

			// ---------------------------------------------
			// DB接続
			// ---------------------------------------------
//			db = new DatabaseAccess(dbHostName, selDbName);
//			isResult = db.open();
//			if (!isResult) {
//				db.close();
//				out.println(msgErr("not connect to database."));
//				return;
//			}

			// ---------------------------------------------
			// チェック処理
			// ---------------------------------------------
			TreeMap<String, String> resultMap = validationRecord(db, out,
					recDataPath, recPath, recVersion);
			if (resultMap.size() == 0) {
				return;
			}

			// ---------------------------------------------
			// 表示処理
			// ---------------------------------------------
			isResult = dispResult(out, resultMap);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (db != null) {
				db.close();
			}
			File tmpDir = new File(tmpPath);
			if (tmpDir.exists()) {
				FileUtil.removeDir(tmpDir.getPath());
			}
			out.println("</body>");
			out.println("</html>");
		}

	}

	/**
	 * チェック処理
	 * 
	 * @param db
	 *            DBアクセスオブジェクト
	 * @param op
	 *            PrintWriter出力バッファ
	 * @param dataPath
	 *            チェック対象レコードパス
	 * @param registPath
	 *            登録先予定パス
	 * @param ver
	 *            レコードフォーマットバージョン
	 * @return チェック結果Map<ファイル名, 画面表示用タブ区切り文字列>
	 * @throws IOException
	 *             入出力例外
	 */
	private static TreeMap<String, String> validationRecordOnline(DatabaseAccess db,
			PrintStream op, String dataPath, String registPath, int ver)
			throws IOException {
	
		op.println(msgInfo("validation archive is&nbsp;&nbsp;["
				+ UPLOAD_RECDATA_ZIP + "]&nbsp;&nbsp;or&nbsp;&nbsp;["
				+ UPLOAD_RECDATA_MSBK + "]."));
		if (ver == 1) {
			op.println(msgInfo("check record format version is&nbsp;&nbsp;[version 1]."));
		}
	
		final String[] dataList = (new File(dataPath)).list();
		TreeMap<String, String> validationMap = new TreeMap<String, String>();
	
		if (dataList.length == 0) {
			op.println(msgWarn("no file for validation."));
			return validationMap;
		}
	
		// ----------------------------------------------------
		// レコードファイル必須項目、必須項目値チェック処理
		// ----------------------------------------------------
		String[] requiredList = new String[] { // Ver.2
		"ACCESSION: ", "RECORD_TITLE: ", "DATE: ", "AUTHORS: ", "LICENSE: ",
				"CH$NAME: ", "CH$COMPOUND_CLASS: ", "CH$FORMULA: ",
				"CH$EXACT_MASS: ", "CH$SMILES: ", "CH$IUPAC: ",
				"AC$INSTRUMENT: ", "AC$INSTRUMENT_TYPE: ",
				"AC$MASS_SPECTROMETRY: MS_TYPE ",
				"AC$MASS_SPECTROMETRY: ION_MODE ", "PK$NUM_PEAK: ", "PK$PEAK: " };
		if (ver == 1) { // Ver.1
			requiredList = new String[] { "ACCESSION: ", "RECORD_TITLE: ",
					"DATE: ", "AUTHORS: ", "COPYRIGHT: ", "CH$NAME: ",
					"CH$COMPOUND_CLASS: ", "CH$FORMULA: ", "CH$EXACT_MASS: ",
					"CH$SMILES: ", "CH$IUPAC: ", "AC$INSTRUMENT: ",
					"AC$INSTRUMENT_TYPE: ", "AC$ANALYTICAL_CONDITION: MODE ",
					"PK$NUM_PEAK: ", "PK$PEAK: " };
		}
		for (int i = 0; i < dataList.length; i++) {
			String name = dataList[i];
			String status = "";
			StringBuilder detailsErr = new StringBuilder();
			StringBuilder detailsWarn = new StringBuilder();
	
			// 読み込み対象チェック処理
			File file = new File(dataPath + name);
			if (file.isDirectory()) {
				// ディレクトリの場合
				status = STATUS_ERR;
				detailsErr.append("<span class=\"errFont\">[" + name
						+ "]&nbsp;&nbsp;is directory.</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			} else if (file.isHidden()) {
				// 隠しファイルの場合
				status = STATUS_ERR;
				detailsErr.append("<span class=\"errFont\">[" + name
						+ "]&nbsp;&nbsp;is hidden.</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			} else if (name.lastIndexOf(REC_EXTENSION) == -1) {
				// ファイル拡張子不正の場合
				status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">file extension of&nbsp;&nbsp;["
								+ name
								+ "]&nbsp;&nbsp;is not&nbsp;&nbsp;["
								+ REC_EXTENSION + "].</span><br />");
				validationMap.put(name, status + "\t" + detailsErr.toString());
				continue;
			}
	
			// 読み込み
			boolean isEndTagRead = false;
			boolean isInvalidInfo = false;
			boolean isDoubleByte = false;
			ArrayList<String> fileContents = new ArrayList<String>();
			boolean existLicense = false; // LICENSEタグ存在チェック用（Ver.1）
			ArrayList<String> workChName = new ArrayList<String>(); // RECORD_TITLEチェック用にCH$NAMEの値を退避（Ver.1以降）
			String workAcInstrumentType = ""; // RECORD_TITLEチェック用にAC$INSTRUMENT_TYPEの値を退避（Ver.1以降）
			String workAcMsType = ""; // RECORD_TITLEチェック用にAC$MASS_SPECTROMETRY:
										// MS_TYPEの値を退避（Ver.2）
			String line = "";
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(file));
				while ((line = br.readLine()) != null) {
					if (isEndTagRead) {
						if (!line.equals("")) {
							isInvalidInfo = true;
						}
					}
	
					// 終了タグ検出時フラグセット
					if (line.startsWith("//")) {
						isEndTagRead = true;
					}
					fileContents.add(line);
	
					// LICENSE退避（Ver.1）
					if (line.startsWith("LICENSE: ")) {
						existLicense = true;
					}
					// CH$NAME退避（Ver.1以降）
					else if (line.startsWith("CH$NAME: ")) {
						workChName.add(line.trim()
								.replaceAll("CH\\$NAME: ", ""));
					}
					// AC$INSTRUMENT_TYPE退避（Ver.1以降）
					else if (line.startsWith("AC$INSTRUMENT_TYPE: ")) {
						workAcInstrumentType = line.trim().replaceAll(
								"AC\\$INSTRUMENT_TYPE: ", "");
					}
					// AC$MASS_SPECTROMETRY: MS_TYPE退避（Ver.2）
					else if (ver != 1
							&& line.startsWith("AC$MASS_SPECTROMETRY: MS_TYPE ")) {
						workAcMsType = line.trim().replaceAll(
								"AC\\$MASS_SPECTROMETRY: MS_TYPE ", "");
					}
	
					// 全角文字混入チェック
					if (!isDoubleByte) {
						byte[] bytes = line.getBytes("MS932");
						if (bytes.length != line.length()) {
							isDoubleByte = true;
						}
					}
				}
			} catch (IOException e) {
				Logger.getLogger("global").severe(
						"file read failed." + NEW_LINE + "    "
								+ file.getPath());
				e.printStackTrace();
				op.println(msgErr("server error."));
				validationMap.clear();
				return validationMap;
			} finally {
				try {
					if (br != null) {
						br.close();
					}
				} catch (IOException e) {
				}
			}
			if (isInvalidInfo) {
				// 終了タグ以降の記述がある場合
				if (status.equals(""))
					status = STATUS_WARN;
				detailsWarn
						.append("<span class=\"warnFont\">invalid after the end tag&nbsp;&nbsp;[//].</span><br />");
			}
			if (isDoubleByte) {
				// 全角文字が混入している場合
				if (status.equals(""))
					status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">double-byte character included.</span><br />");
			}
			if (ver == 1 && existLicense) {
				// LICENSEタグが存在する場合（Ver.1）
				if (status.equals(""))
					status = STATUS_ERR;
				detailsErr
						.append("<span class=\"errFont\">[LICENSE: ]&nbsp;&nbsp;tag can not be used in record format &nbsp;&nbsp;[version 1].</span><br />");
			}
	
			// ----------------------------------------------------
			// 必須項目に対するメインチェック処理
			// ----------------------------------------------------
			boolean isNameCheck = false;
			int peakNum = -1;
			for (int j = 0; j < requiredList.length; j++) {
				String requiredStr = requiredList[j];
				ArrayList<String> valStrs = new ArrayList<String>(); // 値
				boolean findRequired = false; // 必須項目検出フラグ
				boolean findValue = false; // 値検出フラグ
				boolean isPeakMode = false; // ピーク情報検出モード
				for (int k = 0; k < fileContents.size(); k++) {
					String lineStr = fileContents.get(k);
	
					// 終了タグもしくはRELATED_RECORDタグ以降は無効（必須項目検出対象としない）
					if (lineStr.startsWith("//")) { // Ver.1以降
						break;
					} else if (ver == 1
							&& lineStr.startsWith("RELATED_RECORD:")) { // Ver.1
						break;
					}
					// 値（ピーク情報）検出（終了タグまでを全てピーク情報とする）
					else if (isPeakMode) {
						findRequired = true;
						if (!lineStr.trim().equals("")) {
							valStrs.add(lineStr);
						}
					}
					// 必須項目が見つかった場合
					else if (lineStr.indexOf(requiredStr) != -1) {
						// 必須項目検出
						findRequired = true;
						if (requiredStr.equals("PK$PEAK: ")) {
							isPeakMode = true;
							findValue = true;
							valStrs.add(lineStr.replace(requiredStr, ""));
						} else {
							// 値検出
							String tmpVal = lineStr.replace(requiredStr, "");
							if (!tmpVal.trim().equals("")) {
								findValue = true;
								valStrs.add(tmpVal);
							}
							break;
						}
					}
				}
				if (!findRequired) {
					// 必須項目が見つからない場合
					status = STATUS_ERR;
					detailsErr
							.append("<span class=\"errFont\">no required item&nbsp;&nbsp;["
									+ requiredStr + "].</span><br />");
				} else {
					if (!findValue) {
						// 値が存在しない場合
						status = STATUS_ERR;
						detailsErr
								.append("<span class=\"errFont\">no value of required item&nbsp;&nbsp;["
										+ requiredStr + "].</span><br />");
					} else {
						// 値が存在する場合
	
						// ----------------------------------------------------
						// 各値チェック
						// ----------------------------------------------------
						String val = (valStrs.size() > 0) ? valStrs.get(0) : "";
						// ACESSION（Ver.1以降）
						if (requiredStr.equals("ACCESSION: ")) {
							if (!val.equals(name.replace(REC_EXTENSION, ""))) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;not correspond to file name.</span><br />");
							}
							if (val.length() != 8) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is 8 digits necessary.</span><br />");
							}
						}
						// RECORD_TITLE（Ver.1以降）
						else if (requiredStr.equals("RECORD_TITLE: ")) {
							if (!val.equals(DEFAULT_VALUE)) {
								if (val.indexOf(";") != -1) {
									String[] recTitle = val.split(";");
									if (!workChName
											.contains(recTitle[0].trim())) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;compound name is not included in the&nbsp;&nbsp;[CH$NAME].</span><br />");
									}
									if (!workAcInstrumentType
											.equals(recTitle[1].trim())) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
									}
									if (ver != 1
											&& !workAcMsType.equals(recTitle[2]
													.trim())) { // Ver.2
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
									}
								} else {
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "]&nbsp;&nbsp;is not record title format.</span><br />");
	
									if (!workChName.contains(val)) {
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;compound name is not included in the&nbsp;&nbsp;[CH$NAME].</span><br />");
									}
									if (!workAcInstrumentType
											.equals(DEFAULT_VALUE)) {
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
									}
									if (ver != 1
											&& !workAcMsType
													.equals(DEFAULT_VALUE)) { // Ver.2
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
									}
								}
							} else {
								if (!workAcInstrumentType.equals(DEFAULT_VALUE)) {
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "],&nbsp;&nbsp;instrument type is different from&nbsp;&nbsp;[AC$INSTRUMENT_TYPE].</span><br />");
								}
								if (ver != 1
										&& !workAcMsType.equals(DEFAULT_VALUE)) { // Ver.2
									if (status.equals(""))
										status = STATUS_WARN;
									detailsWarn
											.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
													+ requiredStr
													+ "],&nbsp;&nbsp;ms type is different from&nbsp;&nbsp;[AC$MASS_SPECTROMETRY: MS_TYPE].</span><br />");
								}
							}
						}
						// DATE（Ver.1以降）
						else if (requiredStr.equals("DATE: ")
								&& !val.equals(DEFAULT_VALUE)) {
							val = val.replace(".", "/");
							val = val.replace("-", "/");
							try {
								DateFormat.getDateInstance(DateFormat.SHORT,
										Locale.JAPAN).parse(val);
							} catch (ParseException e) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not date format.</span><br />");
							}
						}
						// CH$COMPOUND_CLASS（Ver.1以降）
						else if (requiredStr.equals("CH$COMPOUND_CLASS: ")
								&& !val.equals(DEFAULT_VALUE)) {
							if (!val.startsWith("Natural Product")
									&& !val.startsWith("Non-Natural Product")) {
	
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not compound class format.</span><br />");
							}
						}
						// CH$EXACT_MASS（Ver.1以降）
						else if (requiredStr.equals("CH$EXACT_MASS: ")
								&& !val.equals(DEFAULT_VALUE)) {
							try {
								Double.parseDouble(val);
							} catch (NumberFormatException e) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not numeric.</span><br />");
							}
						}
						// AC$INSTRUMENT_TYPE（Ver.1以降）
						else if (requiredStr.equals("AC$INSTRUMENT_TYPE: ")
								&& !val.equals(DEFAULT_VALUE)) {
							if (val.trim().indexOf(" ") != -1) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is space included.</span><br />");
							}
							if (val.trim().indexOf(" ") != -1) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is space included.</span><br />");
							}
						}
						// AC$MASS_SPECTROMETRY: MS_TYPE（Ver.2）
						else if (ver != 1
								&& requiredStr
										.equals("AC$MASS_SPECTROMETRY: MS_TYPE ")
								&& !val.equals(DEFAULT_VALUE)) {
							boolean isMsType = true;
							if (val.startsWith("MS")) {
								val = val.replace("MS", "");
								if (!val.equals("")) {
									try {
										Integer.parseInt(val);
									} catch (NumberFormatException e) {
										isMsType = false;
									}
								}
							} else {
								isMsType = false;
							}
							if (!isMsType) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not \"MSn\".</span><br />");
							}
						}
						// AC$MASS_SPECTROMETRY:
						// ION_MODE（Ver.2）、AC$ANALYTICAL_CONDITION: MODE（Ver.1）
						else if ((ver != 1
								&& requiredStr
										.equals("AC$MASS_SPECTROMETRY: ION_MODE ") && !val
									.equals(DEFAULT_VALUE))
								|| (ver == 1
										&& requiredStr
												.equals("AC$ANALYTICAL_CONDITION: MODE ") && !val
											.equals(DEFAULT_VALUE))) {
							if (!val.equals("POSITIVE")
									&& !val.equals("NEGATIVE")) {
								if (status.equals(""))
									status = STATUS_WARN;
								detailsWarn
										.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not \"POSITIVE\" or \"NEGATIVE\".</span><br />");
							}
						}
						// PK$NUM_PEAK（Ver.1以降）
						else if (requiredStr.equals("PK$NUM_PEAK: ")
								&& !val.equals(DEFAULT_VALUE)) {
							try {
								peakNum = Integer.parseInt(val);
							} catch (NumberFormatException e) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
												+ requiredStr
												+ "]&nbsp;&nbsp;is not numeric.</span><br />");
							}
						}
						// PK$PEAK:（Ver.1以降）
						else if (requiredStr.equals("PK$PEAK: ")) {
							if (valStrs.size() == 0
									|| !valStrs.get(0).startsWith(
											"m/z int. rel.int.")) {
								status = STATUS_ERR;
								detailsErr
										.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;[PK$PEAK: ]&nbsp;&nbsp;, the first line is not \"PK$PEAK: m/z int. rel.int.\".</span><br />");
							} else {
								boolean isNa = false;
								String peak = "";
								String mz = "";
								String intensity = "";
								boolean mzDuplication = false;
								boolean mzNotNumeric = false;
								boolean intensityNotNumeric = false;
								boolean invalidFormat = false;
								HashSet<String> mzSet = new HashSet<String>();
								for (int l = 0; l < valStrs.size(); l++) {
									peak = valStrs.get(l).trim();
									// N/A検出
									if (peak.indexOf(DEFAULT_VALUE) != -1) {
										isNa = true;
										break;
									}
									if (l == 0) {
										continue;
									} // m/z int. rel.int.が格納されている行のため飛ばす
	
									if (peak.indexOf(" ") != -1) {
										mz = peak.split(" ")[0];
										if (!mzSet.add(mz)) {
											mzDuplication = true;
										}
										try {
											Double.parseDouble(mz);
										} catch (NumberFormatException e) {
											mzNotNumeric = true;
										}
										intensity = peak.split(" ")[1];
										try {
											Double.parseDouble(intensity);
										} catch (NumberFormatException e) {
											intensityNotNumeric = true;
										}
									} else {
										invalidFormat = true;
									}
									if (mzDuplication && mzNotNumeric
											&& intensityNotNumeric
											&& invalidFormat) {
										break;
									}
								}
								if (isNa) {// PK$PEAK:がN/Aの場合
									if (peakNum != -1) { // PK$NUM_PEAK:もN/Aにする
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is mismatch or \""
														+ DEFAULT_VALUE
														+ "\".</span><br />");
									}
									if (valStrs.size() - 1 > 0) { // PK$PEAK:にはピーク情報を記述しないようにする
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is invalid peak information exists.</span><br />");
									}
								} else {
									if (mzDuplication) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">mz value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is duplication.</span><br />");
									}
									if (mzNotNumeric) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">mz value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not numeric.</span><br />");
									}
									if (intensityNotNumeric) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">intensity value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not numeric.</span><br />");
									}
									if (invalidFormat) {
										status = STATUS_ERR;
										detailsErr
												.append("<span class=\"errFont\">value of required item&nbsp;&nbsp;["
														+ requiredStr
														+ "]&nbsp;&nbsp;is not peak format.</span><br />");
									}
									if (peakNum != 0 && valStrs.size() - 1 == 0) { // 値がない場合はN/Aを追加するようにする（PK$NUM_PEAK:が0の場合は記述なしでも可）
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$PEAK: ]&nbsp;&nbsp;is no value.  at that time, please add \""
														+ DEFAULT_VALUE
														+ "\". </span><br />");
									}
									if (peakNum != valStrs.size() - 1) {
										if (status.equals(""))
											status = STATUS_WARN;
										detailsWarn
												.append("<span class=\"warnFont\">value of required item&nbsp;&nbsp;[PK$NUM_PEAK: ]&nbsp;&nbsp;is mismatch or \""
														+ DEFAULT_VALUE
														+ "\".</span><br />");
									}
								}
							}
						}
					}
				}
			}
			String details = detailsErr.toString() + detailsWarn.toString();
			if (status.equals("")) {
				status = STATUS_OK;
				details = " ";
			}
			validationMap.put(name, status + "\t" + details);
		}
	
		// ----------------------------------------------------
		// 登録済みデータ重複チェック処理
		// ----------------------------------------------------
		// 登録済みIDリスト生成（DB）
		HashSet<String> regIdList = new HashSet<String>();
		String[] sqls = { "SELECT ID FROM SPECTRUM ORDER BY ID",
				"SELECT ID FROM RECORD ORDER BY ID",
				"SELECT ID FROM PEAK GROUP BY ID ORDER BY ID",
				"SELECT ID FROM CH_NAME ID ORDER BY ID",
				"SELECT ID FROM CH_LINK ID ORDER BY ID",
				"SELECT ID FROM TREE WHERE ID IS NOT NULL AND ID<>'' ORDER BY ID" };
		for (int i = 0; i < sqls.length; i++) {
			String execSql = sqls[i];
			ResultSet rs = null;
			try {
				rs = db.executeQuery(execSql);
				while (rs.next()) {
					String idStr = rs.getString("ID");
					regIdList.add(idStr);
				}
			} catch (SQLException e) {
				Logger.getLogger("global").severe("    sql : " + execSql);
				e.printStackTrace();
				op.println(msgErr("database access error."));
				return new TreeMap<String, String>();
			} finally {
				try {
					if (rs != null) {
						rs.close();
					}
				} catch (SQLException e) {
				}
			}
		}
		// 登録済みIDリスト生成（レコードファイル）
		final String[] recFileList = (new File(registPath)).list();
		for (int i = 0; i < recFileList.length; i++) {
			String name = recFileList[i];
			File file = new File(registPath + File.separator + name);
			if (!file.isFile() || file.isHidden()
					|| name.lastIndexOf(REC_EXTENSION) == -1) {
				continue;
			}
			String idStr = name.replace(REC_EXTENSION, "");
			regIdList.add(idStr);
		}
	
		// 登録済みチェック
		for (Map.Entry<String, String> e : validationMap.entrySet()) {
			String statusStr = e.getValue().split("\t")[0];
			if (statusStr.equals(STATUS_ERR)) {
				continue;
			}
			String nameStr = e.getKey();
			String idStr = e.getKey().replace(REC_EXTENSION, "");
			String detailsStr = e.getValue().split("\t")[1];
			if (regIdList.contains(idStr)) {
				statusStr = STATUS_WARN;
				detailsStr += "<span class=\"warnFont\">id&nbsp;&nbsp;["
						+ idStr + "]&nbsp;&nbsp;of file name&nbsp;&nbsp;["
						+ nameStr
						+ "]&nbsp;&nbsp;already registered.</span><br />";
				validationMap.put(nameStr, statusStr + "\t" + detailsStr);
			}
		}
	
		return validationMap;
	}

}
