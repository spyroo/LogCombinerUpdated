package io.github.spyroo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.commons.io.FileUtils;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that assists in the fetching and uploading of log files from logs.tf
 * @author spyroo
 *
 */
public class LogCombiner {

	private static final Logger logger = LogManager.getLogger(LogCombiner.class);

	private Path tempDir;
	private String logsApiKey;
	private String appName;
	/**
	 * Accepts an App name(to be displayed on the logs page after an upload) and an api key used to upload to logs.tf
	 * @param appName The name of the app that is doing the uploading
	 * @param logsApiKey The API key to be used to upload the logs
	 */
	public LogCombiner(String appName, String logsApiKey){
		this.logsApiKey = logsApiKey;
		this.appName = appName;
		try {
			tempDir = Files.createTempDirectory("Combiner_");
			logger.trace("Created LogCombiner object");
			logger.trace(String.format("\tApp name: %s\n\tAPI key: %s\n\tTemp Dir: %s", appName, logsApiKey, tempDir.toString()));
		} catch (IOException e) {
			logger.error(e);
		}
	}
	
	/**
	 * Downloads and unzips the log file from the logs link.
	 * @param logFileUrl String from getLogsDownloadLink()
	 * @param cleanLogsLink String from getCleanLogsLink()
	 * @return The log file extracted from the link.
	 * @throws ZipException if there was a problem unzipping the file.
	 * @throws IOException if there was a problem retrieving the file
	 */
	public File getLogFile(String logFileUrl, String cleanLogsLink) throws ZipException, IOException{
		logger.trace("Unzipping log from from " + logFileUrl);
		ZipFile zf = getZipFile(logFileUrl);
		zf.extractAll(tempDir.toAbsolutePath().toString());
		return new File(tempDir.toString() + "/" + getName(cleanLogsLink));
	}

    /**
     * Combines every file in the list of files into a new log file with the name provided
     * @param files List of Files to combine
     * @param newLogFileName New name for the log file
     * @return A new File reference that contains the logs combined
     */
	public File getCombinedFiles(File[] files, String newLogFileName)throws IOException{
		logger.trace(String.format("Combining %d files into new file %s", files.length, newLogFileName));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for(File f : files){
            out.write(Files.readAllBytes(f.toPath()));
        }
        File f = new File(tempDir.toString() + "/" + newLogFileName);
        Files.write(f.toPath(), out.toByteArray());
        return f;
    }

	/**
	 * Downloads the zip file from the url provided
	 * @param fileUrl The URL to grab the log zip file from
	 * @return A ZipFile that contains the log file that was downloaded
	 * @throws IOException
	 * @throws ZipException
	 */
	public ZipFile getZipFile(String fileUrl) throws IOException, ZipException{
		URL website = new URL(fileUrl);
		File f = new File(tempDir.toString() + "/templog");
		boolean success = f.createNewFile();
		logger.trace("Success in creating new temp log file: " + success);
		Files.copy(website.openStream(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return new ZipFile(f);
	}
	
	/**
	 * Cleans the logs.tf link to allow a download link to be formed.
	 * @param dirtyLogsUrl The dirty URL that needs to be cleaned
	 * @return A clean URL to grab the file from
	 */
	public String getCleanLogsLink(String dirtyLogsUrl){
		logger.trace("Creating a clean logs link from dirty URL: " + dirtyLogsUrl);
		StringBuilder sb = new StringBuilder();
		for(char c : dirtyLogsUrl.toCharArray()){
			if(c == '?'){
				break;
			}
			sb.append(c);
		}
		return sb.toString();
	}
	/**
	 * Returns the download url for the log url provided
	 * @param url The non-download URl
	 * @return the download URL to the log
	 */
	public String getLogsDownloadLink(String url){
		return "http://logs.tf/static/logs/" + getName(url) + ".zip";
	}
	
	public String getName(String url){
		String id = url.replaceAll("[^0-9]*", "");
		return "log_" + id + ".log";
	}
	
	/**
	 * Sends the log to logs.tf to be uploaded. This method uses the fields provided in the constructor to generate the query.
	 * @param title The title of the new log
	 * @param map The map of the new log
	 * @param logfile The file to upload to logs.tf
	 * @return a LogsResponse reference that contains info on the response from logs.tf
	 */
	public LogsResponse sendLog(String title, String map, File logfile){

		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpPost uploadFile = new HttpPost("http://logs.tf/upload");
		MultipartEntityBuilder builder = buildLogsRequest(title, map, logfile, logsApiKey, appName);
		HttpEntity multipart = builder.build();
		uploadFile.setEntity(multipart);
        LogsResponse responseObject = new LogsResponse();

		try{
			logger.debug("Sending log to logs.tf/upload");
			HttpResponse response = httpClient.execute(uploadFile); 
			BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
			StringBuilder sb = new StringBuilder();
			for (String line; (line = reader.readLine()) != null;) {
			    sb.append(line).append("\n");
			}
			String responseString = sb.toString();
            Gson respBuilder = new GsonBuilder().create();
            responseObject = respBuilder.fromJson(responseString, LogsResponse.class);
			logger.debug("Got a response, constructing object");
            return  responseObject;
					
		}catch(Exception e){
			e.printStackTrace();
		}
		return responseObject;
	}
	/**
	 * Deletes the temporary directory used to store the files. 
	 * Call this in a shutdown hook to clear the temporary directory used to store the logs, failure to do so will be the start of your temporary folder collection
	 */
	public void delDir() {
		try {
			logger.trace("Deleting temp dir");
			FileUtils.deleteDirectory(tempDir.toFile());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Constructs the MultipartEntityBuilder object used to send the log to logs.tf
	 * @param logsTitle The title of the new log
	 * @param mapName The map name of the new log
	 * @param logFile The logFile to be uploaded
	 * @param logsApiKey The API key to be used
	 * @param uploader The uploading app name to use
	 * @return A MultipartEntityBuilder that can build the request
	 */
	private MultipartEntityBuilder buildLogsRequest(String logsTitle, String mapName, File logFile, String logsApiKey, String uploader){
		logger.trace("Building multipart entity for logs request");
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.addTextBody("title", logsTitle, ContentType.TEXT_PLAIN);
		builder.addTextBody("map", mapName, ContentType.TEXT_PLAIN);
		builder.addBinaryBody("logfile", logFile, ContentType.APPLICATION_OCTET_STREAM, logFile.getName());
		builder.addTextBody("key", logsApiKey, ContentType.TEXT_PLAIN);
		builder.addTextBody("uploader", getAppName(), ContentType.TEXT_PLAIN);
		return builder;
	}

	/**
	 * @return the tempDir
	 */
	public Path getTempDir() {
		return tempDir;
	}

	/**
	 * @param tempDir the tempDir to set
	 */
	public void setTempDir(Path tempDir) {
		this.tempDir = tempDir;
	}

	/**
	 * @return the logsApiKey
	 */
	public String getLogsApiKey() {
		return logsApiKey;
	}

	/**
	 * @param logsApiKey the logsApiKey to set
	 */
	public void setLogsApiKey(String logsApiKey) {
		this.logsApiKey = logsApiKey;
	}

	/**
	 * @return the appName
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 * @param appName the appName to set
	 */
	public void setAppName(String appName) {
		this.appName = appName;
	}
	
}
